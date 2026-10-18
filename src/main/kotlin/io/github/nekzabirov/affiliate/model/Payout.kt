package io.github.nekzabirov.affiliate.model

import java.time.Instant

/** A withdrawal request; its amount was reserved (debited from the balance) when it was made. */
data class Payout(
    val id: Long,

    val accountId: Long,

    /** Nano units of [currency]. */
    val amount: Long,

    val currency: String,

    val network: String,

    val address: String,

    val status: PayoutStatus,

    val txHash: String?,

    val comment: String?,

    val createdAt: Instant,

    val updatedAt: Instant,
)
