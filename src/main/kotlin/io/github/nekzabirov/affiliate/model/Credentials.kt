package io.github.nekzabirov.affiliate.model

/** An account with its bcrypt hash, read only for a login. Not a data class, so toString() never prints the hash. */
class Credentials(
    val account: Account,

    val passwordHash: String,
)
