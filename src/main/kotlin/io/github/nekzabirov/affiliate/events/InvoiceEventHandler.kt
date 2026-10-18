package io.github.nekzabirov.affiliate.events

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.model.Registration
import io.github.nekzabirov.affiliate.services.AccrualService
import org.springframework.stereotype.Component

/**
 * invoice.events (the player service): every invoice on creation and on every status change. Only a deposit that landed -
 * CREDIT in SUCCESS - is money a partner can earn on; every other snapshot is acknowledged and left alone. The
 * invoice's owner travels with it, so a deposit that comes before its registration can attribute the player.
 */
@Component
class InvoiceEventHandler(
    private val reader: EventReader,
    private val accrual: AccrualService,
    properties: AppProperties,
) : EventHandler {

    override val exchange = properties.events.playerExchange

    override val routingKey = ROUTE

    override fun handle(body: ByteArray) {
        val event = reader.read(body, InvoiceEvent::class.java) { it.player.id }
        val invoice = event.data
        if (invoice.type == InvoiceEvent.Type.CREDIT && invoice.status == InvoiceEvent.Status.SUCCESS) {
            val owner = invoice.player
            val player = Registration(event.playerId, owner.affiliateTag, owner.affiliateAttribution.orEmpty(), owner.country, owner.createdAt)
            accrual.deposit(player, invoice.id, invoice.amount, invoice.currency, invoice.updatedAt)
        }
    }

    private companion object {

        const val ROUTE = "invoice.events"
    }
}
