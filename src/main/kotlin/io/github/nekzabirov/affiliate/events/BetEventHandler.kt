package io.github.nekzabirov.affiliate.events

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import io.github.nekzabirov.affiliate.services.AccrualService
import org.springframework.stereotype.Component

/**
 * bet.events (the sportsbook): a BET is a stake; a ROLLBACK of a BET takes that stake back; a WIN
 * earns a partner nothing, and neither does a rollback of one.
 */
@Component
class BetEventHandler(
    private val reader: EventReader,
    private val accrual: AccrualService,
    properties: AppProperties,
) : EventHandler {

    override val exchange = properties.events.casinoExchange

    override val routingKey = ROUTE

    override fun handle(body: ByteArray) {
        val event = reader.read(body, BetEvent::class.java) { it.bet.playerId }
        val leg = event.data
        when (leg.type) {
            BetEvent.Type.BET ->
                accrual.stake(
                    AccrualService.BET + leg.id,
                    PostbackEvent.Kind.BET,
                    leg.id,
                    event.playerId,
                    leg.realAmount,
                    leg.amount,
                    leg.bet.currency,
                    leg.bet.createdAt,
                )
            BetEvent.Type.ROLLBACK -> {
                val reversed = leg.reference
                if (reversed != null && reversed.type == BetEvent.Type.BET) {
                    accrual.reverse(AccrualService.BET + leg.id, AccrualService.BET + reversed.id, leg.bet.createdAt)
                }
            }
            BetEvent.Type.OTHER -> Unit
        }
    }

    private companion object {

        const val ROUTE = "bet.events"
    }
}
