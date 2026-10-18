package io.github.nekzabirov.affiliate.events

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import java.time.Instant

/**
 * `data` of bet.events: one leg of money on a sportsbook bet. `amount` is the whole leg, which the trackers hear of;
 * a BET takes real money, then lock money, and only the player's own, `realAmount`, earns a partner anything (a
 * product rule). A leg has no time of its
 * own; its bet's `createdAt` dates it. `reference` is the reversed leg on a ROLLBACK.
 */
data class BetEvent(
    val id: Long,

    val type: Type,

    val amount: Long,

    val realAmount: Long,

    val reference: Reference?,

    val bet: Bet,
) {

    /**
     * A BET is a stake, a ROLLBACK reverses a leg; every other type (a WIN, or one added later) reads as [OTHER]
     * and earns a partner nothing - and neither does a rollback of one.
     */
    enum class Type {
        BET,
        ROLLBACK,

        @JsonEnumDefaultValue
        OTHER,
    }

    data class Reference(
        val id: Long,

        val type: Type,
    )

    data class Bet(
        val playerId: Long,

        val currency: String,

        val createdAt: Instant,
    )
}
