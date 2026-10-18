package io.github.nekzabirov.affiliate.model

import java.time.Instant
import java.time.LocalDate

/** One line of a partner's earnings ledger, with the identity of the company it was earned on. */
data class CommissionEntry(
    val id: Long,

    val day: LocalDate,

    val company: String,

    val playerId: Long,

    val type: CompanyType,

    /** Signed nano units of the partner currency. */
    val amount: Long,

    val updatedAt: Instant,
)
