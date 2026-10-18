package io.github.nekzabirov.affiliate.model

/** What a partner has asked for and not received yet, and what was paid, in nano units of the partner currency. */
data class PayoutTotals(
    val pending: Long,

    val paid: Long,
)
