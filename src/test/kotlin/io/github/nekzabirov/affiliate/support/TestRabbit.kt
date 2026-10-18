package io.github.nekzabirov.affiliate.support

import org.testcontainers.rabbitmq.RabbitMQContainer

/**
 * One real RabbitMQ 3.13 (the version docker-compose.yml runs) for the run, started on first use, for the tests that
 * go through the broker itself: topology, routing, acknowledgement and dead-lettering are the broker's logic.
 */
object TestRabbit {

    private val container: RabbitMQContainer by lazy {
        RabbitMQContainer("rabbitmq:3.13-management").also { it.start() }
    }

    val host: String get() = container.host

    val port: Int get() = container.amqpPort

    val username: String get() = container.adminUsername

    val password: String get() = container.adminPassword
}
