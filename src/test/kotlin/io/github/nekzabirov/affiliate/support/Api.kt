package io.github.nekzabirov.affiliate.support

import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.MissingNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * A browser stand-in over real HTTP: JSON in and out, the session passed as a cookie by hand, and one client
 * address per instance (CF-Connecting-IP), so the in-memory rate limits of one test never touch another.
 */
class Api(private val port: Int, private val json: JsonMapper, val ip: String) {

    private val http = HttpClient.newHttpClient()

    fun get(path: String, session: String? = null): Reply = send("GET", path, null, session)

    fun post(path: String, body: Any? = null, session: String? = null, contentType: String? = JSON): Reply =
        send("POST", path, body, session, contentType)

    fun patch(path: String, body: Any?, session: String? = null): Reply = send("PATCH", path, body, session)

    fun delete(path: String, session: String? = null): Reply = send("DELETE", path, null, session, contentType = null)

    /** Registers [login] with a valid password and answers the session. */
    fun register(login: String, password: String = PASSWORD): String {
        val reply = post("/api/auth/register", mapOf("login" to login, "password" to password))
        check(reply.status == 201) { "register $login: $reply" }
        return checkNotNull(reply.session)
    }

    private fun send(method: String, path: String, body: Any?, session: String?, contentType: String? = JSON): Reply {
        val text = when (body) {
            null -> null
            is String -> body
            else -> json.writeValueAsString(body)
        }
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method(method, if (text == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(text))
            .header("CF-Connecting-IP", ip)
            .apply { if (contentType != null && text != null) header("Content-Type", contentType) }
            .apply { if (session != null) header("Cookie", "ap_session=$session") }
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val parsed = if (response.body().isBlank()) MissingNode.getInstance() else json.readTree(response.body())
        return Reply(response.statusCode(), parsed, response.headers())
    }

    companion object {

        const val JSON = "application/json"

        const val PASSWORD = "correct-horse-battery"
    }
}
