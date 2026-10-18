package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Me
import io.github.nekzabirov.affiliate.services.Money
import java.time.Instant

/** The signed-in account. Money is a decimal string of [currency]. */
data class MeResponse(
    val id: Long,

    val login: String,

    val role: String,

    val balance: String,

    val totalEarned: String,

    val pendingPayouts: String,

    val paidOut: String,

    val currency: String,

    val createdAt: Instant,
) {

    companion object {

        fun of(me: Me, currency: String) = MeResponse(
            id = me.account.id,
            login = me.account.login,
            role = me.account.role.name,
            balance = Money.format(me.account.balance),
            totalEarned = Money.format(me.totalEarned),
            pendingPayouts = Money.format(me.pendingPayouts),
            paidOut = Money.format(me.paidOut),
            currency = currency,
            createdAt = me.account.createdAt,
        )
    }
}
