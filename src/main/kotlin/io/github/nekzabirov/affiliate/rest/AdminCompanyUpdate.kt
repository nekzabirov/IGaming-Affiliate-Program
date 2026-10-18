package io.github.nekzabirov.affiliate.rest

/** An admin's review of a company: its status and/or its final amount; what is left out stays as it is. */
data class AdminCompanyUpdate(
    val status: String? = null,

    val amount: String? = null,
)
