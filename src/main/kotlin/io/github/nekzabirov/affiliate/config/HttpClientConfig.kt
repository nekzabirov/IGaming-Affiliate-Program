package io.github.nekzabirov.affiliate.config

import io.github.nekzabirov.affiliate.postback.GuardedDnsResolver
import io.github.nekzabirov.affiliate.postback.UrlGuard
import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The client that sends postbacks to partner trackers - addresses a partner typed in, so it trusts nothing:
 * every name resolves through the SSRF guard, redirects are not followed (a 302 to an internal address would
 * walk straight past the guard's check of the original URL), and nothing is retried or remembered between
 * requests. Short timeouts: a slow tracker must not hold the bounded dispatcher pool.
 */
@Configuration
class HttpClientConfig {

    @Bean
    fun urlGuard(): UrlGuard = UrlGuard()

    @Bean(destroyMethod = "close")
    fun postbackHttpClient(urlGuard: UrlGuard): CloseableHttpClient = postbackClient(GuardedDnsResolver(urlGuard))

    companion object {

        /** The postback client over [resolver]; production always passes the guarded one. */
        fun postbackClient(resolver: DnsResolver): CloseableHttpClient {
            val connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setDefaultConnectionConfig(
                    ConnectionConfig.custom()
                        .setConnectTimeout(CONNECT_TIMEOUT)
                        .setSocketTimeout(RESPONSE_TIMEOUT)
                        .build(),
                )
                .setMaxConnTotal(MAX_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_TRACKER)
                .build()
            return HttpClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(
                    RequestConfig.custom()
                        .setConnectionRequestTimeout(CONNECT_TIMEOUT)
                        .setResponseTimeout(RESPONSE_TIMEOUT)
                        .setRedirectsEnabled(false)
                        .build(),
                )
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .setUserAgent(USER_AGENT)
                .build()
        }

        private val CONNECT_TIMEOUT: Timeout = Timeout.ofSeconds(3)

        private val RESPONSE_TIMEOUT: Timeout = Timeout.ofSeconds(10)

        /** Matches the dispatcher's threads: there is never more than one request per thread in flight. */
        private const val MAX_CONNECTIONS = 4

        private const val MAX_CONNECTIONS_PER_TRACKER = 4

        const val USER_AGENT = "affiliate-app-postback/1.0"
    }
}
