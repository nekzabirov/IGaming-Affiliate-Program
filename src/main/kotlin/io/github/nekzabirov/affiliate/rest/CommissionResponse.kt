package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.CommissionEntry
import io.github.nekzabirov.affiliate.services.Money
import java.time.Instant
import java.time.LocalDate

/** One line of the earnings ledger; [amount] is a signed decimal string of the partner currency. */
data class CommissionResponse(
    val id: Long,

    val day: LocalDate,

    val company: String,

    val playerId: Long,

    val type: String,

    val amount: String,

    val updatedAt: Instant,
) {

    companion object {

        fun of(entry: CommissionEntry) = CommissionResponse(
            id = entry.id,
            day = entry.day,
            company = entry.company,
            playerId = entry.playerId,
            type = entry.type.name,
            amount = Money.format(entry.amount),
            updatedAt = entry.updatedAt,
        )
    }
}
