package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.model.Account

/**
 * A new session: the account and the raw token that goes into the cookie, the only place the token ever exists.
 * Not a data class, so toString() never prints the token.
 */
class SignIn(
    val account: Account,

    val token: String,
)
