package io.github.nekzabirov.affiliate.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.atomic.AtomicInteger

/**
 * The whole app on a random port against a real PostgreSQL, migrated by Flyway exactly as the `schema`
 * launcher does it. Every class shares one Spring context; tables are emptied before each test. The broker
 * listener stays off: these tests drive the app through HTTP and the event handlers directly; only a test that
 * brings a broker of its own (EventFlowTest) turns it on, in a context of its own.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.flyway.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.server.port=0",
        "affiliate.site-url=https://example.com",
        // The tests' clients tell the app their address in this header (Api), so rate limits never leak between tests.
        "affiliate.client-ip-header=CF-Connecting-IP",
        "affiliate.promo-manifest-url=https://promo.example.com/library/manifest.json",
    ],
)
@Import(TestBeans::class)
abstract class IntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    protected lateinit var jdbc: JdbcClient

    @Autowired
    private lateinit var json: JsonMapper

    /** A client with an address of its own, so rate limits never leak between tests. */
    protected lateinit var api: Api

    @BeforeEach
    fun cleanSlate() {
        jdbc.sql(
            "TRUNCATE account, session, company, postback, referral, commission, activity_daily, processed_fact, payout " +
                "RESTART IDENTITY CASCADE",
        ).update()
        api = newClient()
    }

    protected fun newClient(): Api {
        val n = addresses.incrementAndGet()
        return Api(port, json, "198.51.${n / OCTET}.${n % OCTET}")
    }

    /** A login nobody else in this run uses: the per-login failure counter outlives a test. */
    protected fun uniqueLogin(prefix: String): String = "$prefix${logins.incrementAndGet()}"

    protected fun makeAdmin(login: String) {
        jdbc.sql("UPDATE account SET role = 'ADMIN' WHERE login = :login").param("login", login).update()
    }

    protected fun accountId(login: String): Long =
        jdbc.sql("SELECT id FROM account WHERE login = :login").param("login", login).query(Long::class.javaObjectType).single()

    protected fun setBalance(login: String, nano: Long) {
        jdbc.sql("UPDATE account SET balance = :balance WHERE login = :login").param("balance", nano).param("login", login).update()
    }

    protected fun balance(login: String): Long =
        jdbc.sql("SELECT balance FROM account WHERE login = :login").param("login", login).query(Long::class.javaObjectType).single()

    protected fun companyId(identity: String): Long =
        jdbc.sql("SELECT id FROM company WHERE identity = :identity").param("identity", identity).query(Long::class.javaObjectType).single()

    /** A company written directly with its final terms and [status], as an admin's review would have left it. */
    protected fun insertCompany(identity: String, type: String, amount: String, owner: Long, status: String = "ACTIVE"): Long =
        jdbc.sql(
            """
            INSERT INTO company (identity, account_id, type, amount, status)
            VALUES (:identity, :owner, :type, CAST(:amount AS NUMERIC), :status) RETURNING id
            """,
        )
            .param("identity", identity)
            .param("owner", owner)
            .param("type", type)
            .param("amount", amount)
            .param("status", status)
            .query(Long::class.javaObjectType)
            .single()

    /** A postback row written directly, past the URL check, as if the name had resolved elsewhere when it was saved. */
    protected fun insertPostback(companyId: Long, url: String, active: Boolean = true): Long =
        jdbc.sql("INSERT INTO postback (company_id, type, url, active) VALUES (:company, 'KEITARO', :url, :active) RETURNING id")
            .param("company", companyId)
            .param("url", url)
            .param("active", active)
            .query(Long::class.javaObjectType)
            .single()

    protected fun attempted(postbackId: Long): Boolean =
        jdbc.sql("SELECT last_sent_at IS NOT NULL FROM postback WHERE id = :id")
            .param("id", postbackId)
            .query(Boolean::class.javaObjectType)
            .single()

    /** Delivery is asynchronous by design: waits for the row to show the attempt, then answers (status, error). */
    protected fun awaitAttempt(postbackId: Long): Pair<Int?, String?> {
        val deadline = System.nanoTime() + ATTEMPT_WAIT_NANOS
        while (System.nanoTime() < deadline) {
            if (attempted(postbackId)) {
                return jdbc.sql("SELECT last_status, last_error FROM postback WHERE id = :id")
                    .param("id", postbackId)
                    .query { rs, _ -> (rs.getObject("last_status") as Int?) to rs.getString("last_error") }
                    .single()
            }
            Thread.sleep(ATTEMPT_POLL_MILLIS)
        }
        error("postback $postbackId was never attempted")
    }

    companion object {

        const val NANO = 1_000_000_000L

        private const val OCTET = 250

        private const val ATTEMPT_WAIT_NANOS = 15_000_000_000L

        private const val ATTEMPT_POLL_MILLIS = 50L

        private val addresses = AtomicInteger()

        private val logins = AtomicInteger()

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { TestPostgres.jdbcUrl }
            registry.add("spring.datasource.username") { TestPostgres.username }
            registry.add("spring.datasource.password") { TestPostgres.password }
        }
    }
}
