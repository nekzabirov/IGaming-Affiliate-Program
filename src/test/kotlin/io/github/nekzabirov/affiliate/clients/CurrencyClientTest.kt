package io.github.nekzabirov.affiliate.clients

import io.github.nekzabirov.affiliate.support.FakeCurrencyManager
import io.github.nekzabirov.affiliate.support.testProperties
import io.grpc.ManagedChannel
import io.grpc.Status
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** The production client over a real gRPC channel to the in-process currency service. */
class CurrencyClientTest {

    private val currency = FakeCurrencyManager()

    private val channel: ManagedChannel = currency.channel()

    private val client = CurrencyClient(channel, testProperties())

    @AfterEach
    fun close() {
        channel.shutdownNow()
        currency.close()
    }

    @Test
    fun `an amount is exchanged into the partner currency at the service's rate`() {
        assertThat(client.toPartnerCurrency(400_000_000_000, "UAH")).isEqualTo(10_000_000_000)
        assertThat(currency.calls.get()).isEqualTo(1)
    }

    @Test
    fun `the partner currency itself is never sent to the service`() {
        assertThat(client.toPartnerCurrency(1_234, "USDT")).isEqualTo(1_234)
        assertThat(currency.calls.get()).isZero()
    }

    @Test
    fun `a connection found gone is asked once more, and only once`() {
        currency.failNext(Status.UNAVAILABLE)

        assertThat(client.toPartnerCurrency(400_000_000_000, "UAH")).isEqualTo(10_000_000_000)
        assertThat(currency.calls.get()).isEqualTo(2)

        currency.reset()
        currency.failNext(Status.UNAVAILABLE)
        currency.failNext(Status.UNAVAILABLE)
        assertThatThrownBy { client.toPartnerCurrency(1, "UAH") }
            .isInstanceOf(CurrencyConversionException::class.java)
            .hasMessageContaining("UNAVAILABLE")
        assertThat(currency.calls.get()).isEqualTo(2)
    }

    @Test
    fun `a refusal is final and names the service's reason`() {
        currency.failNext(Status.FAILED_PRECONDITION, "RATE_MISSING")

        assertThatThrownBy { client.toPartnerCurrency(1, "UAH") }
            .isInstanceOf(CurrencyConversionException::class.java)
            .hasMessage("The currency service did not convert UAH to USDT: RATE_MISSING")
        assertThat(currency.calls.get()).isEqualTo(1)

        assertThatThrownBy { client.toPartnerCurrency(1, "XYZ") }
            .isInstanceOf(CurrencyConversionException::class.java)
            .hasMessageContaining(FakeCurrencyManager.UNKNOWN_CURRENCY)
    }

    @Test
    fun `a service that does not answer is given up after the deadline, without a second try`() {
        currency.silent = true
        val started = System.nanoTime()

        assertThatThrownBy { client.toPartnerCurrency(1, "UAH") }
            .isInstanceOf(CurrencyConversionException::class.java)
            .hasMessageContaining("DEADLINE_EXCEEDED")
        val seconds = (System.nanoTime() - started) / NANOS_PER_SECOND.toDouble()
        assertThat(seconds).isBetween(DEADLINE_SECONDS - SLACK_SECONDS, DEADLINE_SECONDS + SLACK_SECONDS)
        assertThat(currency.calls.get()).isEqualTo(1)
    }

    private companion object {

        const val NANOS_PER_SECOND = 1_000_000_000L

        const val DEADLINE_SECONDS = 3.0

        const val SLACK_SECONDS = 1.0
    }
}
