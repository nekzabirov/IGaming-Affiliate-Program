package io.github.nekzabirov.affiliate.model

import java.math.BigDecimal
import java.time.LocalDate

/** A partner's analytics over a range of UTC days. Every amount is in nano units of the partner currency. */
data class Stats(
    val from: LocalDate,

    val to: LocalDate,

    val totals: Totals,

    /** Every day of the range, oldest first, zeros where nothing happened. */
    val daily: List<Day>,

    val companies: List<CompanyLine>,

    /** The top countries by registrations. */
    val countries: List<CountryCount>,
) {

    data class Totals(
        val registrations: Long,

        val firstDeposits: Long,

        /** First deposits per registration, in percent. */
        val conversion: BigDecimal,

        val depositCount: Long,

        val depositAmount: Long,

        val turnover: Long,

        val earned: Long,

        val earnedByType: Map<CompanyType, Long>,
    )

    data class Day(
        val day: LocalDate,

        val registrations: Long,

        val firstDeposits: Long,

        val depositAmount: Long,

        val turnover: Long,

        val earned: Long,
    )

    data class CompanyLine(
        val company: Company,

        val registrations: Long,

        val firstDeposits: Long,

        val depositAmount: Long,

        val turnover: Long,

        val earned: Long,
    )
}
