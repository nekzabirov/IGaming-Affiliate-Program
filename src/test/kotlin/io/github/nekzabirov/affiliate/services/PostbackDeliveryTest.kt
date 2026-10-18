package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.clients.CurrencyClient
import io.github.nekzabirov.affiliate.config.HttpClientConfig
import io.github.nekzabirov.affiliate.db.PostbackStore
import io.github.nekzabirov.affiliate.postback.PostbackAdapters
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import io.github.nekzabirov.affiliate.support.IntegrationTest
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.hc.client5.http.DnsResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * What the dispatcher makes of each answer a tracker can give, against a real HTTP server. The production
 * client is used as built, except for its resolver: the guard would rightly refuse the loopback this server
 * listens on, and the guard has tests of its own.
 */
class PostbackDeliveryTest : IntegrationTest() {

    @Autowired
    private lateinit var store: PostbackStore

    @Autowired
    private lateinit var adapters: PostbackAdapters

    @Autowired
    private lateinit var currency: CurrencyClient

    private lateinit var server: HttpServer

    private lateinit var dispatcher: PostbackDispatcher

    private val requests = CopyOnWriteArrayList<String>()

    private val agents = CopyOnWriteArrayList<String>()

    private val loopback = object : DnsResolver {

        override fun resolve(host: String): Array<InetAddress> = arrayOf(InetAddress.getLoopbackAddress())

        override fun resolveCanonicalHostname(host: String): String = host
    }

    @BeforeEach
    fun tracker() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.executor = Executors.newVirtualThreadPerTaskExecutor()
        server.createContext("/ok/postback") { answer(it, 200) }
        server.createContext("/missing/postback") { answer(it, 404) }
        server.createContext("/challenged/postback") {
            it.responseHeaders.add("cf-mitigated", "challenge")
            answer(it, 403)
        }
        server.createContext("/moved/postback") {
            it.responseHeaders.add("Location", "${base()}/ok/postback")
            answer(it, 302)
        }
        server.createContext("/endless/postback", ::endless)
        server.start()
        dispatcher = PostbackDispatcher(store, adapters, HttpClientConfig.postbackClient(loopback), currency)
    }

    @AfterEach
    fun stop() {
        dispatcher.destroy()
        server.stop(0)
    }

    @Test
    fun `every answer is recorded on its postback, and only 2xx counts as delivered`() {
        val session = api.register(uniqueLogin("yuri"))
        api.post("/api/companies", mapOf("identity" to "delivered", "type" to "CPA", "amount" to "10"), session)
        val company = companyId("delivered")
        val ok = insertPostback(company, "${base()}/ok/postback")
        val missing = insertPostback(company, "${base()}/missing/postback")
        val challenged = insertPostback(company, "${base()}/challenged/postback")
        val moved = insertPostback(company, "${base()}/moved/postback")
        val endless = insertPostback(company, "${base()}/endless/postback")
        val started = System.nanoTime()

        dispatcher.dispatch(company, event())

        assertThat(awaitAttempt(ok)).isEqualTo(200 to null)
        assertThat(awaitAttempt(missing)).isEqualTo(404 to "HTTP 404")
        assertThat(awaitAttempt(challenged)).isEqualTo(403 to "Cloudflare challenge")
        // The redirect is reported, not followed: /ok was asked exactly once, by its own postback.
        assertThat(awaitAttempt(moved)).isEqualTo(302 to "HTTP 302")
        // A body that never ends is never read: the status is enough, and the thread is free again at once.
        assertThat(awaitAttempt(endless)).isEqualTo(200 to null)
        assertThat((System.nanoTime() - started) / NANOS_PER_SECOND).isLessThan(BODY_NEVER_READ_SECONDS)

        assertThat(requests.filter { it.startsWith("/ok/") }).containsExactly("/ok/postback?subid=abc&status=registration")
        assertThat(agents).allMatch { it == HttpClientConfig.USER_AGENT }
    }

    private fun base() = "http://tracker.example.com:${server.address.port}"

    private fun answer(exchange: HttpExchange, status: Int) {
        record(exchange)
        exchange.sendResponseHeaders(status, NO_BODY)
        exchange.close()
    }

    /** Streams a body until the client goes away (or a minute passes, so a broken test cannot hang the run). */
    private fun endless(exchange: HttpExchange) {
        record(exchange)
        exchange.sendResponseHeaders(200, CHUNKED)
        val until = System.nanoTime() + STREAM_LIMIT_NANOS
        try {
            exchange.responseBody.use { body ->
                while (System.nanoTime() < until) {
                    body.write(ByteArray(CHUNK_BYTES))
                    body.flush()
                    Thread.sleep(CHUNK_PAUSE_MILLIS)
                }
            }
        } catch (_: IOException) {
            // The client hung up, which is what the dispatcher is supposed to do.
        }
    }

    private fun record(exchange: HttpExchange) {
        requests += exchange.requestURI.toString()
        agents += exchange.requestHeaders.getFirst("User-Agent")
    }

    private fun event() =
        PostbackEvent(PostbackEvent.Kind.REGISTRATION, playerId = 8, clickId = "abc", factId = null, amount = 0, currency = PostbackDispatcher.POSTBACK_CURRENCY)

    private companion object {

        const val NO_BODY = -1L

        const val CHUNKED = 0L

        const val CHUNK_BYTES = 1024

        const val CHUNK_PAUSE_MILLIS = 50L

        const val STREAM_LIMIT_NANOS = 60_000_000_000L

        const val NANOS_PER_SECOND = 1_000_000_000L

        /** Well under the 20 s deadline that would cut the request otherwise. */
        const val BODY_NEVER_READ_SECONDS = 8L
    }
}
