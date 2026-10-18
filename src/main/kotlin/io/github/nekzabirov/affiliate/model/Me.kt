package io.github.nekzabirov.affiliate.model

/** The signed-in account with its money figures, in nano units of the partner currency. */
data class Me(
    val account: Account,

    val totalEarned: Long,

    val pendingPayouts: Long,

    val paidOut: Long,
)
