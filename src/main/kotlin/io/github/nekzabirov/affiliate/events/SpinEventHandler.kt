package io.github.nekzabirov.affiliate.events

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import io.github.nekzabirov.affiliate.services.AccrualService
import org.springframework.stereotype.Component

/**
 * spin.events (the casino): a PLACE is a stake; a ROLLBACK of a PLACE takes that stake back; a SETTLE (a win)
 * earns a partner nothing, and neither does a rollback of one.
 */
@Component
class SpinEventHandler(
    private val reader: EventReader,
    private val accrual: AccrualService,
    properties: AppProperties,
) : EventHandler {

    override val exchange = properties.events.casinoExchange

    override val routingKey = ROUTE

    override fun handle(body: ByteArray) {
        val event = reader.read(body, SpinEvent::class.java) { it.round.playerId }
        val spin = event.data
        when (spin.type) {
            SpinEvent.Type.PLACE ->
                accrual.stake(
                    AccrualService.SPIN + spin.id,
                    PostbackEvent.Kind.SPIN,
                    spin.id,
                    event.playerId,
                    spin.realAmount,
                    spin.amount,
                    spin.round.currency,
                    spin.round.createdAt,
                )
            SpinEvent.Type.ROLLBACK -> {
                val reversed = spin.reference
                if (reversed != null && reversed.type == SpinEvent.Type.PLACE) {
                    accrual.reverse(AccrualService.SPIN + spin.id, AccrualService.SPIN + reversed.id, spin.round.createdAt)
                }
            }
            SpinEvent.Type.OTHER -> Unit
        }
    }

    private companion object {

        const val ROUTE = "spin.events"
    }
}
