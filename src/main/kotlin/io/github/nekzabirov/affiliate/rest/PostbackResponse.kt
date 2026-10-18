package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Postback
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import java.net.URI
import java.time.Instant

/** A postback with the outcome of its last attempt and the exact requests it sends for each event. */
data class PostbackResponse(
    val id: Long,

    val type: String,

    val url: String,

    val active: Boolean,

    val lastSentAt: Instant?,

    val lastStatus: Int?,

    val lastError: String?,

    val createdAt: Instant,

    val examples: List<Example>,
) {

    /** One request as this app would send it, built with sample values by the real adapter. */
    data class Example(
        val event: String,

        val method: String,

        val url: String,
    )

    companion object {

        private const val METHOD = "GET"

        fun of(postback: Postback, examples: List<Pair<PostbackEvent.Kind, URI>>) = PostbackResponse(
            id = postback.id,
            type = postback.type.name,
            url = postback.url,
            active = postback.active,
            lastSentAt = postback.lastSentAt,
            lastStatus = postback.lastStatus,
            lastError = postback.lastError,
            createdAt = postback.createdAt,
            examples = examples.map { (kind, uri) -> Example(kind.name, METHOD, uri.toString()) },
        )
    }
}
