package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Player
import io.github.nekzabirov.affiliate.services.Money
import java.time.Instant

/** A player of a company with all-time figures. Money is a decimal string of the partner currency. */
data class PlayerResponse(
    val playerId: Long,

    val clickId: String?,

    val country: String?,

    val registeredAt: Instant,

    val firstDepositAt: Instant?,

    val firstDepositAmount: String?,

    val depositCount: Long,

    val depositAmount: String,

    val turnover: String,

    val earned: String,
) {

    companion object {

        fun of(player: Player) = PlayerResponse(
            playerId = player.referral.playerId,
            clickId = player.referral.clickId,
            country = player.referral.country,
            registeredAt = player.referral.registeredAt,
            firstDepositAt = player.referral.firstDepositAt,
            firstDepositAmount = player.referral.firstDepositAmount?.let(Money::format),
            depositCount = player.activity.depositCount,
            depositAmount = Money.format(player.activity.depositAmount),
            turnover = Money.format(player.activity.turnover),
            earned = Money.format(player.earned),
        )
    }
}
