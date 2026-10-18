package io.github.nekzabirov.affiliate.model

/** A company with its owner: the admin review queue shows both, and a fact accrues only when both are active. */
data class OwnedCompany(
    val company: Company,

    val owner: Owner,
)
