package io.github.nekzabirov.affiliate.model

/** A company in the admin review queue: its terms, its figures and the account behind it. */
data class CompanyReview(
    val company: Company,

    val totals: CompanyTotals,

    val owner: Owner,
)
