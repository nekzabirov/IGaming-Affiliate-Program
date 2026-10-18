package io.github.nekzabirov.affiliate.model

/** A company with its all-time figures, as the company list and card show it. */
data class CompanySummary(
    val company: Company,

    val totals: CompanyTotals,
)
