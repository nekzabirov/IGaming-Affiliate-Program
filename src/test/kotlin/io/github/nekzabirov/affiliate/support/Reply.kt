package io.github.nekzabirov.affiliate.support

import tools.jackson.databind.JsonNode
import java.net.http.HttpHeaders

/** One HTTP answer: the status, the parsed JSON body (a missing node when empty) and the headers. */
class Reply(
    val status: Int,

    val body: JsonNode,

    val headers: HttpHeaders,
) {

    /** The `ap_session` value this answer set, or null when it set none. */
    val session: String?
        get() = setCookie()?.substringAfter("ap_session=")?.substringBefore(';')

    /** The raw `Set-Cookie` header of the session cookie. */
    fun setCookie(): String? = headers.allValues("set-cookie").firstOrNull { it.startsWith("ap_session=") }

    val code: String get() = body.path("code").asString()

    val field: String get() = body.path("field").asString()

    val rule: String get() = body.path("rule").asString()

    override fun toString(): String = "$status $body"
}
