package io.github.nekzabirov.affiliate.config

import io.github.nekzabirov.affiliate.events.EventConsumer
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarable
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.ExchangeBuilder
import org.springframework.amqp.core.FanoutExchange
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The broker side: ONE durable work queue bound to every route a handler reads, on each publisher's own exchange,
 * and one listener on it that acknowledges by hand.
 *
 * One queue, not one per route: a shared broker often caps the queues of a vhost (managed brokers at a hundred or
 * so), and every service's queues count.
 * The publishers' exchanges are declared too - declaring what the publisher already declared is a no-op, and it
 * lets this app boot before a publisher ever has. The work queue names a dead-letter exchange: without one a
 * poison message is requeued forever and the listener stops reading everything else. A durable queue that exists
 * without that argument cannot be given it later (the broker answers 406), so it must be there from the start.
 *
 * No message converter: the consumer reads the raw body with a strict mapper of its own, and nothing this app
 * receives was written by Java.
 */
@Configuration
class RabbitConfig(
    private val properties: AppProperties,
    private val consumer: EventConsumer,
) : RabbitListenerConfigurer {

    @Bean
    fun eventTopology(): Declarables {
        val deadLetters = ExchangeBuilder.fanoutExchange(DEAD_LETTER_EXCHANGE).durable(true).build<FanoutExchange>()
        // Without a queue behind the dead-letter exchange the broker discards a refused fact without a trace. The
        // `.dead` suffix puts it under the broker policy that keeps dead letters for 14 days.
        val dead = QueueBuilder.durable(DEAD_QUEUE).build()
        val work = QueueBuilder.durable(properties.events.queue).deadLetterExchange(DEAD_LETTER_EXCHANGE).build()
        val exchanges = consumer.routes.map { it.exchange }.distinct()
            .associateWith { ExchangeBuilder.topicExchange(it).durable(true).build<TopicExchange>() }
        val bindings = consumer.routes.map { BindingBuilder.bind(work).to(exchanges.getValue(it.exchange)).with(it.routingKey) }
        val declarables = listOf<Declarable>(deadLetters, dead, BindingBuilder.bind(dead).to(deadLetters), work) +
            exchanges.values + bindings
        return Declarables(declarables)
    }

    /** Boot's listener factory (prefetch, virtual threads, auto-startup); the acknowledge mode is set here, beside the listener that acks. */
    override fun configureRabbitListeners(registrar: RabbitListenerEndpointRegistrar) {
        val endpoint = SimpleRabbitListenerEndpoint()
        endpoint.setId(LISTENER_ID)
        endpoint.setQueueNames(properties.events.queue)
        endpoint.setAckMode(AcknowledgeMode.MANUAL)
        endpoint.setMessageListener(consumer)
        registrar.registerEndpoint(endpoint)
    }

    companion object {

        const val DEAD_LETTER_EXCHANGE = "affiliate.dlx"

        const val DEAD_QUEUE = "affiliate.dead"

        private const val LISTENER_ID = "affiliate-events"
    }
}
