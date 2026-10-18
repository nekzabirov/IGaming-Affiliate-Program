package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.events.BetEventHandler
import io.github.nekzabirov.affiliate.events.RegisterEventHandler
import io.github.nekzabirov.affiliate.support.Facts
import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** The daily clean-up against the real database: what goes, in batches, and what must stay. */
class MaintenanceTest : IntegrationTest() {

    @Autowired
    private lateinit var maintenance: Maintenance

    @Autowired
    private lateinit var registrations: RegisterEventHandler

    @Autowired
    private lateinit var bets: BetEventHandler

    @Test
    fun `spins past the replay window go in batches, while sportsbook facts, registrations, invoices and recent spins stay`() {
        jdbc.sql(
            """
            INSERT INTO processed_fact (key, day, created_at)
            SELECT 'spin:' || n, current_date, now() - interval '20 days' FROM generate_series(1, 12001) AS n
            """,
        ).update()
        jdbc.sql(
            """
            INSERT INTO processed_fact (key, day, created_at) VALUES
              ('spin:old', current_date, now() - interval '15 days 1 minute'),
              ('spin:recent', current_date, now() - interval '14 days 23 hours'),
              ('bet:1', current_date, now() - interval '400 days'),
              ('bet:recent', current_date, now()),
              ('register:1', current_date, now() - interval '400 days'),
              ('invoice:1', current_date, now() - interval '400 days')
            """,
        ).update()

        maintenance.daily()

        // A bet may be voided weeks later: its rollback must still find what it added.
        val left = jdbc.sql("SELECT key FROM processed_fact ORDER BY key").query(String::class.java).list()
        assertThat(left).containsExactly("bet:1", "bet:recent", "invoice:1", "register:1", "spin:recent")
    }

    @Test
    fun `a bet voided weeks after it was placed is still taken back`() {
        val login = uniqueLogin("vera")
        api.register(login)
        insertCompany("sport", "REVSHARE_TURNOVER", "5", accountId(login))
        registrations.handle(Facts.register(4001, "sport").toByteArray())
        // 400 UAH is 10 USDT at the fake rate, and 5% of it is the partner's.
        bets.handle(Facts.bet(id = 901, playerId = 4001, type = "BET", realAmount = 400 * NANO).toByteArray())
        jdbc.sql("UPDATE processed_fact SET created_at = now() - interval '40 days' WHERE key = 'bet:901'").update()
        assertThat(balance(login)).isEqualTo(NANO / 2)

        maintenance.daily()
        bets.handle(Facts.bet(id = 902, playerId = 4001, type = "ROLLBACK", realAmount = 400 * NANO, reference = Facts.betLeg(901, 4001, "BET", 400 * NANO)).toByteArray())

        assertThat(balance(login)).isZero()
        assertThat(jdbc.sql("SELECT sum(turnover_amount) FROM activity_daily WHERE player_id = 4001").query(Long::class.javaObjectType).single()).isZero()
    }

    @Test
    fun `expired sessions go, live ones stay`() {
        val login = uniqueLogin("maya")
        val live = api.register(login)
        jdbc.sql("INSERT INTO session (token_hash, account_id, expires_at) VALUES ('expired', :id, now() - interval '1 second')")
            .param("id", accountId(login))
            .update()

        maintenance.daily()

        val left = jdbc.sql("SELECT token_hash FROM session").query(String::class.java).list()
        assertThat(left).hasSize(1).doesNotContain("expired")
        assertThat(api.get("/api/me", live).status).isEqualTo(200)
    }
}
