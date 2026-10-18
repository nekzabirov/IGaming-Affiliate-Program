package io.github.nekzabirov.affiliate.events

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.model.Registration
import io.github.nekzabirov.affiliate.services.AccrualService
import org.springframework.stereotype.Component

/** register.events (the player service): every new player, once; the ones a partner brought become referrals. */
@Component
class RegisterEventHandler(
    private val reader: EventReader,
    private val accrual: AccrualService,
    properties: AppProperties,
) : EventHandler {

    override val exchange = properties.events.playerExchange

    override val routingKey = ROUTE

    override fun handle(body: ByteArray) {
        val event = reader.read(body, RegisterEvent::class.java) { it.id }
        val player = event.data
        accrual.register(Registration(event.playerId, player.affiliateTag, player.affiliateAttribution.orEmpty(), player.country, player.createdAt))
    }

    private companion object {

        const val ROUTE = "register.events"
    }
}
