package io.github.nekzabirov.affiliate.config

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.grpc.MetricCollectingClientInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

/**
 * The channel to the currency service (Currency-Exchange-Service). It usually stands in another network behind a
 * TLS name, so the connection crosses a NAT and a load balancer that both drop idle connections without telling
 * anyone.
 */
@Configuration
class CurrencyConfig {

    @Bean(destroyMethod = "shutdown")
    fun currencyChannel(properties: AppProperties, metrics: MeterRegistry): ManagedChannel {
        val currency = properties.currency
        val builder = NettyChannelBuilder.forAddress(currency.host, currency.port)
        if (currency.plaintext) {
            builder.usePlaintext()
        } else {
            // The JDK's TLS, never the BoringSSL that grpc-netty-shaded bundles: loading that one in the alpine
            // image kills the JVM with a SIGSEGV (main() also switches it off before any Netty class loads).
            // configure() sets the protocol negotiation for that provider; forClient() would pick it for whichever
            // provider it finds loadable, and the JDK refuses the OpenSSL one.
            builder.sslContext(GrpcSslContexts.configure(SslContextBuilder.forClient(), SslProvider.JDK).build())
        }
        return builder
            // Pings keep the connection known to be alive; without them the first call after a quiet spell goes
            // into a connection the NAT has already forgotten.
            .keepAliveTime(KEEP_ALIVE_SECONDS, TimeUnit.SECONDS)
            .keepAliveTimeout(KEEP_ALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .intercept(MetricCollectingClientInterceptor(metrics))
            .build()
    }

    private companion object {

        const val KEEP_ALIVE_SECONDS = 30L

        const val KEEP_ALIVE_TIMEOUT_SECONDS = 10L
    }
}
