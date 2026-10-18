package io.github.nekzabirov.affiliate.model

import java.time.LocalDate

/**
 * A platform fact this app has acted on, with what it changed: a stake records the turnover and the commission
 * it added (and the commission row it added to), so its ROLLBACK takes back exactly that, whatever the rate or
 * the company's terms are by then. A key may also hold nothing: a registration whose partner tag was refused has
 * no company, and a stake whose rollback came first was never counted. Amounts are nano units of the partner
 * currency.
 */
data class ProcessedFact(
    val key: String,

    val turnover: Long,

    val commission: Long,

    val commissionSource: String?,

    val companyId: Long?,

    val playerId: Long?,

    val day: LocalDate,
)
