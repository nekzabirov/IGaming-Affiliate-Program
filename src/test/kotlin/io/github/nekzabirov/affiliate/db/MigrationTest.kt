package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.support.TestPostgres
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/** V1 on databases of their own, the way the `schema` launcher meets an empty one in a new environment. */
class MigrationTest {

    private lateinit var url: String

    @Test
    fun `V1 applies to an empty database and creates every table`() {
        url = TestPostgres.emptyDatabase("affiliate_migration")
        val result = Flyway.configure()
            .dataSource(url, TestPostgres.username, TestPostgres.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()

        assertThat(result.success).isTrue()
        assertThat(result.migrationsExecuted).isEqualTo(1)
        val tables = sql { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname = 'public'").use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) else null }.toSet()
                }
            }
        }
        assertThat(tables).contains(
            "account", "session", "company", "postback", "referral", "commission", "activity_daily", "processed_fact", "payout",
        )
    }

    @Test
    fun `the checks refuse rows the app would never write`() {
        url = TestPostgres.emptyDatabase("affiliate_checks")
        Flyway.configure().dataSource(url, TestPostgres.username, TestPostgres.password).load().migrate()
        execute("INSERT INTO account (login, password_hash) VALUES ('owner', 'x')")

        listOf(
            "INSERT INTO account (login, password_hash, role) VALUES ('root', 'x', 'ROOT')",
            "INSERT INTO company (identity, account_id, type, amount) VALUES ('share', 1, 'REVSHARE_DEPOSIT', 100.5)",
            "INSERT INTO company (identity, account_id, type, amount) VALUES ('minus', 1, 'CPA', -1)",
            "INSERT INTO company (identity, account_id, type, amount) VALUES ('hybrid', 1, 'HYBRID', 1)",
            "INSERT INTO payout (account_id, amount, currency, network, address) VALUES (1, 0, 'USDT', 'TRC20', 'T')",
            "INSERT INTO payout (account_id, amount, currency, network, address, status) VALUES (1, 1, 'USDT', 'TRC20', 'T', 'LOST')",
        ).forEach { statement ->
            assertThatThrownBy { execute(statement) }
                .isInstanceOf(SQLException::class.java)
                .extracting { (it as SQLException).sqlState }
                .isEqualTo(CHECK_VIOLATION)
        }

        // A CPA amount is money, not a percent: above 100 is fine.
        execute("INSERT INTO company (identity, account_id, type, amount) VALUES ('big-cpa', 1, 'CPA', 250)")
        execute("INSERT INTO company (identity, account_id, type, amount) VALUES ('full-share', 1, 'REVSHARE_TURNOVER', 100)")
    }

    private fun execute(statement: String) {
        sql { connection -> connection.createStatement().use { it.executeUpdate(statement) } }
    }

    private fun <T> sql(block: (Connection) -> T): T =
        DriverManager.getConnection(url, TestPostgres.username, TestPostgres.password).use(block)

    private companion object {

        const val CHECK_VIOLATION = "23514"
    }
}
