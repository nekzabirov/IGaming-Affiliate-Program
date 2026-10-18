package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PayoutTest : IntegrationTest() {

    private lateinit var login: String

    private lateinit var session: String

    @BeforeEach
    fun partner() {
        login = uniqueLogin("uma")
        session = api.register(login)
        setBalance(login, 100 * NANO)
    }

    @Test
    fun `a request reserves the amount at once and shows as pending`() {
        val created = api.post("/api/payouts", mapOf("amount" to "30.25", "network" to "trc20", "address" to TRC20), session)

        assertThat(created.status).isEqualTo(201)
        assertThat(created.body["amount"].asString()).isEqualTo("30.25")
        assertThat(created.body["currency"].asString()).isEqualTo("USDT")
        assertThat(created.body["network"].asString()).isEqualTo("TRC20")
        assertThat(created.body["address"].asString()).isEqualTo(TRC20)
        assertThat(created.body["status"].asString()).isEqualTo("PENDING")
        assertThat(created.body["txHash"].isNull).isTrue()
        assertThat(balance(login)).isEqualTo(69_750_000_000L)

        val me = api.get("/api/me", session)
        assertThat(me.body["balance"].asString()).isEqualTo("69.75")
        assertThat(me.body["pendingPayouts"].asString()).isEqualTo("30.25")
        assertThat(me.body["paidOut"].asString()).isEqualTo("0")

        jdbc.sql("UPDATE payout SET status = 'PAID', tx_hash = 'abc' WHERE account_id = :id").param("id", accountId(login)).update()
        val paid = api.get("/api/me", session)
        assertThat(paid.body["pendingPayouts"].asString()).isEqualTo("0")
        assertThat(paid.body["paidOut"].asString()).isEqualTo("30.25")
        val history = api.get("/api/payouts", session)
        assertThat(history.body["total"].asLong()).isEqualTo(1)
        assertThat(history.body["items"][0]["txHash"].asString()).isEqualTo("abc")
    }

    @Test
    fun `more than the balance is refused and nothing is reserved`() {
        api.post("/api/payouts", mapOf("amount" to "60", "network" to "ERC20", "address" to EVM), session)

        val second = api.post("/api/payouts", mapOf("amount" to "40.01", "network" to "ERC20", "address" to EVM), session)

        assertThat(second.status).isEqualTo(409)
        assertThat(second.code).isEqualTo("INSUFFICIENT_BALANCE")
        assertThat(balance(login)).isEqualTo(40 * NANO)
        assertThat(jdbc.sql("SELECT count(*) FROM payout").query(Long::class.javaObjectType).single()).isEqualTo(1)
        val exact = api.post("/api/payouts", mapOf("amount" to "40", "network" to "BEP20", "address" to EVM), session)
        assertThat(exact.status).isEqualTo(201)
        assertThat(balance(login)).isZero()
    }

    @Test
    fun `parallel requests can never reserve more than the balance`() {
        val replies = (1..8).map {
            Thread.ofVirtual().start {
                api.post("/api/payouts", mapOf("amount" to "30", "network" to "TON", "address" to TON), session)
            }
        }
        replies.forEach { it.join() }

        assertThat(jdbc.sql("SELECT count(*) FROM payout").query(Long::class.javaObjectType).single()).isEqualTo(3)
        assertThat(balance(login)).isEqualTo(10 * NANO)
    }

    @Test
    fun `amount, network and address are validated per network`() {
        fun request(amount: Any?, network: String?, address: String?) =
            api.post("/api/payouts", mapOf("amount" to amount, "network" to network, "address" to address), session)

        listOf(
            request(null, "TRC20", TRC20) to ("amount" to "required"),
            request("0", "TRC20", TRC20) to ("amount" to "positive"),
            request("1.001", "TRC20", TRC20) to ("amount" to "format"),
            request("10", null, TRC20) to ("network" to "required"),
            request("10", "SOL", TRC20) to ("network" to "invalid"),
            request("10", "TRC20", null) to ("address" to "required"),
            request("10", "TRC20", EVM) to ("address" to "format"),
            request("10", "TRC20", "T" + "0".repeat(33)) to ("address" to "format"),
            request("10", "ERC20", TRC20) to ("address" to "format"),
            request("10", "ERC20", "0x" + "g".repeat(40)) to ("address" to "format"),
            request("10", "BEP20", EVM + "0") to ("address" to "format"),
            request("10", "TON", "XQ" + "a".repeat(46)) to ("address" to "format"),
            request("10", "TON", TON + "a") to ("address" to "format"),
        ).forEach { (reply, expected) ->
            assertThat(reply.status).describedAs(reply.toString()).isEqualTo(400)
            assertThat(reply.field to reply.rule).describedAs(reply.toString()).isEqualTo(expected)
        }
        assertThat(balance(login)).isEqualTo(100 * NANO)
        assertThat(request("10", "TON", "UQ" + "b_-".repeat(15) + "c").status).isEqualTo(201)
    }

    private companion object {

        const val TRC20 = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE"

        const val EVM = "0x52908400098527886E0F7030069857D2E4169EE7"

        const val TON = "EQDtFpEwcFAEcRe5mLVh2N6C0x-_hJEM7W61_JLnSF74p4q2"
    }
}
