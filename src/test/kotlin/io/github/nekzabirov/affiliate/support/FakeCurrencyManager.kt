package io.github.nekzabirov.affiliate.support

import currency.v1.Currency.ConvertUnitsRequest
import currency.v1.Currency.UnitsResponse
import currency.v1.CurrencyServiceGrpc
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Server
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * The currency service (Currency-Exchange-Service) in this JVM, at fixed rates: the production client talks to it over a real gRPC channel,
 * exactly as it talks to the real one, so the tests never depend on the network or on today's rates.
 *
 * A test can make the next calls fail ([failNext]) or never answer ([silent]); [reset] puts it back.
 */
class FakeCurrencyManager : AutoCloseable {

    private val name = InProcessServerBuilder.generateName()

    private val failures = ConcurrentLinkedQueue<Pair<Status, String?>>()

    private val server: Server = InProcessServerBuilder.forName(name)
        .directExecutor()
        .addService(Service())
        .build()
        .start()

    /** Calls received, failed ones included. */
    val calls = AtomicInteger()

    /** When set, calls are received and never answered, so only the client's deadline ends them. */
    @Volatile
    var silent = false

    fun channel(): ManagedChannel = InProcessChannelBuilder.forName(name).directExecutor().build()

    /** The next call fails with [status], carrying [errorCode] in `x-error-code` as the real service does. */
    fun failNext(status: Status, errorCode: String? = null) {
        failures += status to errorCode
    }

    fun reset() {
        failures.clear()
        silent = false
        calls.set(0)
    }

    override fun close() {
        server.shutdownNow()
    }

    private inner class Service : CurrencyServiceGrpc.CurrencyServiceImplBase() {

        override fun convertUnits(request: ConvertUnitsRequest, response: StreamObserver<UnitsResponse>) {
            calls.incrementAndGet()
            val failure = failures.poll()
            if (failure != null) {
                response.onError(failure.first.asRuntimeException(trailers(failure.second)))
                return
            }
            if (silent) return
            val from = RATES[request.currency]
            val to = RATES[request.toCurrency]
            if (from == null || to == null) {
                response.onError(Status.FAILED_PRECONDITION.withDescription("currency is not in the registry").asRuntimeException(trailers(UNKNOWN_CURRENCY)))
                return
            }
            val units = BigDecimal.valueOf(request.units).multiply(to).divide(from, 0, RoundingMode.HALF_UP).longValueExact()
            response.onNext(UnitsResponse.newBuilder().setUnits(units).build())
            response.onCompleted()
        }

        private fun trailers(errorCode: String?) = Metadata().apply {
            if (errorCode != null) put(ERROR_CODE, errorCode)
        }
    }

    companion object {

        /** How much of each currency one USDT is worth: 40 UAH, half a euro. */
        val RATES = mapOf(
            "USDT" to BigDecimal.ONE,
            "UAH" to BigDecimal("40"),
            "EUR" to BigDecimal("0.5"),
        )

        const val UNKNOWN_CURRENCY = "UNKNOWN_CURRENCY"

        private val ERROR_CODE: Metadata.Key<String> = Metadata.Key.of("x-error-code", Metadata.ASCII_STRING_MARSHALLER)
    }
}
