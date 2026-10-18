package io.github.nekzabirov.affiliate.events

import com.rabbitmq.client.Channel
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.stereotype.Component

/**
 * The one listener on the work queue. The queue carries four routes, so a delivery is handed to the handler of
 * the exchange and routing key it was published with, and acknowledged by hand: once, and only after the handler
 * returned. ANY failure - a body that is not the envelope, a model that does not parse, a handler that throws, the
 * database or the currency service down, a route nobody reads - is an ERROR line with its cause and a nack without
 * requeue, so the broker dead-letters it where it can be seen and the queue moves on. Never catch-and-ack, never
 * a retry loop: a fact this app has no business with is the handler returning normally, not an exception.
 *
 * A dead letter is replayed by moving it back into the work queue through the default exchange, never by
 * publishing it again to the publisher's exchange, which would hand a second copy to every other reader of the
 * route. Such a delivery names the default exchange and the queue itself; where it was first published is in its
 * `x-death` entry for this queue, which the broker keeps, unchanged, however often the fact dies and comes back.
 */
@Component
class EventConsumer(handlers: List<EventHandler>) : ChannelAwareMessageListener {

    private val byRoute = handlers.associateBy { Route(it.exchange, it.routingKey) }

    init {
        check(byRoute.size == handlers.size) { "Two handlers read the same route: ${handlers.map { Route(it.exchange, it.routingKey) }}" }
    }

    /** What the work queue must be bound to: every route a handler reads. */
    val routes: Set<Route> get() = byRoute.keys

    override fun onMessage(message: Message, channel: Channel?) {
        val broker = requireNotNull(channel) { "A manually acknowledged listener is always given its channel" }
        val properties = message.messageProperties
        val route = routeOf(properties)
        // A publisher may put its request's trace id in this header; the postbacks a fact fires carry it on.
        val traceId = properties.headers[REQUEST_ID]?.toString()?.takeIf { TRACE_ID_FORMAT.matches(it) }
        if (traceId != null) MDC.put(TRACE_ID, traceId)
        try {
            val handled = try {
                val handler = byRoute[route] ?: throw MalformedEventException("No handler reads ${route.exchange}/${route.routingKey}")
                handler.handle(message.body)
                true
            } catch (failure: Exception) {
                log.atError()
                    .addKeyValue("event", "broker.handler_failed")
                    .addKeyValue("exchange", route.exchange)
                    .addKeyValue("route", route.routingKey)
                    .addKeyValue("queue", properties.consumerQueue)
                    .addKeyValue("redelivered", properties.redelivered)
                    .setCause(failure)
                    .log("Event refused, dead-lettered")
                false
            }
            // Outside the try: a failed ack is not a failed handler and must not become a dead letter. Its channel
            // is gone, so the broker delivers the fact again, and the fact's key makes that delivery change nothing.
            if (handled) {
                broker.basicAck(properties.deliveryTag, false)
            } else {
                broker.basicNack(properties.deliveryTag, false, false)
            }
        } finally {
            if (traceId != null) MDC.remove(TRACE_ID)
        }
    }

    /**
     * The route the fact was published with. A delivery through the default exchange is a dead letter moved back:
     * its route is the one in its `x-death` entry for the queue it came from. Without such an entry it keeps the
     * default exchange, which no handler reads, so it is refused like any delivery nobody reads.
     */
    private fun routeOf(properties: MessageProperties): Route {
        val received = Route(properties.receivedExchange.orEmpty(), properties.receivedRoutingKey.orEmpty())
        if (received.exchange.isNotEmpty()) return received
        val death = properties.xDeathHeader?.firstOrNull { it[DEATH_QUEUE]?.toString() == properties.consumerQueue }
        val exchange = death?.get(DEATH_EXCHANGE)?.toString()
        val routingKey = (death?.get(DEATH_ROUTING_KEYS) as? List<*>)?.firstOrNull()?.toString()
        return if (exchange.isNullOrEmpty() || routingKey == null) received else Route(exchange, routingKey)
    }

    /** Where a fact was published: the publisher's exchange and the routing key. */
    data class Route(
        val exchange: String,

        val routingKey: String,
    )

    private companion object {

        val log = LoggerFactory.getLogger(EventConsumer::class.java)

        const val REQUEST_ID = "x-request-id"

        // The fields of an x-death entry: the queue the fact died in, and where it had been published.

        const val DEATH_QUEUE = "queue"

        const val DEATH_EXCHANGE = "exchange"

        const val DEATH_ROUTING_KEYS = "routing-keys"

        /** The MDC key the HTTP side uses too, so one query finds a request's lines wherever they were written. */
        const val TRACE_ID = "traceId"

        /** A header is taken only when it is short and harmless in a log line. */
        val TRACE_ID_FORMAT = Regex("^[A-Za-z0-9._:-]{1,128}$")
    }
}
