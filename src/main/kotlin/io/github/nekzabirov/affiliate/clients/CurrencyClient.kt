package io.github.nekzabirov.affiliate.clients

import io.github.nekzabirov.affiliate.config.AppProperties
import currency.v1.Currency.ConvertUnitsRequest
import currency.v1.CurrencyServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * The currency service (Currency-Exchange-Service, `ConvertUnits`): the one way an amount of a fact enters this app. Facts carry nano units of the player's
 * currency; every table holds nano units of PARTNER_CURRENCY, so an amount is exchanged once, on the way in, at
 * the rate of that moment. Never call it inside a database transaction: it is a remote call.
 */
@Component
class CurrencyClient(channel: ManagedChannel, properties: AppProperties) {

    private val stub = CurrencyServiceGrpc.newBlockingStub(channel)

    private val target = properties.partnerCurrency

    /** [units] nano of [currency] as nano of the partner currency; throws [CurrencyConversionException]. */
    fun toPartnerCurrency(units: Long, currency: String): Long = convert(units, currency, target)

    /** [units] nano of [currency] as nano of [toCurrency]; throws [CurrencyConversionException]. */
    fun convert(units: Long, currency: String, toCurrency: String): Long {
        if (currency == toCurrency) {
            return units
        }
        val request = ConvertUnitsRequest.newBuilder()
            .setUnits(units)
            .setCurrency(currency)
            .setToCurrency(toCurrency)
            .build()
        return try {
            convert(request)
        } catch (failure: StatusRuntimeException) {
            // The service names the reason (UNKNOWN_CURRENCY, RATE_MISSING, ...) in a trailer; the status code
            // alone would say FAILED_PRECONDITION for both of the common ones.
            val reason = failure.trailers?.get(ERROR_CODE) ?: failure.status.code.name
            throw CurrencyConversionException("The currency service did not convert $currency to $toCurrency: $reason", failure)
        }
    }

    /**
     * One more attempt on UNAVAILABLE, and only on it: a connection that sat idle across the NAT comes back
     * silently gone, the first call on it fails and reconnects, and the second succeeds. Reading a rate twice is
     * harmless. Anything else - a refusal, the deadline - is final.
     */
    private fun convert(request: ConvertUnitsRequest): Long = try {
        call(request)
    } catch (failure: StatusRuntimeException) {
        if (failure.status.code != Status.Code.UNAVAILABLE) throw failure
        call(request)
    }

    /** The service answers from memory, so anything slower than the deadline is not answering at all. */
    private fun call(request: ConvertUnitsRequest): Long =
        stub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS).convertUnits(request).units

    private companion object {

        const val DEADLINE_SECONDS = 3L

        val ERROR_CODE: Metadata.Key<String> = Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER)
    }
}
