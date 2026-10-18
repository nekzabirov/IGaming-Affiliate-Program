package io.github.nekzabirov.affiliate.events

import io.github.nekzabirov.affiliate.support.Facts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** The envelope and the wire records against the documented payloads, and against every shape a fact must not have. */
class EventReaderTest {

    private val reader = EventReader()

    @Test
    fun `the documented payloads read, everything the app does not model ignored`() {
        val player = read(Facts.register(2, "summer", mapOf("click_id" to "8f3k2a")), RegisterEvent::class.java) { it.id }
        assertThat(player).isEqualTo(Envelope(2, RegisterEvent(2, "summer", mapOf("click_id" to "8f3k2a"), "UA", Instant.parse(Facts.AT))))

        val invoice = read(Facts.invoice(id = 1, playerId = 2, amount = 100_000_000_000), InvoiceEvent::class.java) { it.player.id }.data
        val owner = InvoiceEvent.Player(2, "referal:1", mapOf("fbc" to "fb.1.1759300000000.IwAR2xYz"), "UA", Instant.parse(Facts.AT))
        assertThat(invoice).isEqualTo(
            InvoiceEvent(1, InvoiceEvent.Type.CREDIT, InvoiceEvent.Status.SUCCESS, 100_000_000_000, "UAH", Instant.parse(Facts.AT), owner),
        )

        val rollback = Facts.spin(5, 1042, "ROLLBACK", 7, reference = Facts.spinLeg(4, 1042, "PLACE", 7))
        val spin = read(rollback, SpinEvent::class.java) { it.round.playerId }.data
        assertThat(spin).isEqualTo(
            SpinEvent(5, SpinEvent.Type.ROLLBACK, 7, 7, SpinEvent.Reference(4, SpinEvent.Type.PLACE), SpinEvent.Round(1042, "UAH", Instant.parse(Facts.AT))),
        )

        val bet = read(Facts.bet(2, 1042, "BET", 25_000_000_000), BetEvent::class.java) { it.bet.playerId }.data
        assertThat(bet).isEqualTo(BetEvent(2, BetEvent.Type.BET, 25_000_000_000, 25_000_000_000, null, BetEvent.Bet(1042, "UAH", Instant.parse(Facts.AT))))
    }

    @Test
    fun `an enum value no handler acts on, a future one included, reads as OTHER instead of failing the fact`() {
        listOf("PAYOUT", "REFUND", "CHARGEBACK").forEach { type ->
            val invoice = read(Facts.invoice(id = 1, playerId = 2, amount = 1, type = type), InvoiceEvent::class.java) { it.player.id }.data
            assertThat(invoice.type).describedAs(type).isEqualTo(InvoiceEvent.Type.OTHER)
            assertThat(invoice.status).isEqualTo(InvoiceEvent.Status.SUCCESS)
        }
        listOf("PENDING", "PROCESSING", "REJECTED", "EXPIRED", "success").forEach { status ->
            val invoice = read(Facts.invoice(id = 1, playerId = 2, amount = 1, status = status), InvoiceEvent::class.java) { it.player.id }.data
            assertThat(invoice.status).describedAs(status).isEqualTo(InvoiceEvent.Status.OTHER)
            assertThat(invoice.type).isEqualTo(InvoiceEvent.Type.CREDIT)
        }

        val settle = read(Facts.spin(6, 1042, "SETTLE", 7, reference = Facts.spinLeg(4, 1042, "PLACE", 7)), SpinEvent::class.java) { it.round.playerId }.data
        assertThat(settle.type).isEqualTo(SpinEvent.Type.OTHER)
        assertThat(settle.reference).isEqualTo(SpinEvent.Reference(4, SpinEvent.Type.PLACE))
        val rolledBackWin = read(Facts.spin(7, 1042, "ROLLBACK", 7, reference = Facts.spinLeg(6, 1042, "SETTLE", 7)), SpinEvent::class.java) { it.round.playerId }.data
        assertThat(rolledBackWin.type).isEqualTo(SpinEvent.Type.ROLLBACK)
        assertThat(rolledBackWin.reference).isEqualTo(SpinEvent.Reference(6, SpinEvent.Type.OTHER))
        assertThat(read(Facts.spin(8, 1042, "JACKPOT", 7), SpinEvent::class.java) { it.round.playerId }.data.type).isEqualTo(SpinEvent.Type.OTHER)

        val win = read(Facts.bet(2, 1042, "WIN", 25_000_000_000), BetEvent::class.java) { it.bet.playerId }.data
        assertThat(win).isEqualTo(BetEvent(2, BetEvent.Type.OTHER, 25_000_000_000, 25_000_000_000, null, BetEvent.Bet(1042, "UAH", Instant.parse(Facts.AT))))
        val rolledBackBetWin = read(Facts.bet(3, 1042, "ROLLBACK", 1, reference = Facts.betLeg(2, 1042, "WIN", 1)), BetEvent::class.java) { it.bet.playerId }.data
        assertThat(rolledBackBetWin.reference).isEqualTo(BetEvent.Reference(2, BetEvent.Type.OTHER))
        assertThat(read(Facts.bet(4, 1042, "CASHOUT", 1), BetEvent::class.java) { it.bet.playerId }.data.type).isEqualTo(BetEvent.Type.OTHER)
    }

    @Test
    fun `the examples in docs events read as the documentation says`() {
        val examples = Path.of("docs", "events", "examples")
        val register = read(Files.readString(examples.resolve("register.events.json")), RegisterEvent::class.java) { it.id }
        assertThat(register.data.affiliateTag).isEqualTo("summer")
        val invoice = read(Files.readString(examples.resolve("invoice.events.json")), InvoiceEvent::class.java) { it.player.id }
        assertThat(invoice.data.type to invoice.data.status).isEqualTo(InvoiceEvent.Type.CREDIT to InvoiceEvent.Status.SUCCESS)
        val spin = read(Files.readString(examples.resolve("spin.events.json")), SpinEvent::class.java) { it.round.playerId }
        assertThat(spin.data.type).isEqualTo(SpinEvent.Type.PLACE)
        val bet = read(Files.readString(examples.resolve("bet.events.json")), BetEvent::class.java) { it.bet.playerId }
        assertThat(bet.data.type).isEqualTo(BetEvent.Type.BET)
        assertThat(listOf(register, invoice, spin, bet).map { it.playerId }.toSet()).containsExactly(1001L)
    }

    @Test
    fun `fields a publisher leaves out or sends as null are what they mean, never a failure`() {
        val bare = """{"playerId": 3, "data": {"id": 3, "createdAt": "${Facts.AT}"}}"""
        assertThat(read(bare, RegisterEvent::class.java) { it.id }.data)
            .isEqualTo(RegisterEvent(3, null, null, null, Instant.parse(Facts.AT)))

        val nulls = """{"playerId": 3, "data": {"id": 3, "affiliateTag": null, "affiliateAttribution": {}, "country": null, "createdAt": "${Facts.AT}"}}"""
        assertThat(read(nulls, RegisterEvent::class.java) { it.id }.data)
            .isEqualTo(RegisterEvent(3, null, emptyMap(), null, Instant.parse(Facts.AT)))
    }

    @Test
    fun `a playerId that is not a positive JSON integer is a malformed fact`() {
        val data = """{"id": 7, "createdAt": "${Facts.AT}"}"""
        listOf(
            """{"data": $data}""",
            """{"playerId": null, "data": $data}""",
            """{"playerId": "7", "data": $data}""",
            """{"playerId": 7.5, "data": $data}""",
            """{"playerId": 7.0, "data": $data}""",
            """{"playerId": 7e0, "data": $data}""",
            """{"playerId": 0, "data": $data}""",
            """{"playerId": -7, "data": $data}""",
            """{"playerId": 92233720368547758070, "data": $data}""",
            """{"playerId": true, "data": $data}""",
            """{"playerId": {"id": 7}, "data": $data}""",
        ).forEach { body ->
            assertThatThrownBy { read(body, RegisterEvent::class.java) { it.id } }
                .describedAs(body)
                .isInstanceOf(MalformedEventException::class.java)
                .hasMessageContaining("playerId")
        }
    }

    @Test
    fun `an envelope without a data object, or not an envelope at all, is a malformed fact`() {
        listOf(
            """{"playerId": 7}""",
            """{"playerId": 7, "data": null}""",
            """{"playerId": 7, "data": "{}"}""",
            """{"playerId": 7, "data": [1]}""",
            """[{"playerId": 7}]""",
            """"text"""",
            "",
        ).forEach { body ->
            assertThatThrownBy { read(body, RegisterEvent::class.java) { it.id } }
                .describedAs(body)
                .isInstanceOf(MalformedEventException::class.java)
        }
    }

    @Test
    fun `a playerId that is not the player inside data is a malformed fact`() {
        val register = Facts.register(7, "summer").replaceFirst("\"playerId\": 7", "\"playerId\": 8")
        val invoice = Facts.invoice(id = 1, playerId = 7, amount = 1).replaceFirst("\"playerId\": 7", "\"playerId\": 8")
        val spin = Facts.spin(1, 7, "PLACE", 1).replaceFirst("\"playerId\": 7", "\"playerId\": 8")
        val bet = Facts.bet(1, 7, "BET", 1).replaceFirst("\"playerId\": 7", "\"playerId\": 8")

        assertThatThrownBy { read(register, RegisterEvent::class.java) { it.id } }.isInstanceOf(MalformedEventException::class.java)
        assertThatThrownBy { read(invoice, InvoiceEvent::class.java) { it.player.id } }.isInstanceOf(MalformedEventException::class.java)
        assertThatThrownBy { read(spin, SpinEvent::class.java) { it.round.playerId } }.isInstanceOf(MalformedEventException::class.java)
        assertThatThrownBy { read(bet, BetEvent::class.java) { it.bet.playerId } }.isInstanceOf(MalformedEventException::class.java)
    }

    @Test
    fun `inside data a required field is never defaulted and a value is never coerced`() {
        val valid = Facts.invoice(id = 1, playerId = 7, amount = 5_000_000_000)
        listOf(
            valid.replace("\"amount\": 5000000000", "\"amount\": \"5000000000\""),
            valid.replace("\"amount\": 5000000000", "\"amount\": 5000000000.0"),
            valid.replace("\"amount\": 5000000000", "\"amount\": 5e9"),
            valid.replace("\"amount\": 5000000000", "\"amount\": null"),
            valid.replace("\"amount\": 5000000000,", ""),
            valid.replace("\"type\": \"CREDIT\"", "\"type\": 0"),
            valid.replace("\"type\": \"CREDIT\"", "\"type\": null"),
            valid.replace("\"status\": \"SUCCESS\"", "\"status\": 2"),
            valid.replace("\"status\": \"SUCCESS\"", "\"status\": null"),
            valid.replace("\"updatedAt\": \"${Facts.AT}\"", "\"updatedAt\": \"yesterday\""),
            valid.replace("\"currency\": \"UAH\",\n", ""),
        ).forEach { body ->
            assertThat(body).isNotEqualTo(valid)
            assertThatThrownBy { read(body, InvoiceEvent::class.java) { it.player.id } }
                .describedAs(body)
                .isInstanceOf(RuntimeException::class.java)
        }
    }

    private fun <T : Any> read(body: String, type: Class<T>, playerOf: (T) -> Long): Envelope<T> =
        reader.read(body.toByteArray(), type, playerOf)
}
