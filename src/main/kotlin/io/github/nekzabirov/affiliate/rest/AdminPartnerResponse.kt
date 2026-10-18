package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Partner
import io.github.nekzabirov.affiliate.services.Money
import java.time.Instant

/** An account in the admin partner list; [companies] is how many companies it has. */
data class AdminPartnerResponse(
    val id: Long,

    val login: String,

    val role: String,

    val active: Boolean,

    val balance: String,

    val companies: Long,

    val createdAt: Instant,
) {

    companion object {

        fun of(partner: Partner) = AdminPartnerResponse(
            id = partner.account.id,
            login = partner.account.login,
            role = partner.account.role.name,
            active = partner.account.active,
            balance = Money.format(partner.account.balance),
            companies = partner.companies,
            createdAt = partner.account.createdAt,
        )
    }
}
