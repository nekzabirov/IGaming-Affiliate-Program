package io.github.nekzabirov.affiliate.rest

/** The tracker type and the postback URL the partner copied out of it. */
data class CreatePostbackRequest(
    val type: String? = null,

    val url: String? = null,
)
