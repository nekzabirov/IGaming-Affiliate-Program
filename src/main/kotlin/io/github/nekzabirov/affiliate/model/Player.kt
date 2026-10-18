package io.github.nekzabirov.affiliate.model

/** A referral with its all-time activity and what it earned the partner (nano units of the partner currency). */
data class Player(
    val referral: Referral,

    val activity: PlayerActivity,

    val earned: Long,
)
