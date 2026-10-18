package io.github.nekzabirov.affiliate.postback

import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.services.Money
import io.github.nekzabirov.affiliate.support.FakeDns
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class KeitaroAdapterTest {

    private val adapter = KeitaroAdapter(UrlGuard(FakeDns::lookup))

    private val host = FakeDns.TRACKER

    @Test
    fun `the pasted URL keeps scheme, host, port and key, and drops Keitaro's template query`() {
        assertThat(adapter.normalize("https://$host/fd730b9/postback?subid=REPLACE&status=REPLACE&payout=REPLACE"))
            .isEqualTo("https://$host/fd730b9/postback")
        assertThat(adapter.normalize("  HTTP://${host.uppercase()}:80/custom_KEY-1/postback  ")).isEqualTo("http://$host/custom_KEY-1/postback")
        assertThat(adapter.normalize("https://$host:443/k/postback")).isEqualTo("https://$host/k/postback")
        assertThat(adapter.normalize("http://$host:8080/k/postback")).isEqualTo("http://$host:8080/k/postback")
        assertThat(adapter.normalize("https://$host:8443/k/postback?subid={subid}")).isEqualTo("https://$host:8443/k/postback")
        assertThat(adapter.normalize("http://$host:443/k/postback")).isEqualTo("http://$host:443/k/postback")
    }

    @Test
    fun `anything but a public Keitaro postback URL is refused with the reason`() {
        mapOf(
            "" to "required",
            "   " to "required",
            "https://$host/${"k".repeat(2048)}/postback" to "length",
            "https://$host/k/postback " + "x" to "format",
            // An internationalized name, written escaped: the host is "tracker" in Cyrillic, under the Russian TLD.
            "https://\u0442\u0440\u0430\u043a\u0435\u0440.\u0440\u0444/k/postback" to "format",
            "https://$host/k/postback#top" to "format",
            "//$host/k/postback" to "format",
            "mailto:someone@$host" to "format",
            "javascript:alert(1)" to "format",
            "https://admin@$host/k/postback" to "format",
            "https://$host:abc/k/postback" to "format",
            "https://$host:9000/k/postback" to "format",
            "https://$host/k/postback/" to "format",
            "https://$host/k/Postback" to "format",
            "https://$host//postback" to "format",
            "https://$host/" to "format",
            "https://${FakeDns.INTERNAL}/k/postback" to "blocked_host",
            "https://${FakeDns.MIXED}/k/postback" to "blocked_host",
            "http://10.1.2.3/k/postback" to "blocked_host",
            "http://[fd00:ec2::254]/k/postback" to "blocked_host",
            "http://localhost:8080/k/postback" to "blocked_host",
            "https://${FakeDns.NOWHERE}/k/postback" to "unresolvable_host",
        ).forEach { (url, rule) ->
            assertThatThrownBy { adapter.normalize(url) }
                .describedAs(url)
                .isInstanceOfSatisfying(ValidationException::class.java) {
                    assertThat(it.field).isEqualTo("url")
                    assertThat(it.rule).describedAs(url).isEqualTo(rule)
                }
        }
    }

    @Test
    fun `a registration is Keitaro's registration, with no payout`() {
        val uri = adapter.build("https://$host/k/postback", event(PostbackEvent.Kind.REGISTRATION, amount = "0"))

        assertThat(uri.toString()).isEqualTo("https://$host/k/postback?subid=2k5a13p.ac.2m74&status=registration")
    }

    @Test
    fun `every deposit is a deposit of its own with its whole amount, told apart by its invoice`() {
        val uri = adapter.build("https://$host/k/postback", event(PostbackEvent.Kind.DEPOSIT, amount = "12.345", factId = 9001))

        assertThat(uri.toString())
            .isEqualTo("https://$host/k/postback?subid=2k5a13p.ac.2m74&status=deposit&payout=12.35&currency=eur&tid=dep-9001")
        assertThat(uri.rawQuery).doesNotContain("cost", "sub_id", "source", "date")
    }

    @Test
    fun `every stake is a spin or a bet with the whole stake, told apart by its leg`() {
        val spin = adapter.build("https://$host/k/postback", event(PostbackEvent.Kind.SPIN, amount = "0.5", factId = 31))
        val bet = adapter.build("https://$host/k/postback", event(PostbackEvent.Kind.BET, amount = "20", factId = 32))

        assertThat(spin.toString()).isEqualTo("https://$host/k/postback?subid=2k5a13p.ac.2m74&status=spin&payout=0.50&currency=eur&tid=spin-31")
        assertThat(bet.toString()).isEqualTo("https://$host/k/postback?subid=2k5a13p.ac.2m74&status=bet&payout=20.00&currency=eur&tid=bet-32")
    }

    @Test
    fun `click ids are encoded, and a postback cannot be built without one`() {
        val uri = adapter.build("https://$host/k/postback", event(PostbackEvent.Kind.REGISTRATION, amount = "0", clickId = "a~b c&d"))

        assertThat(uri.rawQuery).startsWith("subid=a%7Eb+c%26d&")
        assertThatThrownBy { adapter.build("https://$host/k/postback", event(PostbackEvent.Kind.REGISTRATION, amount = "0", clickId = null)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun event(kind: PostbackEvent.Kind, amount: String, factId: Long? = null, clickId: String? = "2k5a13p.ac.2m74") =
        PostbackEvent(kind, playerId = 42, clickId = clickId, factId = factId, amount = Money.toNano(BigDecimal(amount)), currency = "EUR")
}
