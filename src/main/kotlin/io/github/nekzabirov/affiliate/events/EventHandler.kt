package io.github.nekzabirov.affiliate.events

/**
 * The reader of one route. [exchange] and [routingKey] bind the work queue to the publisher's exchange, and the
 * consumer hands every delivery that arrived on them to [handle].
 */
interface EventHandler {

    /** The PUBLISHER's exchange: a queue bound to any other reads a stream nobody publishes to. */
    val exchange: String

    val routingKey: String

    /** Reads [body] strictly and acts on it. Returning acknowledges the delivery; throwing dead-letters it. */
    fun handle(body: ByteArray)
}
