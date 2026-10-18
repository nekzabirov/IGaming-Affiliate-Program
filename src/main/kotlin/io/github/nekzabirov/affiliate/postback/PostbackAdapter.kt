package io.github.nekzabirov.affiliate.postback

import io.github.nekzabirov.affiliate.model.PostbackType
import java.net.URI

/** One tracker's postback dialect. Adding a tracker is one implementation, one [PostbackType] and its UI instructions. */
interface PostbackAdapter {

    val type: PostbackType

    /**
     * Validates the URL a partner pasted from their tracker and answers the base URL to store, or throws
     * ValidationException(field = "url"). The host must pass the SSRF guard.
     */
    fun normalize(raw: String): String

    /** The exact GET this app sends for [event]; [base] is a URL [normalize] produced. */
    fun build(base: String, event: PostbackEvent): URI
}
