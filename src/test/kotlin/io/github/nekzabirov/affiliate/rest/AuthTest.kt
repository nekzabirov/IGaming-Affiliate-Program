package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.support.Api
import io.github.nekzabirov.affiliate.support.IntegrationTest
import io.github.nekzabirov.affiliate.support.Reply
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AuthTest : IntegrationTest() {

    @Test
    fun `config is public and says how links and money work`() {
        val reply = api.get("/api/config")

        assertThat(reply.status).isEqualTo(200)
        assertThat(reply.body["currency"].asString()).isEqualTo("USDT")
        assertThat(reply.body["siteUrl"].asString()).isEqualTo("https://example.com")
        assertThat(reply.body["tagPrefix"].asString()).isEqualTo("partner:")
        assertThat(reply.body["clickParam"].asString()).isEqualTo("click_id")
        assertThat(reply.body["cpaMinDeposit"].asString()).isEqualTo("10")
        assertThat(reply.body["payoutNetworks"].values().map { it.asString() }).containsExactly("TRC20", "ERC20", "BEP20", "TON")
        assertThat(reply.body["supportUrl"].isNull).isTrue()
        assertThat(reply.body["postbackSourceIp"].isNull).isTrue()
        assertThat(reply.body["postbackTypes"].values().map { it.asString() }).containsExactly("KEITARO")
        assertThat(reply.body["promoManifestUrl"].asString()).isEqualTo("https://promo.example.com/library/manifest.json")
    }

    @Test
    fun `register signs in, me answers the account, logout ends the session`() {
        val login = uniqueLogin("alice")
        val registered = api.post("/api/auth/register", mapOf("login" to login.uppercase(), "password" to Api.PASSWORD))

        assertThat(registered.status).isEqualTo(201)
        assertThat(registered.body["login"].asString()).isEqualTo(login)
        assertThat(registered.body["role"].asString()).isEqualTo("PARTNER")
        assertThat(registered.body["balance"].asString()).isEqualTo("0")
        assertThat(registered.body["currency"].asString()).isEqualTo("USDT")
        val cookie = checkNotNull(registered.setCookie())
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=${30 * 24 * 3600}")
        val session = checkNotNull(registered.session)
        assertThat(session).matches("[A-Za-z0-9_-]{43}")
        val stored = jdbc.sql("SELECT token_hash FROM session").query(String::class.java).single()
        assertThat(stored).hasSize(64).isNotEqualTo(session)

        val me = api.get("/api/me", session)
        assertThat(me.status).isEqualTo(200)
        assertThat(me.body["id"].asLong()).isEqualTo(accountId(login))
        assertThat(me.body["totalEarned"].asString()).isEqualTo("0")
        assertThat(me.body["pendingPayouts"].asString()).isEqualTo("0")
        assertThat(me.body["paidOut"].asString()).isEqualTo("0")

        val logout = api.post("/api/auth/logout", session = session)
        assertThat(logout.status).isEqualTo(204)
        assertThat(logout.setCookie()).contains("Max-Age=0")
        assertThat(jdbc.sql("SELECT count(*) FROM session").query(Long::class.javaObjectType).single()).isZero()

        val after = api.get("/api/me", session)
        assertThat(after.status).isEqualTo(401)
        assertThat(after.code).isEqualTo("UNAUTHENTICATED")
    }

    @Test
    fun `login answers the account and a new session, and the login is case-insensitive`() {
        val login = uniqueLogin("bob")
        api.register(login)

        val reply = api.post("/api/auth/login", mapOf("login" to " ${login.uppercase()} ", "password" to Api.PASSWORD))

        assertThat(reply.status).isEqualTo(200)
        assertThat(reply.body["login"].asString()).isEqualTo(login)
        assertThat(api.get("/api/me", reply.session).status).isEqualTo(200)
    }

    @Test
    fun `a wrong password and an unknown login get the same answer`() {
        val login = uniqueLogin("carol")
        api.register(login)

        val wrongPassword = api.post("/api/auth/login", mapOf("login" to login, "password" to "not-the-password"))
        val unknownLogin = api.post("/api/auth/login", mapOf("login" to uniqueLogin("nobody"), "password" to Api.PASSWORD))

        listOf(wrongPassword, unknownLogin).forEach {
            assertThat(it.status).isEqualTo(401)
            assertThat(it.code).isEqualTo("INVALID_CREDENTIALS")
            assertThat(it.session).isNull()
        }
        assertThat(wrongPassword.body["message"].asString()).isEqualTo(unknownLogin.body["message"].asString())
    }

    @Test
    fun `a taken login is a conflict, whatever its case`() {
        val login = uniqueLogin("dave")
        api.register(login)

        val again = newClient().post("/api/auth/register", mapOf("login" to login.uppercase(), "password" to Api.PASSWORD))

        assertThat(again.status).isEqualTo(409)
        assertThat(again.code).isEqualTo("LOGIN_TAKEN")
    }

    @Test
    fun `registration validates login and password`() {
        fun register(login: String?, password: String?) =
            newClient().post("/api/auth/register", mapOf("login" to login, "password" to password))

        listOf(
            register(null, Api.PASSWORD) to ("login" to "required"),
            register("ab", Api.PASSWORD) to ("login" to "length"),
            register("a".repeat(33), Api.PASSWORD) to ("login" to "length"),
            register("-dash-first", Api.PASSWORD) to ("login" to "format"),
            register("with space", Api.PASSWORD) to ("login" to "format"),
            register("valid-login", null) to ("password" to "required"),
            register("valid-login", "short") to ("password" to "length"),
            register("valid-login", "x".repeat(73)) to ("password" to "length"),
            // 37 characters but 74 bytes: past what bcrypt reads.
            register("valid-login", "\u0436".repeat(37)) to ("password" to "length"),
        ).forEach { (reply, expected) ->
            assertThat(reply.status).describedAs(reply.toString()).isEqualTo(400)
            assertThat(reply.code).isEqualTo("VALIDATION")
            assertThat(reply.field to reply.rule).describedAs(reply.toString()).isEqualTo(expected)
        }
    }

    @Test
    fun `a blocked account cannot sign in, and its open sessions stop working`() {
        val login = uniqueLogin("erin")
        val session = api.register(login)

        jdbc.sql("UPDATE account SET active = false WHERE login = :login").param("login", login).update()

        val me = api.get("/api/me", session)
        assertThat(me.status).isEqualTo(403)
        assertThat(me.code).isEqualTo("ACCOUNT_BLOCKED")
        val signIn = api.post("/api/auth/login", mapOf("login" to login, "password" to Api.PASSWORD))
        assertThat(signIn.status).isEqualTo(403)
        assertThat(signIn.code).isEqualTo("ACCOUNT_BLOCKED")
        assertThat(signIn.session).isNull()
        // Being blocked is only revealed to someone who knows the password.
        val guess = api.post("/api/auth/login", mapOf("login" to login, "password" to "a-wrong-guess"))
        assertThat(guess.code).isEqualTo("INVALID_CREDENTIALS")
    }

    @Test
    fun `ten failures on one login lock it for every address, and the right password does not get through`() {
        val login = uniqueLogin("frank")
        api.register(login)

        repeat(10) {
            val reply = newClient().post("/api/auth/login", mapOf("login" to login, "password" to "wrong-$it"))
            assertThat(reply.code).isEqualTo("INVALID_CREDENTIALS")
        }
        val locked = newClient().post("/api/auth/login", mapOf("login" to login, "password" to Api.PASSWORD))

        assertThat(locked.status).isEqualTo(429)
        assertThat(locked.code).isEqualTo("TOO_MANY_REQUESTS")
        assertThat(locked.session).isNull()
    }

    @Test
    fun `thirty failures from one address lock that address for every login`() {
        repeat(30) {
            val reply = api.post("/api/auth/login", mapOf("login" to uniqueLogin("guess"), "password" to "wrong-password"))
            assertThat(reply.code).isEqualTo("INVALID_CREDENTIALS")
        }
        val login = uniqueLogin("grace")
        newClient().register(login)

        val locked = api.post("/api/auth/login", mapOf("login" to login, "password" to Api.PASSWORD))
        val elsewhere = newClient().post("/api/auth/login", mapOf("login" to login, "password" to Api.PASSWORD))

        assertThat(locked.status).isEqualTo(429)
        assertThat(elsewhere.status).isEqualTo(200)
    }

    @Test
    fun `a burst of guesses at one login gets exactly the ten failures it is allowed`() {
        val login = uniqueLogin("ivan")
        newClient().register(login)

        val replies = atOnce(80) { api.post("/api/auth/login", mapOf("login" to login, "password" to "wrong-$it")) }

        assertThat(replies.count { it.status == 401 }).describedAs(replies.toString()).isEqualTo(10)
        assertThat(replies.count { it.status == 429 }).describedAs(replies.toString()).isEqualTo(70)
    }

    @Test
    fun `an attempt that was not a wrong guess gives its slot back`() {
        val login = uniqueLogin("judy")
        api.register(login)
        jdbc.sql("UPDATE account SET active = false WHERE login = :login").param("login", login).update()

        // Knowing the password of a blocked account is no failure: ten such attempts leave the allowance whole.
        repeat(10) { assertThat(api.post("/api/auth/login", mapOf("login" to login, "password" to Api.PASSWORD)).code).isEqualTo("ACCOUNT_BLOCKED") }
        jdbc.sql("UPDATE account SET active = true WHERE login = :login").param("login", login).update()
        repeat(9) { assertThat(api.post("/api/auth/login", mapOf("login" to login, "password" to "wrong-$it")).code).isEqualTo("INVALID_CREDENTIALS") }

        assertThat(api.post("/api/auth/login", mapOf("login" to login, "password" to Api.PASSWORD)).status).isEqualTo(200)
    }

    @Test
    fun `a burst of sign-ups from one address gets exactly the five accounts it is allowed`() {
        val replies = atOnce(25) { api.post("/api/auth/register", mapOf("login" to uniqueLogin("burst"), "password" to Api.PASSWORD)) }

        assertThat(replies.count { it.status == 201 }).describedAs(replies.toString()).isEqualTo(5)
        assertThat(replies.count { it.status == 429 }).describedAs(replies.toString()).isEqualTo(20)
    }

    @Test
    fun `a sign-up that creates no account does not use the address's allowance`() {
        val taken = uniqueLogin("kate")
        newClient().register(taken)

        repeat(5) { assertThat(api.post("/api/auth/register", mapOf("login" to taken, "password" to Api.PASSWORD)).code).isEqualTo("LOGIN_TAKEN") }
        repeat(5) { assertThat(api.post("/api/auth/register", mapOf("login" to uniqueLogin("kate"), "password" to "short")).code).isEqualTo("VALIDATION") }

        repeat(5) { api.register(uniqueLogin("kate")) }
        assertThat(api.post("/api/auth/register", mapOf("login" to uniqueLogin("kate"), "password" to Api.PASSWORD)).status).isEqualTo(429)
    }

    @Test
    fun `an address registers at most five accounts an hour`() {
        repeat(5) { api.register(uniqueLogin("bulk")) }

        val sixth = api.post("/api/auth/register", mapOf("login" to uniqueLogin("bulk"), "password" to Api.PASSWORD))

        assertThat(sixth.status).isEqualTo(429)
        assertThat(sixth.code).isEqualTo("TOO_MANY_REQUESTS")
        assertThat(newClient().post("/api/auth/register", mapOf("login" to uniqueLogin("bulk"), "password" to Api.PASSWORD)).status)
            .isEqualTo(201)
    }

    @Test
    fun `writes must be JSON, and an unknown field is refused by name`() {
        val form = api.post("/api/auth/login", "login=x&password=y", contentType = "application/x-www-form-urlencoded")
        val text = api.post("/api/auth/register", "{}", contentType = "text/plain")
        assertThat(form.status).isEqualTo(415)
        assertThat(form.code).isEqualTo("UNSUPPORTED_MEDIA_TYPE")
        assertThat(text.status).isEqualTo(415)

        val unknown = api.post("/api/auth/register", mapOf("login" to uniqueLogin("x"), "password" to Api.PASSWORD, "role" to "ADMIN"))
        assertThat(unknown.status).isEqualTo(400)
        assertThat(unknown.field to unknown.rule).isEqualTo("role" to "unknown_field")

        val broken = api.post("/api/auth/register", "{not json")
        assertThat(broken.status).isEqualTo(400)
        assertThat(broken.code).isEqualTo("VALIDATION")
    }

    @Test
    fun `everything else under api needs a session, and an unknown endpoint is a JSON 404`() {
        listOf("/api/me", "/api/companies", "/api/stats", "/api/payouts", "/api/admin/partners").forEach { path ->
            val reply = api.get(path)
            assertThat(reply.status).describedAs(path).isEqualTo(401)
            assertThat(reply.code).isEqualTo("UNAUTHENTICATED")
        }
        val dead = api.get("/api/me", "no-such-session")
        assertThat(dead.status).isEqualTo(401)
        assertThat(dead.setCookie()).contains("Max-Age=0")

        val session = api.register(uniqueLogin("henry"))
        val missing = api.get("/api/nothing-here", session)
        assertThat(missing.status).isEqualTo(404)
        assertThat(missing.code).isEqualTo("NOT_FOUND")
    }

    /** Sends [count] requests at the same moment, each on its own thread, and answers every reply. */
    private fun atOnce(count: Int, request: (Int) -> Reply): List<Reply> {
        val start = CountDownLatch(1)
        Executors.newVirtualThreadPerTaskExecutor().use { threads ->
            val replies = (0 until count).map { n ->
                threads.submit(
                    Callable {
                        start.await()
                        request(n)
                    },
                )
            }
            start.countDown()
            return replies.map { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
        }
    }

    private companion object {

        const val WAIT_SECONDS = 60L
    }
}
