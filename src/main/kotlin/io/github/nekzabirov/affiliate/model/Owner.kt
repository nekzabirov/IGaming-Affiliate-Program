package io.github.nekzabirov.affiliate.model

/** The account behind a company, as the admin review queue shows it. */
data class Owner(
    val id: Long,

    val login: String,

    val active: Boolean,
)
