package io.github.nekzabirov.affiliate.model

/** An account in the admin partner list, with how many companies it has. */
data class Partner(
    val account: Account,

    val companies: Long,
)
