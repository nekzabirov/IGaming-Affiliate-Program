package io.github.nekzabirov.affiliate.model

import java.time.Instant

/**
 * A partner's (or an admin's) account. The password hash is deliberately not here: an account travels through
 * request attributes and log calls, and a hash must never ride along; [Credentials] carries it for the one
 * check that needs it.
 */
data class Account(
    val id: Long,

    val login: String,

    val role: Role,

    val active: Boolean,

    /** Available balance in nano units of the partner currency; it may go negative after a rollback. */
    val balance: Long,

    val createdAt: Instant,

    val updatedAt: Instant,
)
