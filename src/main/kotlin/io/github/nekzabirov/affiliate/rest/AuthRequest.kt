package io.github.nekzabirov.affiliate.rest

/** The body of both sign-up and sign-in. */
data class AuthRequest(
    val login: String? = null,

    val password: String? = null,
)
