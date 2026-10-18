package io.github.nekzabirov.affiliate.rest

/** A partner's proposal: the identity, the commission model and the amount (CPA) or percent (revenue share). */
data class CreateCompanyRequest(
    val identity: String? = null,

    val type: String? = null,

    val amount: String? = null,
)
