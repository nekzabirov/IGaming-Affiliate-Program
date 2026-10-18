package io.github.nekzabirov.affiliate.rest

/** A withdrawal: the amount of the partner currency, the network and the wallet address on it, given every time. */
data class CreatePayoutRequest(
    val amount: String? = null,

    val network: String? = null,

    val address: String? = null,
)
