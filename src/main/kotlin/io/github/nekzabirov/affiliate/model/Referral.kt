package io.github.nekzabirov.affiliate.model

import java.time.Instant

/** A player attributed to a company ("Players" in the cabinet). Amounts are nano units of the partner currency. */
data class Referral(
    val playerId: Long,

    val companyId: Long,

    val clickId: String?,

    val country: String?,

    val registeredAt: Instant,

    val firstDepositAt: Instant?,

    val firstDepositAmount: Long?,

    val firstDepositInvoiceId: Long?,

    val createdAt: Instant,
)
