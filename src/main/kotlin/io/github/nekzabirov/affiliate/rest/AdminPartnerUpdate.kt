package io.github.nekzabirov.affiliate.rest

/** Blocks (`false`) or unblocks (`true`) a partner. */
data class AdminPartnerUpdate(
    val active: Boolean? = null,
)
