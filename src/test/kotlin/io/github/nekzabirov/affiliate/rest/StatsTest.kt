package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode

/**
 * The analytics over rows the event handlers would have written, seeded with SQL: two companies of the partner,
 * one of somebody else's that must never show, and facts on both sides of the range and of UTC midnight.
 */
class StatsTest : IntegrationTest() {

    private lateinit var session: String

    private var cpa = 0L

    private var turnover = 0L

    @BeforeEach
    fun seed() {
        val login = uniqueLogin("vera")
        session = api.register(login)
        api.post("/api/companies", mapOf("identity" to "alpha", "type" to "CPA", "amount" to "25"), session)
        api.post("/api/companies", mapOf("identity" to "beta", "type" to "REVSHARE_TURNOVER", "amount" to "3"), session)
        val strangerLogin = uniqueLogin("walt")
        val stranger = newClient().register(strangerLogin)
        api.post("/api/companies", mapOf("identity" to "gamma", "type" to "CPA", "amount" to "5"), stranger)
        cpa = companyId("alpha")
        turnover = companyId("beta")
        val other = companyId("gamma")
        val partnerId = accountId(login)
        val strangerId = accountId(strangerLogin)

        jdbc.sql(
            """
            INSERT INTO referral (player_id, company_id, click_id, country, registered_at, first_deposit_at, first_deposit_amount, first_deposit_invoice_id) VALUES
              (1, :a, 'c1', 'UA', '2026-09-10T10:00:00Z', '2026-09-11T05:00:00Z', 50000000000, 101),
              (2, :a, NULL, 'UA', '2026-09-10T23:59:59Z', NULL, NULL, NULL),
              (3, :b, 'c3', 'PL', '2026-09-12T00:00:00Z', '2026-09-12T01:00:00Z', 20000000000, 103),
              (4, :g, 'c4', 'DE', '2026-09-10T12:00:00Z', '2026-09-10T13:00:00Z', 70000000000, 104),
              (5, :a, NULL, NULL, '2026-08-01T00:00:00Z', '2026-09-11T08:00:00Z', 10000000000, 105),
              (6, :a, NULL, 'UA', '2026-09-13T00:00:00Z', NULL, NULL, NULL)
            """,
        ).param("a", cpa).param("b", turnover).param("g", other).update()
        jdbc.sql(
            """
            INSERT INTO activity_daily (player_id, day, company_id, deposit_count, deposit_amount, turnover_amount) VALUES
              (1, '2026-09-11', :a, 1, 50000000000, 0),
              (1, '2026-09-12', :a, 2, 30000000000, 100000000000),
              (3, '2026-09-12', :b, 1, 20000000000, 500000000000),
              (4, '2026-09-10', :g, 1, 70000000000, 900000000000),
              (1, '2026-09-13', :a, 1, 99000000000, 0)
            """,
        ).param("a", cpa).param("b", turnover).param("g", other).update()
        jdbc.sql(
            """
            INSERT INTO commission (account_id, company_id, player_id, type, amount, source, day) VALUES
              (:p, :a, 1, 'CPA', 25000000000, 'cpa:1', '2026-09-11'),
              (:p, :b, 3, 'REVSHARE_TURNOVER', 15000000000, 'turnover:b:3:2026-09-12', '2026-09-12'),
              (:p, :a, 5, 'CPA', 25000000000, 'cpa:5', '2026-09-09'),
              (:s, :g, 4, 'CPA', 5000000000, 'cpa:4', '2026-09-10')
            """,
        ).param("p", partnerId).param("s", strangerId).param("a", cpa).param("b", turnover).param("g", other).update()
    }

    @Test
    fun `totals, every day of the range, the companies and the countries`() {
        val stats = api.get("/api/stats?from=2026-09-10&to=2026-09-12", session)

        assertThat(stats.status).isEqualTo(200)
        assertThat(stats.body["from"].asString()).isEqualTo("2026-09-10")
        assertThat(stats.body["to"].asString()).isEqualTo("2026-09-12")
        val totals = stats.body["totals"]
        assertThat(totals["registrations"].asLong()).isEqualTo(3)
        // Player 5 registered before the range and made the first deposit inside it.
        assertThat(totals["firstDeposits"].asLong()).isEqualTo(3)
        assertThat(totals["conversion"].asString()).isEqualTo("100")
        assertThat(totals["depositCount"].asLong()).isEqualTo(4)
        assertThat(totals["depositAmount"].asString()).isEqualTo("100")
        assertThat(totals["turnover"].asString()).isEqualTo("600")
        assertThat(totals["earned"].asString()).isEqualTo("40")
        assertThat(texts(totals["earnedByType"])).isEqualTo(mapOf("CPA" to "25", "REVSHARE_DEPOSIT" to "0", "REVSHARE_TURNOVER" to "15"))

        assertThat(stats.body["daily"].values().map { day(it) }).containsExactly(
            listOf("2026-09-10", "2", "0", "0", "0", "0"),
            listOf("2026-09-11", "0", "2", "50", "0", "25"),
            listOf("2026-09-12", "1", "1", "50", "600", "15"),
        )
        assertThat(stats.body["companies"].values().map { company(it) }).containsExactly(
            listOf("alpha", "CPA", "IN_REVIEW", "2", "2", "80", "100", "25"),
            listOf("beta", "REVSHARE_TURNOVER", "IN_REVIEW", "1", "1", "20", "500", "15"),
        )
        assertThat(stats.body["countries"].values().map { listOf(country(it), it["registrations"].asString(), it["firstDeposits"].asString()) })
            .containsExactly(listOf("UA", "2", "1"), listOf("PL", "1", "1"), listOf(null, "0", "1"))
    }

    @Test
    fun `one company narrows everything to it`() {
        val stats = api.get("/api/stats?from=2026-09-10&to=2026-09-12&company=beta", session)

        assertThat(stats.body["totals"]["registrations"].asLong()).isEqualTo(1)
        assertThat(stats.body["totals"]["firstDeposits"].asLong()).isEqualTo(1)
        assertThat(stats.body["totals"]["earned"].asString()).isEqualTo("15")
        assertThat(stats.body["companies"].values().map { it["identity"].asString() }).containsExactly("beta")
        assertThat(stats.body["countries"].values().map(::country)).containsExactly("PL")
    }

    @Test
    fun `the default range is the last 30 days with today, and the range is checked`() {
        val stats = api.get("/api/stats", session)

        assertThat(stats.status).isEqualTo(200)
        assertThat(stats.body["daily"].size()).isEqualTo(30)
        assertThat(stats.body["daily"][29]["day"].asString()).isEqualTo(stats.body["to"].asString())

        val reversed = api.get("/api/stats?from=2026-09-12&to=2026-09-10", session)
        assertThat(reversed.field to reversed.rule).isEqualTo("from" to "range")
        val tooLong = api.get("/api/stats?from=2025-01-01&to=2026-01-02", session)
        assertThat(tooLong.field to tooLong.rule).isEqualTo("from" to "range")
        assertThat(api.get("/api/stats?from=2025-01-01&to=2026-01-01", session).status).isEqualTo(200)
        val malformed = api.get("/api/stats?from=10.09.2026", session)
        assertThat(malformed.field to malformed.rule).isEqualTo("from" to "format")
    }

    @Test
    fun `the ledger lists earnings newest day first, the company list and me total them`() {
        val ledger = api.get("/api/commissions", session)

        assertThat(ledger.body["total"].asLong()).isEqualTo(3)
        assertThat(ledger.body["items"].values().map { it["day"].asString() to it["amount"].asString() })
            .containsExactly("2026-09-12" to "15", "2026-09-11" to "25", "2026-09-09" to "25")
        assertThat(ledger.body["items"][0]["company"].asString()).isEqualTo("beta")
        assertThat(ledger.body["items"][0]["type"].asString()).isEqualTo("REVSHARE_TURNOVER")
        assertThat(api.get("/api/commissions?company=alpha", session).body["total"].asLong()).isEqualTo(2)

        val companies = api.get("/api/companies", session).body.associate { it["identity"].asString() to it["stats"] }
        assertThat(companies.getValue("alpha")["registrations"].asLong()).isEqualTo(4)
        assertThat(companies.getValue("alpha")["firstDeposits"].asLong()).isEqualTo(2)
        assertThat(companies.getValue("alpha")["earned"].asString()).isEqualTo("50")
        assertThat(api.get("/api/me", session).body["totalEarned"].asString()).isEqualTo("65")
    }

    private fun country(node: JsonNode): String? = node["country"].takeUnless { it.isNull }?.asString()

    private fun texts(node: JsonNode): Map<String, String> = node.properties().associate { it.key to it.value.asString() }

    private fun day(node: JsonNode) = listOf("day", "registrations", "firstDeposits", "depositAmount", "turnover", "earned")
        .map { node[it].asString() }

    private fun company(node: JsonNode) =
        listOf("identity", "type", "status", "registrations", "firstDeposits", "depositAmount", "turnover", "earned")
            .map { node[it].asString() }
}
