package io.github.nekzabirov.affiliate.rest

/** Either field may be left out; what is left out stays as it is. */
data class UpdatePostbackRequest(
    val active: Boolean? = null,

    val url: String? = null,
)
