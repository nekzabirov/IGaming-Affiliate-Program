package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.clients.CurrencyClient
import io.github.nekzabirov.affiliate.clients.CurrencyConversionException
import io.github.nekzabirov.affiliate.events.BetEventHandler
import io.github.nekzabirov.affiliate.events.InvoiceEventHandler
import io.github.nekzabirov.affiliate.events.MalformedEventException
import io.github.nekzabirov.affiliate.events.RegisterEventHandler
import io.github.nekzabirov.affiliate.events.SpinEventHandler
import io.github.nekzabirov.affiliate.postback.KeitaroAdapter
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import io.github.nekzabirov.affiliate.support.Facts
import io.github.nekzabirov.affiliate.support.FakeCurrencyManager
import io.github.nekzabirov.affiliate.support.FakeDns
import io.github.nekzabirov.affiliate.support.IntegrationTest
import io.github.nekzabirov.affiliate.support.RecordingDispatcher
import io.grpc.Status
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Every rule of what facts earn, driven through the route handlers with the documented payloads, against the
 * real database. The currency service is the in-process fake: 40 UAH to one USDT, the partner currency.
 */
class AccrualTest : IntegrationTest() {

    @Autowired
    private lateinit var registrations: RegisterEventHandler

    @Autowired
    private lateinit var invoices: InvoiceEventHandler

    @Autowired
    private lateinit var spins: SpinEventHandler

    @Autowired
    private lateinit var bets: BetEventHandler

    @Autowired
    private lateinit var postbacks: RecordingDispatcher

    @Autowired
    private lateinit var currency: FakeCurrencyManager

    @Autowired
    private lateinit var currencyClient: CurrencyClient

    @Autowired
    private lateinit var keitaro: KeitaroAdapter

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var login: String

    private var partner = 0L

    @BeforeEach
    fun partner() {
        postbacks.dispatched.clear()
        currency.reset()
        login = uniqueLogin("paula")
        api.register(login)
        partner = accountId(login)
    }

    @Test
    fun `a partner's tag attributes the player to the active company, keeps the click id and sends a lead after the commit`() {
        val company = company("summer", "CPA", "25")

        register(Facts.register(1001, "partner:summer", mapOf("click_id" to "8f3k2a", "fbc" to "fb.1.17593.IwAR"), country = "ua"))

        val referral = referral(1001)
        assertThat(referral.companyId).isEqualTo(company)
        assertThat(referral.clickId).isEqualTo("8f3k2a")
        assertThat(referral.country).isEqualTo("UA")
        assertThat(referral.registeredAt).isEqualTo(Instant.parse(Facts.AT))
        assertThat(referral.firstDepositAt).isNull()
        assertThat(referral.attribution).contains("\"click_id\": \"8f3k2a\"").contains("\"fbc\": \"fb.1.17593.IwAR\"")
        assertThat(facts()).containsExactly("register:1001")
        val sent = postbacks.dispatched.single()
        assertThat(sent.companyId).isEqualTo(company)
        assertThat(sent.insideTransaction).isFalse()
        assertThat(sent.event.kind).isEqualTo(PostbackEvent.Kind.REGISTRATION)
        assertThat(sent.event.playerId).isEqualTo(1001)
        assertThat(sent.event.clickId).isEqualTo("8f3k2a")
        assertThat(sent.event.factId).isNull()
        assertThat(sent.event.amount).isZero()
    }

    @Test
    fun `the prefix is read in any letter case and the identity in lower case`() {
        val company = company("summer", "CPA", "25")

        register(Facts.register(1002, "PARTNER:Summer"))

        assertThat(referral(1002).companyId).isEqualTo(company)
    }

    @Test
    fun `other programmes' tags, unknown companies, inactive companies and blocked partners attribute nobody`() {
        company("review", "CPA", "25", status = "IN_REVIEW")
        company("off", "CPA", "25", status = "DISABLED")
        val blockedLogin = uniqueLogin("bruno")
        newClient().register(blockedLogin)
        val blocked = accountId(blockedLogin)
        company("blocked", "CPA", "25", owner = blocked)
        jdbc.sql("UPDATE account SET active = false WHERE id = :id").param("id", blocked).update()

        listOf(null, "referal:5", "summer", "partner:", "partner:nobody", "partner:review", "partner:off", "partner:blocked")
            .forEachIndexed { index, tag -> register(Facts.register(2000L + index, tag)) }

        assertThat(count("referral")).isZero()
        // A partner tag is refused for good, so no later fact attributes the player; other programmes' tags leave nothing.
        assertThat(facts()).containsExactlyInAnyOrder("register:2003", "register:2004", "register:2005", "register:2006", "register:2007")
        assertThat(postbacks.dispatched).isEmpty()
    }

    @Test
    fun `a click id no tracker would issue is dropped, and the player is attributed all the same`() {
        company("summer", "CPA", "25")

        register(Facts.register(1003, "partner:summer", mapOf("click_id" to "not a click id")))

        assertThat(referral(1003).clickId).isNull()
        assertThat(postbacks.dispatched.single().event.clickId).isNull()
    }

    @Test
    fun `a registration delivered twice attributes once and tells the trackers once`() {
        company("summer", "CPA", "25")
        val fact = Facts.register(1004, "partner:summer", mapOf("click_id" to "abc"))

        register(fact)
        register(fact)

        assertThat(count("referral")).isEqualTo(1)
        assertThat(postbacks.dispatched).hasSize(1)
    }

    @Test
    fun `the first deposit at the CPA minimum pays the CPA once, and later deposits count without paying more`() {
        val company = company("summer", "CPA", "25")
        register(Facts.register(1010, "partner:summer", mapOf("click_id" to "c1")))
        postbacks.dispatched.clear()

        // 400 UAH is 10 USDT: exactly CPA_MIN_DEPOSIT.
        invoice(Facts.invoice(id = 501, playerId = 1010, amount = uah("400"), updatedAt = "2026-09-18T08:00:00Z"))
        invoice(Facts.invoice(id = 502, playerId = 1010, amount = uah("800"), updatedAt = "2026-09-18T09:00:00Z"))
        invoice(Facts.invoice(id = 503, playerId = 1010, amount = uah("4000"), updatedAt = "2026-09-19T09:00:00Z"))

        val referral = referral(1010)
        assertThat(referral.firstDepositInvoiceId).isEqualTo(501)
        assertThat(referral.firstDepositAmount).isEqualTo(usdt("10"))
        assertThat(referral.firstDepositAt).isEqualTo(Instant.parse("2026-09-18T08:00:00Z"))
        assertThat(activity(1010)).containsExactly(
            Activity("2026-09-18", 2, usdt("30"), 0),
            Activity("2026-09-19", 1, usdt("100"), 0),
        )
        assertThat(commissions()).containsExactly(Commission("cpa:1010", "CPA", usdt("25"), "2026-09-18", company))
        assertThat(balance(login)).isEqualTo(usdt("25"))
        // The trackers hear of every deposit with its whole amount as it was made, never the commission.
        assertThat(postbacks.dispatched.map { Triple(it.event.kind, it.event.amount, it.event.currency) }).containsExactly(
            Triple(PostbackEvent.Kind.DEPOSIT, uah("400"), "UAH"),
            Triple(PostbackEvent.Kind.DEPOSIT, uah("800"), "UAH"),
            Triple(PostbackEvent.Kind.DEPOSIT, uah("4000"), "UAH"),
        )
        assertThat(postbacks.dispatched.map { it.event.factId }).containsExactly(501L, 502L, 503L)
        assertThat(postbacks.dispatched).allMatch { it.event.clickId == "c1" && !it.insideTransaction }
    }

    @Test
    fun `a first deposit below the CPA minimum is recorded and never pays, not even after a bigger second one`() {
        company("summer", "CPA", "25")
        register(Facts.register(1011, "partner:summer"))
        postbacks.dispatched.clear()

        // 399.96 UAH is 9.999 USDT, a tenth of a cent short.
        invoice(Facts.invoice(id = 511, playerId = 1011, amount = uah("399.96")))
        invoice(Facts.invoice(id = 512, playerId = 1011, amount = uah("4000")))

        assertThat(referral(1011).firstDepositInvoiceId).isEqualTo(511)
        assertThat(referral(1011).firstDepositAmount).isEqualTo(usdt("9.999"))
        assertThat(commissions()).isEmpty()
        assertThat(balance(login)).isZero()
        assertThat(postbacks.dispatched.map { it.event.kind to it.event.amount }).containsExactly(
            PostbackEvent.Kind.DEPOSIT to uah("399.96"),
            PostbackEvent.Kind.DEPOSIT to uah("4000"),
        )
    }

    @Test
    fun `REVSHARE_DEPOSIT pays its percent of every deposit, rounded half up to the nano`() {
        val company = company("share", "REVSHARE_DEPOSIT", "12.5")
        register(Facts.register(1012, "partner:share"))
        postbacks.dispatched.clear()

        invoice(Facts.invoice(id = 521, playerId = 1012, amount = uah("400")))
        // 160 nano UAH is 4 nano USDT, and 12.5% of that is half a nano: rounded up.
        invoice(Facts.invoice(id = 522, playerId = 1012, amount = 160))
        invoice(Facts.invoice(id = 523, playerId = 1012, amount = 5_000_000_000_000L, currency = "EUR"))

        assertThat(commissions()).containsExactly(
            Commission("deposit:521", "REVSHARE_DEPOSIT", usdt("1.25"), Facts.DAY, company),
            Commission("deposit:522", "REVSHARE_DEPOSIT", 1, Facts.DAY, company),
            // 5000 EUR is 10000 USDT.
            Commission("deposit:523", "REVSHARE_DEPOSIT", usdt("1250"), Facts.DAY, company),
        )
        assertThat(balance(login)).isEqualTo(usdt("1251.25") + 1)
        assertThat(postbacks.dispatched.map { it.event.amount to it.event.currency }).containsExactly(
            uah("400") to "UAH",
            160L to "UAH",
            5_000_000_000_000L to "EUR",
        )
    }

    @Test
    fun `an invoice delivered twice counts once`() {
        company("summer", "CPA", "25")
        register(Facts.register(1013, "partner:summer"))
        postbacks.dispatched.clear()
        val fact = Facts.invoice(id = 531, playerId = 1013, amount = uah("400"))

        invoice(fact)
        invoice(fact)

        assertThat(activity(1013)).containsExactly(Activity(Facts.DAY, 1, usdt("10"), 0))
        assertThat(commissions()).hasSize(1)
        assertThat(balance(login)).isEqualTo(usdt("25"))
        assertThat(postbacks.dispatched).hasSize(1)
    }

    @Test
    fun `only a landed deposit of a referral counts`() {
        company("summer", "REVSHARE_DEPOSIT", "10")
        register(Facts.register(1014, "partner:summer"))
        postbacks.dispatched.clear()

        invoice(Facts.invoice(id = 541, playerId = 9999, amount = uah("400")))
        invoice(Facts.invoice(id = 542, playerId = 1014, amount = uah("400"), status = "PENDING"))
        invoice(Facts.invoice(id = 543, playerId = 1014, amount = uah("400"), status = "PROCESSING"))
        invoice(Facts.invoice(id = 544, playerId = 1014, amount = uah("400"), status = "REJECTED"))
        invoice(Facts.invoice(id = 545, playerId = 1014, amount = uah("400"), type = "PAYOUT"))
        invoice(Facts.invoice(id = 546, playerId = 1014, amount = uah("400"), type = "REFUND"))
        invoice(Facts.invoice(id = 547, playerId = 1014, amount = 0))

        assertThat(referral(1014).firstDepositAt).isNull()
        assertThat(count("activity_daily")).isZero()
        assertThat(commissions()).isEmpty()
        assertThat(facts()).containsExactly("register:1014")
        assertThat(postbacks.dispatched).isEmpty()
        assertThat(currency.calls.get()).isZero()
    }

    @Test
    fun `values a publisher adds later are acknowledged without effect, exactly like the ones the app does not act on`() {
        val company = company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1016, "partner:spins"))
        spin(Facts.spin(id = 631, playerId = 1016, type = "PLACE", realAmount = uah("200")))
        bet(Facts.bet(id = 731, playerId = 1016, type = "BET", realAmount = uah("400")))
        postbacks.dispatched.clear()
        currency.reset()

        invoice(Facts.invoice(id = 548, playerId = 1016, amount = uah("400"), type = "CHARGEBACK"))
        invoice(Facts.invoice(id = 549, playerId = 1016, amount = uah("400"), status = "EXPIRED"))
        spin(Facts.spin(id = 632, playerId = 1016, type = "JACKPOT", realAmount = uah("1000")))
        bet(Facts.bet(id = 732, playerId = 1016, type = "CASHOUT", realAmount = uah("1000")))
        // A rollback of a leg whose type the app does not know is a rollback of no stake, like one of a SETTLE or a WIN.
        spin(Facts.spin(id = 633, playerId = 1016, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(631, 1016, "FREEROUND", uah("200"))))
        bet(Facts.bet(id = 733, playerId = 1016, type = "ROLLBACK", realAmount = uah("400"), reference = Facts.betLeg(731, 1016, "VOID", uah("400"))))

        assertThat(referral(1016).firstDepositAt).isNull()
        assertThat(activity(1016)).containsExactly(Activity(Facts.DAY, 0, 0, usdt("15")))
        assertThat(commissions()).containsExactly(
            Commission("turnover:$company:1016:${Facts.DAY}", "REVSHARE_TURNOVER", usdt("0.75"), Facts.DAY, company),
        )
        assertThat(balance(login)).isEqualTo(usdt("0.75"))
        assertThat(facts()).containsExactlyInAnyOrder("register:1016", "spin:631", "bet:731")
        assertThat(postbacks.dispatched).isEmpty()
        assertThat(currency.calls.get()).isZero()
    }

    @Test
    fun `a referral's deposits count only while the company is active and its partner is not blocked`() {
        val company = company("summer", "CPA", "25")
        register(Facts.register(1015, "partner:summer"))
        postbacks.dispatched.clear()
        val deposit = Facts.invoice(id = 551, playerId = 1015, amount = uah("400"))

        setStatus(company, "DISABLED")
        invoice(deposit)
        setStatus(company, "ACTIVE")
        jdbc.sql("UPDATE account SET active = false WHERE id = :id").param("id", partner).update()
        invoice(deposit)

        assertThat(referral(1015).firstDepositAt).isNull()
        assertThat(count("activity_daily")).isZero()
        assertThat(facts()).containsExactly("register:1015")
        assertThat(postbacks.dispatched).isEmpty()

        // Nothing was recorded for it, so the same fact counts once the partner is unblocked.
        jdbc.sql("UPDATE account SET active = true WHERE id = :id").param("id", partner).update()
        invoice(deposit)
        assertThat(referral(1015).firstDepositInvoiceId).isEqualTo(551)
        assertThat(balance(login)).isEqualTo(usdt("25"))
    }

    @Test
    fun `real-money stakes add turnover and the turnover share, while lock, bonus, SETTLE and WIN never earn`() {
        val company = company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1020, "partner:spins"))
        postbacks.dispatched.clear()

        // 200 UAH of own money is 5 USDT; the lock and bonus parts of the same stake are not the player's.
        spin(Facts.spin(id = 601, playerId = 1020, type = "PLACE", realAmount = uah("200"), lockAmount = uah("50"), bonusAmount = uah("30")))
        spin(Facts.spin(id = 602, playerId = 1020, type = "PLACE", realAmount = 0, lockAmount = uah("100")))
        spin(Facts.spin(id = 603, playerId = 1020, type = "PLACE", realAmount = 0, bonusAmount = uah("100")))
        spin(Facts.spin(id = 604, playerId = 1020, type = "SETTLE", realAmount = uah("1000"), reference = Facts.spinLeg(601, 1020, "PLACE", uah("200"))))
        bet(Facts.bet(id = 701, playerId = 1020, type = "BET", realAmount = uah("400"), lockAmount = uah("40")))
        bet(Facts.bet(id = 702, playerId = 1020, type = "WIN", realAmount = uah("2000")))
        invoice(Facts.invoice(id = 561, playerId = 1020, amount = uah("400")))

        assertThat(activity(1020)).containsExactly(Activity(Facts.DAY, 1, usdt("10"), usdt("15")))
        val source = "turnover:$company:1020:${Facts.DAY}"
        assertThat(commissions()).containsExactly(Commission(source, "REVSHARE_TURNOVER", usdt("0.75"), Facts.DAY, company))
        assertThat(balance(login)).isEqualTo(usdt("0.75"))
        assertThat(facts()).containsExactlyInAnyOrder("register:1020", "spin:601", "spin:602", "spin:603", "bet:701", "invoice:561")
        // The trackers hear of every stake with its whole amount, lock and bonus money included; SETTLE and WIN tell nobody.
        assertThat(postbacks.dispatched.map { Triple(it.event.kind, it.event.factId, it.event.amount) }).containsExactly(
            Triple(PostbackEvent.Kind.SPIN, 601L, uah("280")),
            Triple(PostbackEvent.Kind.SPIN, 602L, uah("100")),
            Triple(PostbackEvent.Kind.SPIN, 603L, uah("100")),
            Triple(PostbackEvent.Kind.BET, 701L, uah("440")),
            Triple(PostbackEvent.Kind.DEPOSIT, 561L, uah("400")),
        )
    }

    @Test
    fun `a rollback takes back exactly what its stake added, whatever the terms and the status are by then`() {
        val company = company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1021, "partner:spins"))
        spin(Facts.spin(id = 611, playerId = 1021, type = "PLACE", realAmount = uah("200")))
        bet(Facts.bet(id = 711, playerId = 1021, type = "BET", realAmount = uah("400")))
        spin(Facts.spin(id = 612, playerId = 1021, type = "PLACE", realAmount = uah("40"), createdAt = "2026-09-18T00:00:01Z"))
        val source = "turnover:$company:1021:${Facts.DAY}"
        assertThat(balance(login)).isEqualTo(usdt("0.80"))

        // New terms and a disabled company must not change what the reversal takes back.
        jdbc.sql("UPDATE company SET amount = 50, status = 'DISABLED' WHERE id = :id").param("id", company).update()
        spin(Facts.spin(id = 613, playerId = 1021, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(611, 1021, "PLACE", uah("200"))))

        assertThat(activity(1021)).containsExactly(
            Activity(Facts.DAY, 0, 0, usdt("10")),
            Activity("2026-09-18", 0, 0, usdt("1")),
        )
        assertThat(commissions()).containsExactly(
            Commission(source, "REVSHARE_TURNOVER", usdt("0.50"), Facts.DAY, company),
            Commission("turnover:$company:1021:2026-09-18", "REVSHARE_TURNOVER", usdt("0.05"), "2026-09-18", company),
        )
        assertThat(balance(login)).isEqualTo(usdt("0.55"))

        bet(Facts.bet(id = 712, playerId = 1021, type = "ROLLBACK", realAmount = uah("400"), reference = Facts.betLeg(711, 1021, "BET", uah("400"))))

        assertThat(activity(1021).first()).isEqualTo(Activity(Facts.DAY, 0, 0, 0))
        assertThat(commissions().first()).isEqualTo(Commission(source, "REVSHARE_TURNOVER", 0, Facts.DAY, company))
        assertThat(balance(login)).isEqualTo(usdt("0.05"))
        assertThat(fact("spin:613")).isEqualTo(-usdt("5") to -usdt("0.25"))
    }

    @Test
    fun `a rollback reverses once, only a stake that was counted, and voids one that was not`() {
        val company = company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1022, "partner:spins"))
        spin(Facts.spin(id = 621, playerId = 1022, type = "PLACE", realAmount = uah("200")))
        bet(Facts.bet(id = 721, playerId = 1022, type = "BET", realAmount = uah("400")))
        val rollback = Facts.spin(id = 622, playerId = 1022, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(621, 1022, "PLACE", uah("200")))

        spin(rollback)
        spin(rollback)
        // A rolled-back win, a stake this app never counted, and a rollback that names no leg.
        spin(Facts.spin(id = 623, playerId = 1022, type = "ROLLBACK", realAmount = uah("90"), reference = Facts.spinLeg(620, 1022, "SETTLE", uah("90"))))
        spin(Facts.spin(id = 624, playerId = 1022, type = "ROLLBACK", realAmount = uah("90"), reference = Facts.spinLeg(555, 1022, "PLACE", uah("90"))))
        spin(Facts.spin(id = 625, playerId = 1022, type = "ROLLBACK", realAmount = uah("90")))
        bet(Facts.bet(id = 722, playerId = 1022, type = "ROLLBACK", realAmount = uah("70"), reference = Facts.betLeg(720, 1022, "WIN", uah("70"))))
        bet(Facts.bet(id = 723, playerId = 1022, type = "ROLLBACK", realAmount = uah("70"), reference = Facts.betLeg(556, 1022, "BET", uah("70"))))

        assertThat(activity(1022)).containsExactly(Activity(Facts.DAY, 0, 0, usdt("10")))
        assertThat(commissions()).containsExactly(Commission("turnover:$company:1022:${Facts.DAY}", "REVSHARE_TURNOVER", usdt("0.5"), Facts.DAY, company))
        assertThat(balance(login)).isEqualTo(usdt("0.5"))
        // The rollbacks of the never-counted PLACE 555 and BET 556 took those stakes' keys, empty.
        assertThat(facts()).containsExactlyInAnyOrder(
            "register:1022", "spin:621", "bet:721", "spin:622", "spin:624", "spin:555", "bet:723", "bet:556",
        )
        assertThat(fact("spin:555")).isEqualTo(0L to 0L)
        assertThat(fact("bet:556")).isEqualTo(0L to 0L)
        assertThat(fact("spin:624")).isEqualTo(0L to 0L)
    }

    @Test
    fun `a rollback handled before its stake keeps that stake from ever counting`() {
        company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1031, "partner:spins"))

        // The stakes wait in the dead queue, or in the other pod's prefetch, while their rollbacks are handled.
        spin(Facts.spin(id = 652, playerId = 1031, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(651, 1031, "PLACE", uah("200"))))
        bet(Facts.bet(id = 752, playerId = 1031, type = "ROLLBACK", realAmount = uah("400"), reference = Facts.betLeg(751, 1031, "BET", uah("400"))))
        spin(Facts.spin(id = 651, playerId = 1031, type = "PLACE", realAmount = uah("200")))
        bet(Facts.bet(id = 751, playerId = 1031, type = "BET", realAmount = uah("400")))

        assertThat(count("activity_daily")).isZero()
        assertThat(commissions()).isEmpty()
        assertThat(balance(login)).isZero()
        assertThat(fact("spin:651")).isEqualTo(0L to 0L)
        assertThat(fact("bet:751")).isEqualTo(0L to 0L)

        // Only those legs are void: the player's next stake counts as usual.
        spin(Facts.spin(id = 653, playerId = 1031, type = "PLACE", realAmount = uah("40")))
        assertThat(activity(1031)).containsExactly(Activity(Facts.DAY, 0, 0, usdt("1")))
        assertThat(balance(login)).isEqualTo(usdt("0.05"))
    }

    @Test
    fun `a rollback racing its stake on another pod waits for the stake's commit and then takes it back`() {
        company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1032, "partner:spins"))
        val otherPod = Executors.newSingleThreadExecutor()
        try {
            val rollback = checkNotNull(
                TransactionTemplate(transactionManager).execute {
                    // The stake's transaction, still open: what the other pod holds until its commit lands.
                    spin(Facts.spin(id = 661, playerId = 1032, type = "PLACE", realAmount = uah("200")))
                    val pending = otherPod.submit(
                        Callable {
                            spin(Facts.spin(id = 662, playerId = 1032, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(661, 1032, "PLACE", uah("200"))))
                        },
                    )
                    awaitLockWait()
                    pending
                },
            )
            rollback.get(WAIT_SECONDS, TimeUnit.SECONDS)
        } finally {
            otherPod.shutdownNow()
        }

        assertThat(activity(1032)).containsExactly(Activity(Facts.DAY, 0, 0, 0))
        assertThat(commissions().single().amount).isZero()
        assertThat(balance(login)).isZero()
        assertThat(fact("spin:661")).isEqualTo(usdt("5") to usdt("0.25"))
        assertThat(fact("spin:662")).isEqualTo(-usdt("5") to -usdt("0.25"))
    }

    @Test
    fun `a first deposit that comes before its registration attributes the player itself, and the registration then changes nothing`() {
        val company = company("summer", "CPA", "25")

        // The registration was dead-lettered (the database was away) and is replayed after the deposit.
        invoice(
            Facts.invoice(
                id = 595,
                playerId = 1040,
                amount = uah("400"),
                playerTag = "partner:summer",
                playerAttribution = mapOf("click_id" to "late1"),
                playerCreatedAt = "2026-09-16T22:00:00Z",
            ),
        )

        val referral = referral(1040)
        assertThat(referral.companyId).isEqualTo(company)
        assertThat(referral.clickId).isEqualTo("late1")
        assertThat(referral.registeredAt).isEqualTo(Instant.parse("2026-09-16T22:00:00Z"))
        assertThat(referral.firstDepositInvoiceId).isEqualTo(595)
        assertThat(commissions()).containsExactly(Commission("cpa:1040", "CPA", usdt("25"), Facts.DAY, company))
        assertThat(balance(login)).isEqualTo(usdt("25"))
        assertThat(postbacks.dispatched.map { it.event.kind to it.event.clickId }).containsExactly(
            PostbackEvent.Kind.REGISTRATION to "late1",
            PostbackEvent.Kind.DEPOSIT to "late1",
        )
        assertThat(postbacks.dispatched).noneMatch { it.insideTransaction }

        register(Facts.register(1040, "partner:summer", mapOf("click_id" to "late1"), createdAt = "2026-09-16T22:00:00Z"))

        assertThat(count("referral")).isEqualTo(1)
        assertThat(facts()).containsExactlyInAnyOrder("register:1040", "invoice:595")
        assertThat(postbacks.dispatched).hasSize(2)
    }

    @Test
    fun `whichever of the registration and the first deposit comes first decides, and a refused tag stays refused`() {
        val refused = company("summer", "CPA", "25", status = "IN_REVIEW")
        val later = company("autumn", "CPA", "25", status = "DISABLED")
        register(Facts.register(1041, "partner:summer"))
        invoice(Facts.invoice(id = 596, playerId = 1042, amount = uah("400"), playerTag = "partner:autumn"))
        setStatus(refused, "ACTIVE")
        setStatus(later, "ACTIVE")

        // An approval afterwards claims neither player: not through a deposit, nor through a registration replayed.
        invoice(Facts.invoice(id = 597, playerId = 1041, amount = uah("400"), playerTag = "partner:summer"))
        register(Facts.register(1042, "partner:autumn"))
        invoice(Facts.invoice(id = 598, playerId = 1042, amount = uah("400"), playerTag = "partner:autumn"))

        assertThat(count("referral")).isZero()
        assertThat(count("activity_daily")).isZero()
        assertThat(commissions()).isEmpty()
        assertThat(postbacks.dispatched).isEmpty()
        assertThat(facts()).containsExactlyInAnyOrder("register:1041", "register:1042")
    }

    @Test
    fun `stakes of CPA and REVSHARE_DEPOSIT companies count turnover and earn nothing, and so do their rollbacks`() {
        company("cpa", "CPA", "25")
        company("share", "REVSHARE_DEPOSIT", "30")
        register(Facts.register(1023, "partner:cpa"))
        register(Facts.register(1024, "partner:share"))

        spin(Facts.spin(id = 631, playerId = 1023, type = "PLACE", realAmount = uah("200")))
        bet(Facts.bet(id = 731, playerId = 1024, type = "BET", realAmount = uah("400")))
        spin(Facts.spin(id = 632, playerId = 1023, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(631, 1023, "PLACE", uah("200"))))

        assertThat(activity(1023)).containsExactly(Activity(Facts.DAY, 0, 0, 0))
        assertThat(activity(1024)).containsExactly(Activity(Facts.DAY, 0, 0, usdt("10")))
        assertThat(commissions()).isEmpty()
        assertThat(balance(login)).isZero()
    }

    @Test
    fun `a stake delivered twice counts once`() {
        company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1025, "partner:spins"))
        val stake = Facts.bet(id = 741, playerId = 1025, type = "BET", realAmount = uah("400"))

        bet(stake)
        bet(stake)

        assertThat(activity(1025)).containsExactly(Activity(Facts.DAY, 0, 0, usdt("10")))
        assertThat(commissions().single().amount).isEqualTo(usdt("0.5"))
        assertThat(balance(login)).isEqualTo(usdt("0.5"))
    }

    @Test
    fun `a reversal may leave the balance negative once the commission was paid out`() {
        company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1026, "partner:spins"))
        spin(Facts.spin(id = 641, playerId = 1026, type = "PLACE", realAmount = uah("200")))
        setBalance(login, 0)

        spin(Facts.spin(id = 642, playerId = 1026, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(641, 1026, "PLACE", uah("200"))))

        assertThat(balance(login)).isEqualTo(-usdt("0.25"))
    }

    @Test
    fun `when the currency service refuses, the fact fails, nothing is written, and a later delivery counts it`() {
        company("summer", "CPA", "25")
        register(Facts.register(1027, "partner:summer"))
        postbacks.dispatched.clear()
        val deposit = Facts.invoice(id = 571, playerId = 1027, amount = uah("400"))
        currency.failNext(Status.FAILED_PRECONDITION, "RATE_MISSING")

        assertThatThrownBy { invoice(deposit) }
            .isInstanceOf(CurrencyConversionException::class.java)
            .hasMessageContaining("RATE_MISSING")
        assertThat(facts()).containsExactly("register:1027")
        assertThat(count("activity_daily")).isZero()
        assertThat(postbacks.dispatched).isEmpty()

        invoice(deposit)
        assertThat(balance(login)).isEqualTo(usdt("25"))
    }

    @Test
    fun `a partner-currency fact is taken as it is, without asking the currency service`() {
        company("summer", "CPA", "25")
        register(Facts.register(1028, "partner:summer"))

        invoice(Facts.invoice(id = 581, playerId = 1028, amount = usdt("12.34"), currency = "USDT"))

        assertThat(referral(1028).firstDepositAmount).isEqualTo(usdt("12.34"))
        assertThat(currency.calls.get()).isZero()
    }

    @Test
    fun `a fact whose envelope names another player than its data is refused and changes nothing`() {
        company("summer", "CPA", "25")
        register(Facts.register(1029, "partner:summer"))
        val mismatched = Facts.invoice(id = 591, playerId = 1029, amount = uah("400")).replaceFirst("\"playerId\": 1029", "\"playerId\": 1030")

        assertThatThrownBy { invoice(mismatched) }.isInstanceOf(MalformedEventException::class.java)
        assertThat(count("activity_daily")).isZero()
    }

    @Test
    fun `the postbacks are the Keitaro requests a tracker expects, in euros`() {
        company("summer", "CPA", "25")
        register(Facts.register(1030, "partner:summer", mapOf("click_id" to "8f3k2a")))
        invoice(Facts.invoice(id = 1001, playerId = 1030, amount = uah("400")))
        invoice(Facts.invoice(id = 1002, playerId = 1030, amount = uah("400")))
        spin(Facts.spin(id = 1003, playerId = 1030, type = "PLACE", realAmount = uah("20"), bonusAmount = uah("20")))
        bet(Facts.bet(id = 1004, playerId = 1030, type = "BET", realAmount = uah("80")))
        val base = "https://${FakeDns.TRACKER}/abc1234/postback"

        // The dispatcher's own conversion, as it runs before a request goes out: 40 UAH is 1 USDT is 0.5 EUR.
        val requests = postbacks.dispatched.map {
            val priced = it.event.copy(amount = currencyClient.convert(it.event.amount, it.event.currency, "EUR"), currency = "EUR")
            keitaro.build(base, priced).toString()
        }

        assertThat(requests).containsExactly(
            "$base?subid=8f3k2a&status=registration",
            "$base?subid=8f3k2a&status=deposit&payout=5.00&currency=eur&tid=dep-1001",
            "$base?subid=8f3k2a&status=deposit&payout=5.00&currency=eur&tid=dep-1002",
            "$base?subid=8f3k2a&status=spin&payout=0.50&currency=eur&tid=spin-1003",
            "$base?subid=8f3k2a&status=bet&payout=1.00&currency=eur&tid=bet-1004",
        )
    }

    @Test
    fun `a CPA of 0 records the first deposit and tells the trackers of every deposit, with no ledger line`() {
        val company = company("gift", "CPA", "0")
        register(Facts.register(1050, "partner:gift", mapOf("click_id" to "g1")))

        invoice(Facts.invoice(id = 801, playerId = 1050, amount = uah("400")))
        invoice(Facts.invoice(id = 802, playerId = 1050, amount = uah("800")))

        val referral = referral(1050)
        assertThat(referral.companyId).isEqualTo(company)
        assertThat(referral.firstDepositInvoiceId).isEqualTo(801)
        assertThat(referral.firstDepositAmount).isEqualTo(usdt("10"))
        assertThat(activity(1050)).containsExactly(Activity(Facts.DAY, 2, usdt("30"), 0))
        assertThat(facts()).containsExactlyInAnyOrder("register:1050", "invoice:801", "invoice:802")
        assertThat(commissions()).isEmpty()
        assertThat(balance(login)).isZero()
        assertThat(postbacks.dispatched.map { it.event.kind to it.event.amount }).containsExactly(
            PostbackEvent.Kind.REGISTRATION to 0L,
            PostbackEvent.Kind.DEPOSIT to uah("400"),
            PostbackEvent.Kind.DEPOSIT to uah("800"),
        )

        // An amount set afterwards pays the first deposits still to come; this player's was made under 0.
        setAmount(company, "25")
        invoice(Facts.invoice(id = 803, playerId = 1050, amount = uah("400")))
        register(Facts.register(1051, "partner:gift"))
        invoice(Facts.invoice(id = 804, playerId = 1051, amount = uah("400")))

        assertThat(commissions()).containsExactly(Commission("cpa:1051", "CPA", usdt("25"), Facts.DAY, company))
        assertThat(balance(login)).isEqualTo(usdt("25"))
    }

    @Test
    fun `a REVSHARE_DEPOSIT of 0 percent counts every deposit and tells the trackers of each, with no ledger line`() {
        val company = company("share-gift", "REVSHARE_DEPOSIT", "0")
        register(Facts.register(1055, "partner:share-gift", mapOf("click_id" to "s1")))
        postbacks.dispatched.clear()

        invoice(Facts.invoice(id = 821, playerId = 1055, amount = uah("400")))
        invoice(Facts.invoice(id = 822, playerId = 1055, amount = uah("4000")))

        assertThat(referral(1055).firstDepositInvoiceId).isEqualTo(821)
        assertThat(activity(1055)).containsExactly(Activity(Facts.DAY, 2, usdt("110"), 0))
        assertThat(commissions()).isEmpty()
        assertThat(balance(login)).isZero()
        assertThat(postbacks.dispatched.map { it.event.kind to it.event.amount }).containsExactly(
            PostbackEvent.Kind.DEPOSIT to uah("400"),
            PostbackEvent.Kind.DEPOSIT to uah("4000"),
        )

        // A percent set afterwards pays the deposits that come after it, never the ones already counted.
        setAmount(company, "10")
        invoice(Facts.invoice(id = 823, playerId = 1055, amount = uah("400")))

        assertThat(commissions()).containsExactly(Commission("deposit:823", "REVSHARE_DEPOSIT", usdt("1"), Facts.DAY, company))
        assertThat(balance(login)).isEqualTo(usdt("1"))
    }

    @Test
    fun `a REVSHARE_TURNOVER of 0 percent counts turnover with no ledger line, and a rollback takes back the turnover alone`() {
        val company = company("spins-gift", "REVSHARE_TURNOVER", "0")
        register(Facts.register(1060, "partner:spins-gift"))

        spin(Facts.spin(id = 901, playerId = 1060, type = "PLACE", realAmount = uah("200")))
        bet(Facts.bet(id = 951, playerId = 1060, type = "BET", realAmount = uah("400")))

        assertThat(activity(1060)).containsExactly(Activity(Facts.DAY, 0, 0, usdt("15")))
        assertThat(commissions()).isEmpty()
        assertThat(balance(login)).isZero()
        assertThat(fact("spin:901")).isEqualTo(usdt("5") to 0L)
        assertThat(source("spin:901")).isNull()
        assertThat(source("bet:951")).isNull()

        // A percent set afterwards pays the stakes that come after it; the earlier stakes' rollbacks take no commission back.
        setAmount(company, "5")
        spin(Facts.spin(id = 902, playerId = 1060, type = "PLACE", realAmount = uah("40")))
        spin(Facts.spin(id = 903, playerId = 1060, type = "ROLLBACK", realAmount = uah("200"), reference = Facts.spinLeg(901, 1060, "PLACE", uah("200"))))
        bet(Facts.bet(id = 952, playerId = 1060, type = "ROLLBACK", realAmount = uah("400"), reference = Facts.betLeg(951, 1060, "BET", uah("400"))))

        assertThat(activity(1060)).containsExactly(Activity(Facts.DAY, 0, 0, usdt("1")))
        assertThat(commissions()).containsExactly(
            Commission("turnover:$company:1060:${Facts.DAY}", "REVSHARE_TURNOVER", usdt("0.05"), Facts.DAY, company),
        )
        assertThat(balance(login)).isEqualTo(usdt("0.05"))
        assertThat(fact("spin:903")).isEqualTo(-usdt("5") to 0L)
        assertThat(fact("bet:952")).isEqualTo(-usdt("10") to 0L)
        assertThat(source("spin:903")).isNull()
        assertThat(source("bet:952")).isNull()
    }

    @Test
    fun `a share that rounds to nothing earns nothing and writes no ledger line`() {
        company("share", "REVSHARE_DEPOSIT", "12.5")
        company("spins", "REVSHARE_TURNOVER", "5")
        register(Facts.register(1065, "partner:share"))
        register(Facts.register(1066, "partner:spins"))

        // 40 nano UAH is one nano USDT: 12.5% and 5% of it are under half a nano.
        invoice(Facts.invoice(id = 831, playerId = 1065, amount = 40))
        spin(Facts.spin(id = 911, playerId = 1066, type = "PLACE", realAmount = 40))

        assertThat(activity(1065)).containsExactly(Activity(Facts.DAY, 1, 1, 0))
        assertThat(activity(1066)).containsExactly(Activity(Facts.DAY, 0, 0, 1))
        assertThat(commissions()).isEmpty()
        assertThat(balance(login)).isZero()
        assertThat(fact("spin:911")).isEqualTo(1L to 0L)
        assertThat(source("spin:911")).isNull()
    }

    private fun register(body: String) = registrations.handle(body.toByteArray())

    private fun invoice(body: String) = invoices.handle(body.toByteArray())

    private fun spin(body: String) = spins.handle(body.toByteArray())

    private fun bet(body: String) = bets.handle(body.toByteArray())

    private fun company(identity: String, type: String, amount: String, status: String = "ACTIVE", owner: Long = partner): Long =
        insertCompany(identity, type, amount, owner, status)

    private fun setStatus(company: Long, status: String) {
        jdbc.sql("UPDATE company SET status = :status WHERE id = :id").param("status", status).param("id", company).update()
    }

    /** New terms, as an admin's review would set them. */
    private fun setAmount(company: Long, amount: String) {
        jdbc.sql("UPDATE company SET amount = CAST(:amount AS NUMERIC) WHERE id = :id").param("amount", amount).param("id", company).update()
    }

    private fun referral(playerId: Long): Referral =
        jdbc.sql(
            """
            SELECT company_id, click_id, country, registered_at, first_deposit_at,
                   first_deposit_amount, first_deposit_invoice_id, attribution::text AS attribution
            FROM referral WHERE player_id = :playerId
            """,
        )
            .param("playerId", playerId)
            .query { rs, _ ->
                Referral(
                    companyId = rs.getLong("company_id"),
                    clickId = rs.getString("click_id"),
                    country = rs.getString("country"),
                    registeredAt = rs.getObject("registered_at", OffsetDateTime::class.java).toInstant(),
                    firstDepositAt = rs.getObject("first_deposit_at", OffsetDateTime::class.java)?.toInstant(),
                    firstDepositAmount = rs.getObject("first_deposit_amount") as Long?,
                    firstDepositInvoiceId = rs.getObject("first_deposit_invoice_id") as Long?,
                    attribution = rs.getString("attribution"),
                )
            }
            .single()

    private fun activity(playerId: Long): List<Activity> =
        jdbc.sql("SELECT day, deposit_count, deposit_amount, turnover_amount FROM activity_daily WHERE player_id = :playerId ORDER BY day")
            .param("playerId", playerId)
            .query { rs, _ ->
                Activity(rs.getString("day"), rs.getInt("deposit_count"), rs.getLong("deposit_amount"), rs.getLong("turnover_amount"))
            }
            .list()

    private fun commissions(): List<Commission> =
        jdbc.sql("SELECT source, type, amount, day, company_id, account_id FROM commission ORDER BY day, id")
            .query { rs, _ ->
                assertThat(rs.getLong("account_id")).isEqualTo(partner)
                Commission(rs.getString("source"), rs.getString("type"), rs.getLong("amount"), rs.getString("day"), rs.getLong("company_id"))
            }
            .list()

    private fun facts(): List<String> =
        jdbc.sql("SELECT key FROM processed_fact ORDER BY created_at, key").query { rs, _ -> rs.getString("key") }.list()

    /** What a fact recorded: (turnover, commission). */
    private fun fact(key: String): Pair<Long, Long> =
        jdbc.sql("SELECT turnover, commission FROM processed_fact WHERE key = :key")
            .param("key", key)
            .query { rs, _ -> rs.getLong("turnover") to rs.getLong("commission") }
            .single()

    /** The commission row a fact added to, or took back from; null when it touched none. */
    private fun source(key: String): String? =
        jdbc.sql("SELECT commission_source FROM processed_fact WHERE key = :key")
            .param("key", key)
            .query { rs, _ -> rs.getString("commission_source") }
            .list()
            .single()

    private fun count(table: String): Long =
        jdbc.sql("SELECT count(*) FROM $table").query(Long::class.javaObjectType).single()

    /** Waits until a transaction is blocked on a lock: the other pod's insert waiting for the open one to end. */
    private fun awaitLockWait() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (jdbc.sql("SELECT count(*) FROM pg_locks WHERE NOT granted").query(Long::class.javaObjectType).single() == 0L) {
            check(System.nanoTime() < deadline) { "nothing ever waited on a lock" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private data class Referral(
        val companyId: Long,
        val clickId: String?,
        val country: String?,
        val registeredAt: Instant,
        val firstDepositAt: Instant?,
        val firstDepositAmount: Long?,
        val firstDepositInvoiceId: Long?,
        val attribution: String,
    )

    private data class Activity(val day: String, val deposits: Int, val depositAmount: Long, val turnover: Long)

    private data class Commission(val source: String, val type: String, val amount: Long, val day: String, val companyId: Long)

    private companion object {

        const val WAIT_SECONDS = 30L

        const val POLL_MILLIS = 20L

        /** [amount] UAH in nano units. */
        fun uah(amount: String): Long = Money.toNano(BigDecimal(amount))

        /** [amount] USDT (the partner currency) in nano units. */
        fun usdt(amount: String): Long = Money.toNano(BigDecimal(amount))
    }
}
