package io.github.nekzabirov.affiliate.events

import io.github.nekzabirov.affiliate.config.RabbitConfig
import io.github.nekzabirov.affiliate.services.Money
import io.github.nekzabirov.affiliate.support.Facts
import io.github.nekzabirov.affiliate.support.FakeCurrencyManager
import io.github.nekzabirov.affiliate.support.FakeDns
import io.github.nekzabirov.affiliate.support.IntegrationTest
import io.github.nekzabirov.affiliate.support.TestRabbit
import io.grpc.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal

/**
 * The whole path through a real broker, with the listener running: facts published to the publishers' own
 * exchanges in the documented shapes reach the one work queue and are counted once each, a malformed one is
 * dead-lettered into the dead queue and nowhere else, and a dead letter moved back is routed by its `x-death`.
 */
class EventFlowTest : IntegrationTest() {

    @Autowired
    private lateinit var rabbit: RabbitTemplate

    @Autowired
    private lateinit var admin: AmqpAdmin

    @Autowired
    private lateinit var currency: FakeCurrencyManager

    @BeforeEach
    fun emptyQueues() {
        currency.reset()
        admin.purgeQueue(QUEUE, false)
        admin.purgeQueue(RabbitConfig.DEAD_QUEUE, false)
    }

    @Test
    fun `a dead letter moved back through the default exchange is counted, and never reaches another reader`() {
        val login = uniqueLogin("ruth")
        api.register(login)
        insertCompany("flow-replay", "CPA", "25", accountId(login))
        // Another reader of the route, a reporting service, say: a replay must not hand it a second copy.
        val other = Queue(OTHER_READER, false, false, false)
        admin.declareQueue(other)
        admin.declareBinding(BindingBuilder.bind(other).to(TopicExchange(PLAYER_EXCHANGE)).with("invoice.events"))
        try {
            publish(PLAYER_EXCHANGE, "register.events", Facts.register(3101, "flow-replay"))
            eventually { assertThat(count("SELECT count(*) FROM referral")).isEqualTo(1) }

            // The currency service refuses the deposit twice: it dies, is moved back while the cause lasts, and dies again.
            currency.failNext(Status.FAILED_PRECONDITION, "RATE_MISSING")
            currency.failNext(Status.FAILED_PRECONDITION, "RATE_MISSING")
            publish(PLAYER_EXCHANGE, "invoice.events", Facts.invoice(id = 911, playerId = 3101, amount = nano("400")))
            moveBack(awaitDead())
            val twice = awaitDead()
            val death = checkNotNull(twice.messageProperties.xDeathHeader) { "no x-death header" }.single { it["queue"] == QUEUE }
            assertThat(death["count"]).isEqualTo(2L)
            assertThat(death["exchange"]).isEqualTo(PLAYER_EXCHANGE)
            assertThat(death["routing-keys"] as List<*>).containsExactly("invoice.events")

            moveBack(twice)

            eventually { assertThat(count("SELECT count(*) FROM processed_fact WHERE key = 'invoice:911'")).isEqualTo(1) }
            assertThat(count("SELECT first_deposit_invoice_id FROM referral WHERE player_id = 3101")).isEqualTo(911)
            assertThat(count("SELECT amount FROM commission WHERE source = 'cpa:3101'")).isEqualTo(nano("25"))
            assertThat(balance(login)).isEqualTo(nano("25"))
            assertThat(queued(RabbitConfig.DEAD_QUEUE)).isZero()
            assertThat(queued(OTHER_READER)).isEqualTo(1)
        } finally {
            admin.deleteQueue(OTHER_READER)
        }
    }

    @Test
    fun `published facts are counted once each, and a malformed one waits in the dead queue`() {
        val login = uniqueLogin("flora")
        api.register(login)
        val owner = accountId(login)
        val cpa = insertCompany("flow-cpa", "CPA", "25", owner)
        val turnover = insertCompany("flow-turn", "REVSHARE_TURNOVER", "5", owner)
        val tracker = insertPostback(cpa, "https://${FakeDns.INTERNAL}/k1/postback")

        publish(PLAYER_EXCHANGE, "register.events", Facts.register(3001, "flow-cpa", mapOf("click_id" to "k1")), requestId = "flow-1")
        publish(PLAYER_EXCHANGE, "register.events", Facts.register(3002, "flow-turn"))
        eventually { assertThat(count("SELECT count(*) FROM referral")).isEqualTo(2) }
        // The lead went to the company's tracker after the commit, and the guard refused its internal address.
        assertThat(awaitAttempt(tracker)).isEqualTo(null to "Blocked address")

        val deposit = Facts.invoice(id = 901, playerId = 3001, amount = nano("400"))
        publish(PLAYER_EXCHANGE, "invoice.events", deposit)
        publish(PLAYER_EXCHANGE, "invoice.events", deposit)
        publish(CASINO_EXCHANGE, "spin.events", Facts.spin(id = 801, playerId = 3002, type = "PLACE", realAmount = nano("200")))
        publish(CASINO_EXCHANGE, "bet.events", Facts.bet(id = 802, playerId = 3002, type = "BET", realAmount = nano("400")))
        publish(CASINO_EXCHANGE, "spin.events", Facts.spin(id = 804, playerId = 9999, type = "PLACE", realAmount = nano("100")))
        eventually { assertThat(count("SELECT count(*) FROM processed_fact")).isEqualTo(5) }
        publish(CASINO_EXCHANGE, "spin.events", Facts.spin(id = 803, playerId = 3002, type = "ROLLBACK", realAmount = nano("200"), reference = Facts.spinLeg(801, 3002, "PLACE", nano("200"))))
        eventually { assertThat(count("SELECT count(*) FROM processed_fact")).isEqualTo(6) }

        // 400 UAH is 10 USDT: the CPA's minimum, so its 25 are earned; the rollback took the spin's 5 back out.
        assertThat(count("SELECT first_deposit_invoice_id FROM referral WHERE player_id = 3001")).isEqualTo(901)
        assertThat(count("SELECT sum(deposit_count) FROM activity_daily WHERE player_id = 3001")).isEqualTo(1)
        assertThat(count("SELECT sum(turnover_amount) FROM activity_daily WHERE player_id = 3002")).isEqualTo(nano("10"))
        assertThat(count("SELECT amount FROM commission WHERE source = 'cpa:3001'")).isEqualTo(nano("25"))
        assertThat(count("SELECT amount FROM commission WHERE company_id = $turnover")).isEqualTo(nano("0.5"))
        assertThat(balance(login)).isEqualTo(nano("25.5"))
        assertThat(queued(QUEUE)).isZero()
        assertThat(queued(RabbitConfig.DEAD_QUEUE)).isZero()

        val malformed = Facts.invoice(id = 902, playerId = 3001, amount = nano("400")).replaceFirst("\"playerId\": 3001", "\"playerId\": \"3001\"")
        publish(PLAYER_EXCHANGE, "invoice.events", malformed)

        val dead = checkNotNull(rabbit.receive(RabbitConfig.DEAD_QUEUE, DEAD_LETTER_WAIT_MILLIS)) { "nothing was dead-lettered" }
        assertThat(String(dead.body)).isEqualTo(malformed)
        val death = checkNotNull(dead.messageProperties.xDeathHeader) { "no x-death header" }.single()
        assertThat(death["queue"]).isEqualTo(QUEUE)
        assertThat(death["reason"]).isEqualTo("rejected")
        assertThat(death["exchange"]).isEqualTo(PLAYER_EXCHANGE)
        assertThat(death["routing-keys"] as List<*>).containsExactly("invoice.events")
        assertThat(count("SELECT count(*) FROM processed_fact")).isEqualTo(6)
        assertThat(balance(login)).isEqualTo(nano("25.5"))
    }

    @Test
    fun `facts the app does not act on, and enum values added later, are acknowledged and never dead-lettered`() {
        val login = uniqueLogin("otto")
        api.register(login)
        insertCompany("flow-other", "REVSHARE_TURNOVER", "5", accountId(login))
        publish(PLAYER_EXCHANGE, "register.events", Facts.register(3201, "flow-other"))
        eventually { assertThat(count("SELECT count(*) FROM referral")).isEqualTo(1) }

        val deposit = nano("400")
        listOf("PAYOUT", "REFUND", "CHARGEBACK").forEachIndexed { index, type ->
            publish(PLAYER_EXCHANGE, "invoice.events", Facts.invoice(id = 921L + index, playerId = 3201, amount = deposit, type = type))
        }
        listOf("PENDING", "PROCESSING", "REJECTED", "EXPIRED").forEachIndexed { index, status ->
            publish(PLAYER_EXCHANGE, "invoice.events", Facts.invoice(id = 931L + index, playerId = 3201, amount = deposit, status = status))
        }
        publish(CASINO_EXCHANGE, "spin.events", Facts.spin(id = 941, playerId = 3201, type = "SETTLE", realAmount = nano("1000")))
        publish(CASINO_EXCHANGE, "spin.events", Facts.spin(id = 942, playerId = 3201, type = "JACKPOT", realAmount = nano("1000")))
        publish(CASINO_EXCHANGE, "spin.events", Facts.spin(id = 943, playerId = 3201, type = "ROLLBACK", realAmount = nano("1000"), reference = Facts.spinLeg(941, 3201, "SETTLE", nano("1000"))))
        publish(CASINO_EXCHANGE, "bet.events", Facts.bet(id = 951, playerId = 3201, type = "WIN", realAmount = nano("1000")))
        publish(CASINO_EXCHANGE, "bet.events", Facts.bet(id = 952, playerId = 3201, type = "CASHOUT", realAmount = nano("1000")))
        publish(CASINO_EXCHANGE, "bet.events", Facts.bet(id = 953, playerId = 3201, type = "ROLLBACK", realAmount = nano("1000"), reference = Facts.betLeg(951, 3201, "WIN", nano("1000"))))
        // One listener answers the deliveries in order: once this stake is counted, every fact before it was answered.
        publish(CASINO_EXCHANGE, "spin.events", Facts.spin(id = 944, playerId = 3201, type = "PLACE", realAmount = nano("200")))
        eventually { assertThat(count("SELECT count(*) FROM processed_fact WHERE key = 'spin:944'")).isEqualTo(1) }

        assertThat(count("SELECT count(*) FROM processed_fact")).isEqualTo(2)
        assertThat(count("SELECT count(*) FROM referral WHERE first_deposit_invoice_id IS NOT NULL")).isZero()
        assertThat(count("SELECT sum(turnover_amount) FROM activity_daily WHERE player_id = 3201")).isEqualTo(nano("5"))
        assertThat(queued(QUEUE)).isZero()
        assertThat(queued(RabbitConfig.DEAD_QUEUE)).isZero()
    }

    private fun publish(exchange: String, routingKey: String, body: String, requestId: String? = null) {
        val message = MessageBuilder.withBody(body.toByteArray())
            .setContentType("application/json")
            .apply { if (requestId != null) setHeader("x-request-id", requestId) }
            .build()
        rabbit.send(exchange, routingKey, message)
    }

    /** What the README's replay does: the dead letter, its properties and headers as they are, through the default exchange. */
    private fun moveBack(dead: Message) {
        rabbit.send("", QUEUE, dead)
    }

    private fun awaitDead(): Message =
        checkNotNull(rabbit.receive(RabbitConfig.DEAD_QUEUE, DEAD_LETTER_WAIT_MILLIS)) { "nothing was dead-lettered" }

    private fun count(sql: String): Long = jdbc.sql(sql).query(Long::class.javaObjectType).single()

    private fun queued(queue: String): Long = checkNotNull(admin.getQueueInfo(queue)) { "no queue $queue" }.messageCount

    /** Delivery is asynchronous: retries [assertion] until it holds or the wait is over. */
    private fun eventually(assertion: () -> Unit) {
        val deadline = System.nanoTime() + WAIT_NANOS
        while (true) {
            try {
                assertion()
                return
            } catch (failure: AssertionError) {
                if (System.nanoTime() > deadline) throw failure
                Thread.sleep(POLL_MILLIS)
            }
        }
    }

    companion object {

        private const val PLAYER_EXCHANGE = "pam.events"

        private const val CASINO_EXCHANGE = "casino.events"

        private const val QUEUE = "affiliate.events"

        private const val OTHER_READER = "flow.other-reader.invoice.events"

        private const val WAIT_NANOS = 20_000_000_000L

        private const val POLL_MILLIS = 100L

        private const val DEAD_LETTER_WAIT_MILLIS = 20_000L

        /** UAH and USDT amounts alike in nano units; the fake currency service makes 40 UAH one USDT. */
        private fun nano(amount: String): Long = Money.toNano(BigDecimal(amount))

        @JvmStatic
        @DynamicPropertySource
        fun broker(registry: DynamicPropertyRegistry) {
            registry.add("spring.rabbitmq.host") { TestRabbit.host }
            registry.add("spring.rabbitmq.port") { TestRabbit.port }
            registry.add("spring.rabbitmq.username") { TestRabbit.username }
            registry.add("spring.rabbitmq.password") { TestRabbit.password }
            registry.add("spring.rabbitmq.listener.simple.auto-startup") { "true" }
        }
    }
}
