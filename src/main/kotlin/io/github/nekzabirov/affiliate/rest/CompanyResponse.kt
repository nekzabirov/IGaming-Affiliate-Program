package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.AffiliateTag
import io.github.nekzabirov.affiliate.model.CompanySummary
import io.github.nekzabirov.affiliate.services.Money
import java.time.Instant

/** A company as the cabinet shows it, with the tag and the ready partner link. Money is a decimal string. */
data class CompanyResponse(
    val identity: String,

    val type: String,

    /** CPA: an amount of the partner currency; revenue share: a percent. */
    val amount: String,

    val status: String,

    val tag: String,

    val link: String,

    val createdAt: Instant,

    val stats: Totals,
) {

    /** All-time figures. */
    data class Totals(
        val registrations: Long,

        val firstDeposits: Long,

        val earned: String,
    )

    companion object {

        fun of(summary: CompanySummary, siteOrigin: String): CompanyResponse {
            val company = summary.company
            return CompanyResponse(
                identity = company.identity,
                type = company.type.name,
                amount = Money.format(company.amount),
                status = company.status.name,
                tag = company.identity,
                link = AffiliateTag.link(siteOrigin, company.identity),
                createdAt = company.createdAt,
                stats = Totals(
                    registrations = summary.totals.registrations,
                    firstDeposits = summary.totals.firstDeposits,
                    earned = Money.format(summary.totals.earned),
                ),
            )
        }
    }
}
