package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.clients.CurrencyClient
import io.github.nekzabirov.affiliate.db.PostbackStore
import io.github.nekzabirov.affiliate.model.Postback
import io.github.nekzabirov.affiliate.postback.PostbackAdapters
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import io.github.nekzabirov.affiliate.support.CapturedLog
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** What the dispatcher leaves behind when the app stops while partners' trackers are slow. */
class PostbackShutdownTest {

    @Test
    fun `stopping with deliveries still queued and in flight says how many never went out`() {
        val trackers = CountDownLatch(1)
        val busy = CountDownLatch(THREADS)
        val postbacks = mock(PostbackStore::class.java)
        `when`(postbacks.findActiveByCompany(anyLong())).thenAnswer {
            busy.countDown()
            // A request stuck on a tracker's socket: an interrupt does not end it.
            while (true) {
                try {
                    trackers.await()
                    break
                } catch (_: InterruptedException) {
                    continue
                }
            }
            emptyList<Postback>()
        }
        val dispatcher = PostbackDispatcher(postbacks, mock(PostbackAdapters::class.java), mock(CloseableHttpClient::class.java), mock(CurrencyClient::class.java))
        try {
            CapturedLog(PostbackDispatcher::class.java).use { log ->
                (1L..THREADS + QUEUED).forEach { dispatcher.dispatch(it, EVENT) }
                assertThat(busy.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue()

                dispatcher.destroy()

                val line = log.lines("postback.dropped").single()
                assertThat(line["dropped"]).isEqualTo(QUEUED.toInt())
                assertThat(line["abandoned"]).isEqualTo(THREADS)
            }
        } finally {
            trackers.countDown()
        }
    }

    private companion object {

        /** The dispatcher's pool size: this many deliveries are in flight, the rest wait in its queue. */
        const val THREADS = 4

        const val QUEUED = 3L

        const val WAIT_SECONDS = 10L

        val EVENT = PostbackEvent(PostbackEvent.Kind.REGISTRATION, playerId = 7, clickId = "abc", factId = null, amount = 0, currency = PostbackDispatcher.POSTBACK_CURRENCY)
    }
}
