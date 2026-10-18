package io.github.nekzabirov.affiliate.events

import com.rabbitmq.client.Channel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.slf4j.MDC
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import java.io.IOException

/** How the one listener answers the broker: one ack after the handler returned, one nack without requeue on any failure. */
class EventConsumerTest {

    private val channel: Channel = mock(Channel::class.java)

    private val received = mutableListOf<String>()

    private val traces = mutableListOf<String?>()

    private val register = handler("pam.events", "register.events") { body ->
        traces += MDC.get("traceId")
        received += "register:${String(body)}"
    }

    private val spin = handler("casino.events", "spin.events") { body -> received += "spin:${String(body)}" }

    private val failing = handler("casino.events", "bet.events") { throw IllegalStateException("the database is down") }

    private val consumer = EventConsumer(listOf(register, spin, failing))

    @Test
    fun `a delivery goes to the handler of its exchange and routing key, and is acked once it returned`() {
        consumer.onMessage(message("casino.events", "spin.events", "s1", tag = 7), channel)
        consumer.onMessage(message("pam.events", "register.events", "r1", tag = 8), channel)

        assertThat(received).containsExactly("spin:s1", "register:r1")
        verify(channel).basicAck(7, false)
        verify(channel).basicAck(8, false)
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `a handler that throws gets its delivery nacked without requeue, onto the dead-letter exchange`() {
        consumer.onMessage(message("casino.events", "bet.events", "b1", tag = 9), channel)

        verify(channel).basicNack(9, false, false)
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `a malformed body is a failure like any other`() {
        val reader = EventReader()
        val strict = handler("pam.events", "invoice.events") { body -> reader.read(body, InvoiceEvent::class.java) { it.player.id } }
        val consumer = EventConsumer(listOf(strict))

        consumer.onMessage(message("pam.events", "invoice.events", """{"playerId": "2", "data": {}}""", tag = 10), channel)

        verify(channel).basicNack(10, false, false)
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `a delivery on a route nobody reads is dead-lettered, never acked into oblivion`() {
        consumer.onMessage(message("pam.events", "player.events", "p1", tag = 11), channel)
        consumer.onMessage(message("casino.events", "register.events", "r2", tag = 12), channel)
        // Through the default exchange without an x-death entry: nothing says where it was published.
        consumer.onMessage(message("", "affiliate.events", "replayed", tag = 13), channel)

        assertThat(received).isEmpty()
        verify(channel).basicNack(11, false, false)
        verify(channel).basicNack(12, false, false)
        verify(channel).basicNack(13, false, false)
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `a dead letter moved back through the default exchange goes to the handler of the route it was published with`() {
        consumer.onMessage(moved("pam.events", "register.events", "r6", tag = 18), channel)
        // Only the entry of the queue it came from counts: another queue's death says nothing about this one.
        consumer.onMessage(moved("pam.events", "register.events", "r7", tag = 19, deadIn = "report.register.events"), channel)
        // A route recovered that way is held to the same rule as any other: nobody reads it, so it is refused.
        consumer.onMessage(moved("pam.events", "player.events", "p2", tag = 20), channel)

        assertThat(received).containsExactly("register:r6")
        verify(channel).basicAck(18, false)
        verify(channel).basicNack(19, false, false)
        verify(channel).basicNack(20, false, false)
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `a failed ack is not turned into a nack`() {
        doThrow(IOException("channel closed")).`when`(channel).basicAck(14, false)

        assertThatThrownBy { consumer.onMessage(message("casino.events", "spin.events", "s2", tag = 14), channel) }
            .isInstanceOf(IOException::class.java)
        verify(channel).basicAck(14, false)
        verifyNoMoreInteractions(channel)
    }

    @Test
    fun `the publisher's request id is the trace id while the fact is handled, and only then`() {
        consumer.onMessage(message("pam.events", "register.events", "r3", tag = 15, requestId = "req-42"), channel)
        consumer.onMessage(message("pam.events", "register.events", "r4", tag = 16, requestId = "bad id\nforged=line"), channel)
        consumer.onMessage(message("pam.events", "register.events", "r5", tag = 17), channel)

        assertThat(traces).containsExactly("req-42", null, null)
        assertThat(MDC.get("traceId")).isNull()
    }

    @Test
    fun `two handlers of one route are refused at boot`() {
        assertThatThrownBy { EventConsumer(listOf(spin, handler("casino.events", "spin.events") {})) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun handler(publisher: String, route: String, action: (ByteArray) -> Unit) = object : EventHandler {

        override val exchange = publisher

        override val routingKey = route

        override fun handle(body: ByteArray) = action(body)
    }

    private fun message(exchange: String, routingKey: String, body: String, tag: Long, requestId: String? = null): Message {
        val properties = MessageProperties()
        properties.setDeliveryTag(tag)
        properties.setReceivedExchange(exchange)
        properties.setReceivedRoutingKey(routingKey)
        properties.setConsumerQueue("affiliate.events")
        if (requestId != null) properties.setHeader("x-request-id", requestId)
        return Message(body.toByteArray(), properties)
    }

    /**
     * A dead letter moved back into the work queue: through the default exchange, addressed to the queue, with the
     * `x-death` entry the broker wrote when it died in [deadIn] after being published to [exchange] with [routingKey].
     */
    private fun moved(exchange: String, routingKey: String, body: String, tag: Long, deadIn: String = "affiliate.events"): Message {
        val message = message("", "affiliate.events", body, tag)
        val death = mapOf("queue" to deadIn, "reason" to "rejected", "count" to 1L, "exchange" to exchange, "routing-keys" to listOf(routingKey))
        message.messageProperties.setHeader("x-death", listOf(death))
        return message
    }
}
