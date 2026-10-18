package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.support.FakeDns
import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CompanyTest : IntegrationTest() {

    @Test
    fun `a new company is in review, with its tag and a ready partner link`() {
        val session = api.register(uniqueLogin("ivy"))

        val created = api.post("/api/companies", mapOf("identity" to "Summer_Promo", "type" to "cpa", "amount" to "25.5"), session)

        assertThat(created.status).isEqualTo(201)
        assertThat(created.body["identity"].asString()).isEqualTo("summer_promo")
        assertThat(created.body["type"].asString()).isEqualTo("CPA")
        assertThat(created.body["amount"].asString()).isEqualTo("25.5")
        assertThat(created.body["status"].asString()).isEqualTo("IN_REVIEW")
        assertThat(created.body["tag"].asString()).isEqualTo("summer_promo")
        assertThat(created.body["link"].asString())
            .isEqualTo("https://example.com/?affiliate=summer_promo&click_id={subid}")
        assertThat(created.body["stats"]["registrations"].asLong()).isZero()
        assertThat(created.body["stats"]["earned"].asString()).isEqualTo("0")

        val listed = api.get("/api/companies", session)
        assertThat(listed.body.values().map { it["identity"].asString() }).containsExactly("summer_promo")
        assertThat(api.get("/api/companies/SUMMER_PROMO", session).body["identity"].asString()).isEqualTo("summer_promo")
    }

    @Test
    fun `a revenue share is a percent of at most 100`() {
        val session = api.register(uniqueLogin("jack"))

        val full = api.post("/api/companies", mapOf("identity" to "share-all", "type" to "REVSHARE_DEPOSIT", "amount" to "100"), session)
        val over = api.post("/api/companies", mapOf("identity" to "share-more", "type" to "REVSHARE_TURNOVER", "amount" to "100.01"), session)

        assertThat(full.status).isEqualTo(201)
        assertThat(over.status).isEqualTo(400)
        assertThat(over.field to over.rule).isEqualTo("amount" to "max")
    }

    @Test
    fun `terms of zero are valid for every type, for a partner paid up front`() {
        val session = api.register(uniqueLogin("gina"))

        val created = listOf(
            Triple("gift-cpa", "CPA", "0"),
            Triple("gift-cpa-cents", "CPA", "0.00"),
            Triple("gift-deposits", "REVSHARE_DEPOSIT", "0"),
            Triple("gift-deposits-cents", "REVSHARE_DEPOSIT", "0.00"),
            Triple("gift-turnover", "REVSHARE_TURNOVER", "0"),
            Triple("gift-turnover-cents", "REVSHARE_TURNOVER", "0.00"),
            Triple("gift-number", "CPA", 0),
        ).map { (identity, type, amount) ->
            api.post("/api/companies", mapOf("identity" to identity, "type" to type, "amount" to amount), session)
        }

        created.forEach {
            assertThat(it.status).describedAs(it.toString()).isEqualTo(201)
            assertThat(it.body["amount"].asString()).describedAs(it.toString()).isEqualTo("0")
            assertThat(it.body["status"].asString()).isEqualTo("IN_REVIEW")
        }
        assertThat(api.get("/api/companies/gift-turnover", session).body["amount"].asString()).isEqualTo("0")
        assertThat(jdbc.sql("SELECT count(*) FROM company WHERE amount = 0").query(Long::class.javaObjectType).single()).isEqualTo(7)
    }

    @Test
    fun `identity, type and amount are validated`() {
        val session = api.register(uniqueLogin("kate"))
        fun create(identity: Any?, type: Any?, amount: Any?) =
            api.post("/api/companies", mapOf("identity" to identity, "type" to type, "amount" to amount), session)

        listOf(
            create(null, "CPA", "10") to ("identity" to "required"),
            create("ab", "CPA", "10") to ("identity" to "length"),
            create("a".repeat(49), "CPA", "10") to ("identity" to "length"),
            create("double--dash", "CPA", "10") to ("identity" to "format"),
            create("dot.ted", "CPA", "10") to ("identity" to "format"),
            create("-leading", "CPA", "10") to ("identity" to "format"),
            create("good-one", null, "10") to ("type" to "required"),
            create("good-one", "HYBRID", "10") to ("type" to "invalid"),
            create("good-one", "CPA", null) to ("amount" to "required"),
            create("good-one", "CPA", "-5") to ("amount" to "format"),
            create("good-one", "CPA", -5) to ("amount" to "format"),
            create("good-one", "CPA", "00") to ("amount" to "format"),
            create("good-one", "CPA", "1.001") to ("amount" to "format"),
            create("good-one", "CPA", "1e3") to ("amount" to "format"),
            create("good-one", "REVSHARE_DEPOSIT", "-0.01") to ("amount" to "format"),
            create("good-one", "REVSHARE_DEPOSIT", "12.345") to ("amount" to "format"),
            create("good-one", "REVSHARE_TURNOVER", "-1") to ("amount" to "format"),
        ).forEach { (reply, expected) ->
            assertThat(reply.status).describedAs(reply.toString()).isEqualTo(400)
            assertThat(reply.field to reply.rule).describedAs(reply.toString()).isEqualTo(expected)
        }
        assertThat(jdbc.sql("SELECT count(*) FROM company").query(Long::class.javaObjectType).single()).isZero()
        assertThat(create("good-one", "CPA", 12.5).status).describedAs("a JSON number is read as its text").isEqualTo(201)
    }

    @Test
    fun `an identity is unique across all partners`() {
        val first = api.register(uniqueLogin("leo"))
        val second = newClient().register(uniqueLogin("mia"))
        api.post("/api/companies", mapOf("identity" to "taken", "type" to "CPA", "amount" to "10"), first)

        val clash = api.post("/api/companies", mapOf("identity" to "TAKEN", "type" to "CPA", "amount" to "10"), second)

        assertThat(clash.status).isEqualTo(409)
        assertThat(clash.code).isEqualTo("IDENTITY_TAKEN")
    }

    @Test
    fun `another partner's company is a 404 everywhere`() {
        val owner = api.register(uniqueLogin("nina"))
        val stranger = newClient().register(uniqueLogin("oscar"))
        api.post("/api/companies", mapOf("identity" to "private-one", "type" to "CPA", "amount" to "10"), owner)
        val postback = api.post(
            "/api/companies/private-one/postbacks",
            mapOf("type" to "KEITARO", "url" to "https://${FakeDns.TRACKER}/abc1234/postback"),
            owner,
        )
        val postbackId = postback.body["id"].asLong()

        listOf(
            api.get("/api/companies/private-one", stranger),
            api.get("/api/companies/private-one/players", stranger),
            api.get("/api/companies/private-one/postbacks", stranger),
            api.post("/api/companies/private-one/postbacks", mapOf("type" to "KEITARO", "url" to "https://${FakeDns.TRACKER}/k/postback"), stranger),
            api.patch("/api/companies/private-one/postbacks/$postbackId", mapOf("active" to false), stranger),
            api.delete("/api/companies/private-one/postbacks/$postbackId", stranger),
            api.get("/api/stats?company=private-one", stranger),
            api.get("/api/commissions?company=private-one", stranger),
        ).forEach {
            assertThat(it.status).describedAs(it.toString()).isEqualTo(404)
            assertThat(it.code).isEqualTo("NOT_FOUND")
        }
        assertThat(api.get("/api/companies", stranger).body.size()).isZero()
        assertThat(jdbc.sql("SELECT active FROM postback").query(Boolean::class.javaObjectType).single()).isTrue()
    }

    @Test
    fun `the players page shows each player's all-time figures, newest registration first`() {
        val login = uniqueLogin("paul")
        val session = api.register(login)
        api.post("/api/companies", mapOf("identity" to "players-co", "type" to "CPA", "amount" to "10"), session)
        val company = jdbc.sql("SELECT id FROM company WHERE identity = 'players-co'").query(Long::class.javaObjectType).single()
        jdbc.sql(
            """
            INSERT INTO referral (player_id, company_id, click_id, country, registered_at, first_deposit_at, first_deposit_amount, first_deposit_invoice_id)
            VALUES (11, :c, 'click-11', 'UA', '2026-09-01T10:00:00Z', '2026-09-02T10:00:00Z', 50000000000, 900),
                   (12, :c, NULL, NULL, '2026-09-03T10:00:00Z', NULL, NULL, NULL)
            """,
        ).param("c", company).update()
        jdbc.sql(
            """
            INSERT INTO activity_daily (player_id, day, company_id, deposit_count, deposit_amount, turnover_amount)
            VALUES (11, '2026-09-02', :c, 1, 50000000000, 120500000000), (11, '2026-09-05', :c, 2, 30000000000, 0)
            """,
        ).param("c", company).update()
        jdbc.sql(
            """
            INSERT INTO commission (account_id, company_id, player_id, type, amount, source, day)
            VALUES (:a, :c, 11, 'CPA', 10000000000, 'cpa:11', '2026-09-02')
            """,
        ).param("a", accountId(login)).param("c", company).update()

        val page = api.get("/api/companies/players-co/players?page=0&size=1", session)

        assertThat(page.status).isEqualTo(200)
        assertThat(page.body["total"].asLong()).isEqualTo(2)
        assertThat(page.body["size"].asInt()).isEqualTo(1)
        assertThat(page.body["items"].values().map { it["playerId"].asLong() }).containsExactly(12)
        val older = api.get("/api/companies/players-co/players?page=1&size=1", session).body["items"][0]
        assertThat(older["playerId"].asLong()).isEqualTo(11)
        assertThat(older["clickId"].asString()).isEqualTo("click-11")
        assertThat(older["country"].asString()).isEqualTo("UA")
        assertThat(older["firstDepositAmount"].asString()).isEqualTo("50")
        assertThat(older["depositCount"].asLong()).isEqualTo(3)
        assertThat(older["depositAmount"].asString()).isEqualTo("80")
        assertThat(older["turnover"].asString()).isEqualTo("120.5")
        assertThat(older["earned"].asString()).isEqualTo("10")
        assertThat(older["registeredAt"].asString()).isEqualTo("2026-09-01T10:00:00Z")

        val badPage = api.get("/api/companies/players-co/players?size=500", session)
        assertThat(badPage.field to badPage.rule).isEqualTo("size" to "range")
        val notANumber = api.get("/api/companies/players-co/players?page=first", session)
        assertThat(notANumber.field to notANumber.rule).isEqualTo("page" to "type")
    }
}
