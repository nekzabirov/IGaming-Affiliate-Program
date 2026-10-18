package io.github.nekzabirov.affiliate.model

import java.math.BigDecimal
import java.time.Instant

/** A partner's campaign; its [identity] is the player's affiliate tag `partner:<identity>`. */
data class Company(
    val id: Long,

    val identity: String,

    val accountId: Long,

    val type: CompanyType,

    /** CPA: the amount in the partner currency; REVSHARE_*: a percent. */
    val amount: BigDecimal,

    val status: CompanyStatus,

    val createdAt: Instant,

    val updatedAt: Instant,
)
