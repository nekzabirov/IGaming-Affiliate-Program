package io.github.nekzabirov.affiliate.model

/** Registrations and first deposits of one country in a range; [country] is null when the player service did not know it. */
data class CountryCount(
    val country: String?,

    val registrations: Long,

    val firstDeposits: Long,
)
