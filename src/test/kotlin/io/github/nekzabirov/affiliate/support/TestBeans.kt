package io.github.nekzabirov.affiliate.support

import io.github.nekzabirov.affiliate.clients.CurrencyClient
import io.github.nekzabirov.affiliate.db.PostbackStore
import io.github.nekzabirov.affiliate.postback.PostbackAdapters
import io.github.nekzabirov.affiliate.postback.UrlGuard
import io.grpc.ManagedChannel
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * What the tests swap in: the real guard over a fake resolver (the ranges are production's, only the names are
 * the test's), the currency service in-process at fixed rates (no test reaches the network), and the production
 * dispatcher with a memory of what it was asked to send.
 */
@TestConfiguration
class TestBeans {

    @Bean
    @Primary
    fun testUrlGuard(): UrlGuard = UrlGuard(FakeDns::lookup)

    @Bean(destroyMethod = "close")
    fun fakeCurrencyManager(): FakeCurrencyManager = FakeCurrencyManager()

    @Bean(destroyMethod = "shutdownNow")
    @Primary
    fun testCurrencyChannel(currency: FakeCurrencyManager): ManagedChannel = currency.channel()

    @Bean
    @Primary
    fun recordingDispatcher(postbacks: PostbackStore, adapters: PostbackAdapters, http: CloseableHttpClient, currency: CurrencyClient): RecordingDispatcher =
        RecordingDispatcher(postbacks, adapters, http, currency)
}
