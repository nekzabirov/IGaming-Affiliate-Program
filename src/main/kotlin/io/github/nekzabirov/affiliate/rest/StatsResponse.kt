package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Stats
import io.github.nekzabirov.affiliate.services.Money
import java.time.LocalDate

/** Analytics over a range of UTC days. Money is a decimal string of the partner currency, conversion a percent. */
data class StatsResponse(
    val from: LocalDate,

    val to: LocalDate,

    val totals: Totals,

    val daily: List<Day>,

    val companies: List<CompanyLine>,

    val countries: List<Country>,
) {

    data class Totals(
        val registrations: Long,

        val firstDeposits: Long,

        val conversion: String,

        val depositCount: Long,

        val depositAmount: String,

        val turnover: String,

        val earned: String,

        val earnedByType: Map<String, String>,
    )

    data class Day(
        val day: LocalDate,

        val registrations: Long,

        val firstDeposits: Long,

        val depositAmount: String,

        val turnover: String,

        val earned: String,
    )

    data class CompanyLine(
        val identity: String,

        val type: String,

        val status: String,

        val registrations: Long,

        val firstDeposits: Long,

        val depositAmount: String,

        val turnover: String,

        val earned: String,
    )

    data class Country(
        val country: String?,

        val registrations: Long,

        val firstDeposits: Long,
    )

    companion object {

        fun of(stats: Stats) = StatsResponse(
            from = stats.from,
            to = stats.to,
            totals = Totals(
                registrations = stats.totals.registrations,
                firstDeposits = stats.totals.firstDeposits,
                conversion = Money.format(stats.totals.conversion),
                depositCount = stats.totals.depositCount,
                depositAmount = Money.format(stats.totals.depositAmount),
                turnover = Money.format(stats.totals.turnover),
                earned = Money.format(stats.totals.earned),
                earnedByType = stats.totals.earnedByType.entries.associate { (type, amount) -> type.name to Money.format(amount) },
            ),
            daily = stats.daily.map {
                Day(
                    day = it.day,
                    registrations = it.registrations,
                    firstDeposits = it.firstDeposits,
                    depositAmount = Money.format(it.depositAmount),
                    turnover = Money.format(it.turnover),
                    earned = Money.format(it.earned),
                )
            },
            companies = stats.companies.map {
                CompanyLine(
                    identity = it.company.identity,
                    type = it.company.type.name,
                    status = it.company.status.name,
                    registrations = it.registrations,
                    firstDeposits = it.firstDeposits,
                    depositAmount = Money.format(it.depositAmount),
                    turnover = Money.format(it.turnover),
                    earned = Money.format(it.earned),
                )
            },
            countries = stats.countries.map { Country(it.country, it.registrations, it.firstDeposits) },
        )
    }
}
