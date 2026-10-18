package io.github.nekzabirov.affiliate.events

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import java.time.Instant

/**
 * `data` of invoice.events: the player service's Invoice, of which the app reads these. `amount` is positive nano
 * units of `currency`; `type` says which way the money moved. A publisher may republish an unchanged SUCCESS (on a
 * payment provider's webhook redelivery, say), so the same invoice may arrive more than once.
 *
 * `player` is the owner's whole Player snapshot, as of the invoice: the same fields register.events carries, of
 * which attribution reads the same ones, for a deposit that comes before its registration.
 */
data class InvoiceEvent(
    val id: Long,

    val type: Type,

    val status: Status,

    val amount: Long,

    val currency: String,

    val updatedAt: Instant,

    val player: Player,
) {

    /** Only a CREDIT (a deposit) is acted on; every other type reads as [OTHER] and is acknowledged untouched. */
    enum class Type {
        CREDIT,

        @JsonEnumDefaultValue
        OTHER,
    }

    /** Only a SUCCESS (the money landed) is acted on; every other status reads as [OTHER]. */
    enum class Status {
        SUCCESS,

        @JsonEnumDefaultValue
        OTHER,
    }

    data class Player(
        val id: Long,

        val affiliateTag: String?,

        val affiliateAttribution: Map<String, String>?,

        val country: String?,

        val createdAt: Instant,
    )
}
