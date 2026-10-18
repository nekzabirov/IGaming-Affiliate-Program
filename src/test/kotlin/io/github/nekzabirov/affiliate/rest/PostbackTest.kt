package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.support.FakeDns
import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PostbackTest : IntegrationTest() {

    private lateinit var session: String

    @BeforeEach
    fun company() {
        session = api.register(uniqueLogin("tom"))
        api.post("/api/companies", mapOf("identity" to "tracked", "type" to "CPA", "amount" to "20"), session)
    }

    @Test
    fun `a pasted Keitaro URL is stored as its base, with the exact requests it will send`() {
        val created = api.post(
            "/api/companies/tracked/postbacks",
            mapOf("type" to "keitaro", "url" to "HTTPS://${FakeDns.TRACKER.uppercase()}/abc1234/postback?subid=REPLACE&status=REPLACE&payout=REPLACE"),
            session,
        )

        assertThat(created.status).isEqualTo(201)
        assertThat(created.body["type"].asString()).isEqualTo("KEITARO")
        assertThat(created.body["url"].asString()).isEqualTo("https://${FakeDns.TRACKER}/abc1234/postback")
        assertThat(created.body["active"].asBoolean()).isTrue()
        assertThat(created.body["lastSentAt"].isNull).isTrue()
        assertThat(created.body["lastStatus"].isNull).isTrue()
        assertThat(created.body["lastError"].isNull).isTrue()
        val examples = created.body["examples"].associate { it["event"].asString() to (it["method"].asString() to it["url"].asString()) }
        val base = "https://${FakeDns.TRACKER}/abc1234/postback"
        assertThat(examples).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                "REGISTRATION" to ("GET" to "$base?subid=8f3k2a&status=registration"),
                "DEPOSIT" to ("GET" to "$base?subid=8f3k2a&status=deposit&payout=100.00&currency=eur&tid=dep-1001"),
                "SPIN" to ("GET" to "$base?subid=8f3k2a&status=spin&payout=1.00&currency=eur&tid=spin-5001"),
                "BET" to ("GET" to "$base?subid=8f3k2a&status=bet&payout=10.00&currency=eur&tid=bet-7001"),
            ),
        )
        assertThat(api.get("/api/companies/tracked/postbacks", session).body.values().map { it["id"].asLong() })
            .containsExactly(created.body["id"].asLong())
    }

    @Test
    fun `a patch switches it off, and a new URL forgets the last attempt`() {
        val id = api.post(
            "/api/companies/tracked/postbacks",
            mapOf("type" to "KEITARO", "url" to "https://${FakeDns.TRACKER}/abc1234/postback"),
            session,
        ).body["id"].asLong()
        jdbc.sql("UPDATE postback SET last_sent_at = now(), last_status = 404, last_error = 'HTTP 404' WHERE id = :id").param("id", id).update()

        val off = api.patch("/api/companies/tracked/postbacks/$id", mapOf("active" to false), session)
        assertThat(off.status).isEqualTo(200)
        assertThat(off.body["active"].asBoolean()).isFalse()
        assertThat(off.body["lastStatus"].asInt()).isEqualTo(404)

        val moved = api.patch("/api/companies/tracked/postbacks/$id", mapOf("url" to "http://${FakeDns.TRACKER}:8080/newkey/postback"), session)
        assertThat(moved.body["url"].asString()).isEqualTo("http://${FakeDns.TRACKER}:8080/newkey/postback")
        assertThat(moved.body["active"].asBoolean()).isFalse()
        assertThat(moved.body["lastStatus"].isNull).isTrue()
        assertThat(moved.body["lastError"].isNull).isTrue()
        assertThat(moved.body["examples"][0]["url"].asString()).startsWith("http://${FakeDns.TRACKER}:8080/newkey/postback?subid=")

        val bad = api.patch("/api/companies/tracked/postbacks/$id", mapOf("url" to "https://${FakeDns.TRACKER}/not-postback"), session)
        assertThat(bad.field to bad.rule).isEqualTo("url" to "format")
        assertThat(api.patch("/api/companies/tracked/postbacks/999", mapOf("active" to true), session).status).isEqualTo(404)
    }

    @Test
    fun `delete removes it, and a second delete is a 404`() {
        val id = api.post(
            "/api/companies/tracked/postbacks",
            mapOf("type" to "KEITARO", "url" to "https://${FakeDns.TRACKER}/abc1234/postback"),
            session,
        ).body["id"].asLong()

        assertThat(api.delete("/api/companies/tracked/postbacks/$id", session).status).isEqualTo(204)
        assertThat(api.delete("/api/companies/tracked/postbacks/$id", session).status).isEqualTo(404)
        assertThat(api.get("/api/companies/tracked/postbacks", session).body.size()).isZero()
    }

    @Test
    fun `a company holds at most ten postbacks`() {
        repeat(10) {
            val reply = api.post(
                "/api/companies/tracked/postbacks",
                mapOf("type" to "KEITARO", "url" to "https://${FakeDns.TRACKER}/key$it/postback"),
                session,
            )
            assertThat(reply.status).isEqualTo(201)
        }

        val eleventh = api.post(
            "/api/companies/tracked/postbacks",
            mapOf("type" to "KEITARO", "url" to "https://${FakeDns.TRACKER}/key10/postback"),
            session,
        )

        assertThat(eleventh.status).isEqualTo(409)
        assertThat(eleventh.code).isEqualTo("LIMIT_REACHED")
    }

    @Test
    fun `a URL the guard refuses, or that is not a Keitaro postback URL, is a validation error`() {
        fun create(type: String?, url: String?) =
            api.post("/api/companies/tracked/postbacks", mapOf("type" to type, "url" to url), session)

        listOf(
            create(null, "https://${FakeDns.TRACKER}/k/postback") to ("type" to "required"),
            create("BINOM", "https://${FakeDns.TRACKER}/k/postback") to ("type" to "invalid"),
            create("KEITARO", null) to ("url" to "required"),
            create("KEITARO", "ftp://${FakeDns.TRACKER}/k/postback") to ("url" to "format"),
            create("KEITARO", "${FakeDns.TRACKER}/k/postback") to ("url" to "format"),
            create("KEITARO", "https://user:pass@${FakeDns.TRACKER}/k/postback") to ("url" to "format"),
            create("KEITARO", "https://${FakeDns.TRACKER}/k/postback#frag") to ("url" to "format"),
            create("KEITARO", "https://${FakeDns.TRACKER}:22/k/postback") to ("url" to "format"),
            create("KEITARO", "https://${FakeDns.TRACKER}/postback") to ("url" to "format"),
            create("KEITARO", "https://${FakeDns.TRACKER}/a/b/postback") to ("url" to "format"),
            create("KEITARO", "https://${FakeDns.INTERNAL}/k/postback") to ("url" to "blocked_host"),
            create("KEITARO", "https://${FakeDns.MIXED}/k/postback") to ("url" to "blocked_host"),
            create("KEITARO", "http://127.0.0.1/k/postback") to ("url" to "blocked_host"),
            create("KEITARO", "http://[::1]/k/postback") to ("url" to "blocked_host"),
            create("KEITARO", "http://169.254.169.254/k/postback") to ("url" to "blocked_host"),
            create("KEITARO", "http://billing-service/k/postback") to ("url" to "blocked_host"),
            create("KEITARO", "http://billing.corp.internal/k/postback") to ("url" to "blocked_host"),
            create("KEITARO", "https://${FakeDns.NOWHERE}/k/postback") to ("url" to "unresolvable_host"),
        ).forEach { (reply, expected) ->
            assertThat(reply.status).describedAs(reply.toString()).isEqualTo(400)
            assertThat(reply.field to reply.rule).describedAs(reply.toString()).isEqualTo(expected)
        }
        assertThat(jdbc.sql("SELECT count(*) FROM postback").query(Long::class.javaObjectType).single()).isZero()
    }
}
