package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Payout
import io.github.nekzabirov.affiliate.services.Money
import java.time.Instant

/** A withdrawal request; [txHash] and [comment] are written by staff by hand. */
data class PayoutResponse(
    val id: Long,

    val amount: String,

    val currency: String,

    val network: String,

    val address: String,

    val status: String,

    val txHash: String?,

    val comment: String?,

    val createdAt: Instant,

    val updatedAt: Instant,
) {

    companion object {

        fun of(payout: Payout) = PayoutResponse(
            id = payout.id,
            amount = Money.format(payout.amount),
            currency = payout.currency,
            network = payout.network,
            address = payout.address,
            status = payout.status.name,
            txHash = payout.txHash,
            comment = payout.comment,
            createdAt = payout.createdAt,
            updatedAt = payout.updatedAt,
        )
    }
}
