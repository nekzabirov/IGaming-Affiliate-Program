package io.github.nekzabirov.affiliate.events

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import java.time.Instant

/**
 * `data` of spin.events: one leg of money in a casino round. `amount` is the whole leg, which the trackers hear of;
 * `realAmount` is the player's own money, the only part that earns a partner anything (a product rule). A spin has no time of its
 * own; its round's `createdAt` dates it.
 *
 * `reference` is the reversed leg on a ROLLBACK, but a SETTLE may carry one too (the bonus PLACE of its round):
 * read `type`, never the presence of `reference`.
 */
data class SpinEvent(
    val id: Long,

    val type: Type,

    val amount: Long,

    val realAmount: Long,

    val reference: Reference?,

    val round: Round,
) {

    /**
     * A PLACE is a stake, a ROLLBACK reverses a leg; every other type (a SETTLE, which is a win, or one added
     * later) reads as [OTHER] and earns a partner nothing - and neither does a rollback of one.
     */
    enum class Type {
        PLACE,
        ROLLBACK,

        @JsonEnumDefaultValue
        OTHER,
    }

    data class Reference(
        val id: Long,

        val type: Type,
    )

    data class Round(
        val playerId: Long,

        val currency: String,

        val createdAt: Instant,
    )
}
