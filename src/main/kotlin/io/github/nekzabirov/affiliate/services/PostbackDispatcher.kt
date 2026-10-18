package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.clients.CurrencyClient
import io.github.nekzabirov.affiliate.clients.CurrencyConversionException
import io.github.nekzabirov.affiliate.db.PostbackStore
import io.github.nekzabirov.affiliate.model.Postback
import io.github.nekzabirov.affiliate.postback.BlockedHostException
import io.github.nekzabirov.affiliate.postback.PostbackAdapters
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.ClassicHttpResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException

/**
 * Sends S2S postbacks to partner trackers, off the event-handling path.
 *
 * Event handling never waits on a partner's server: a delivery is queued on a small bounded pool and the
 * caller returns at once. When the queue is full the OLDEST waiting delivery is dropped with a WARN - a burst
 * must not grow memory without bound. Every outcome is recorded on the postback row (`last_*`, which the
 * cabinet shows) and logged; nothing is retried and nothing is thrown back (owner: "a crash is not scary, just
 * log"). The response body is never read: the status says whether the tracker took it.
 *
 * Amounts reach the trackers in [POSTBACK_CURRENCY]: converted here, once per event and only when a request will
 * actually go out, because Keitaro has no USDT and most facts concern companies with no postback at all.
 */
@Component
class PostbackDispatcher(
    private val postbacks: PostbackStore,
    private val adapters: PostbackAdapters,
    private val http: CloseableHttpClient,
    private val currency: CurrencyClient,
) : DisposableBean {

    private val pool = ThreadPoolExecutor(
        THREADS,
        THREADS,
        0,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUE_CAPACITY),
        named("postback"),
        ::dropOldest,
    )

    /** Cancels a request that outlives [TOTAL_DEADLINE_SECONDS]: the timeouts bound each read, not the whole exchange. */
    private val deadlines: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(named("postback-deadline"))

    /**
     * Tells every ACTIVE postback of company [companyId] about [event]. Call it only AFTER the transaction that
     * recorded the fact has committed, so a tracker never hears of a conversion that was rolled back, and outside
     * of any transaction. Returns at once; the requests run on the dispatcher's own pool.
     *
     * [PostbackEvent.clickId] is the referral's click id. When it is null no request is sent: each active
     * postback is marked "Skipped: the player arrived without click_id", because a tracker cannot attribute a
     * conversion without it. The caller's MDC (traceId) travels with the delivery into its log lines.
     */
    fun dispatch(companyId: Long, event: PostbackEvent) {
        val context = MDC.getCopyOfContextMap()
        try {
            pool.execute {
                if (context != null) MDC.setContextMap(context)
                try {
                    deliverAll(companyId, event)
                } finally {
                    MDC.clear()
                }
            }
        } catch (_: RejectedExecutionException) {
            log.atWarn()
                .addKeyValue("event", "postback.dropped")
                .addKeyValue("companyId", companyId)
                .addKeyValue("kind", event.kind)
                .log("Postback dropped: the dispatcher is shutting down")
        }
    }

    /**
     * Gives the queued deliveries a few seconds to go out while the app stops. What is still queued after that is
     * dropped, and a request still in flight - blocked on a slow tracker, which an interrupt does not wake - dies
     * with the JVM; neither leaves a trace on its postback row, so the count of both is the one line that tells.
     */
    override fun destroy() {
        pool.shutdown()
        if (!pool.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
            val dropped = pool.shutdownNow().size
            // The interrupt ends a delivery waiting on anything but a socket; a moment lets those record their end.
            pool.awaitTermination(INTERRUPT_GRACE_SECONDS, TimeUnit.SECONDS)
            log.atWarn()
                .addKeyValue("event", "postback.dropped")
                .addKeyValue("dropped", dropped)
                .addKeyValue("abandoned", pool.activeCount)
                .log("Postbacks not delivered: the app is stopping")
        }
        deadlines.shutdownNow()
    }

    private fun deliverAll(companyId: Long, event: PostbackEvent) {
        val targets = try {
            postbacks.findActiveByCompany(companyId)
        } catch (failure: Exception) {
            log.atError()
                .addKeyValue("event", "postback.failed")
                .addKeyValue("companyId", companyId)
                .addKeyValue("kind", event.kind)
                .setCause(failure)
                .log("Could not read the company's postbacks")
            return
        }
        if (targets.isEmpty()) return
        val priced = if (event.clickId == null) event else priced(event)
        if (priced == null) {
            targets.forEach { runCatching { postbacks.recordAttempt(it.id, Instant.now(), null, CONVERSION_FAILED) } }
            return
        }
        targets.forEach { deliver(it, priced) }
    }

    /** [event] with its amount in [POSTBACK_CURRENCY], or null (logged) when the currency service would not convert it. */
    private fun priced(event: PostbackEvent): PostbackEvent? = try {
        event.copy(amount = currency.convert(event.amount, event.currency, POSTBACK_CURRENCY), currency = POSTBACK_CURRENCY)
    } catch (failure: CurrencyConversionException) {
        log.atError()
            .addKeyValue("event", "postback.failed")
            .addKeyValue("playerId", event.playerId)
            .addKeyValue("kind", event.kind)
            .setCause(failure)
            .log("Postback amount not converted")
        null
    }

    private fun deliver(postback: Postback, event: PostbackEvent) {
        val started = System.nanoTime()
        val base = URI.create(postback.url)
        try {
            if (event.clickId == null) {
                postbacks.recordAttempt(postback.id, Instant.now(), null, SKIPPED_NO_CLICK_ID)
                line(postback, event, base, started).addKeyValue("event", "postback.skipped").log(SKIPPED_NO_CLICK_ID)
                return
            }
            val attempt = send(adapters.of(postback.type).build(postback.url, event))
            postbacks.recordAttempt(postback.id, Instant.now(), attempt.status, attempt.error)
            if (attempt.error == null) {
                line(postback, event, base, started)
                    .addKeyValue("event", "postback.sent")
                    .addKeyValue("status", attempt.status)
                    .log("Postback delivered")
            } else {
                log.atWarn()
                    .addKeyValue("event", "postback.failed")
                    .addKeyValue("postbackId", postback.id)
                    .addKeyValue("companyId", postback.companyId)
                    .addKeyValue("kind", event.kind)
                    .addKeyValue("host", base.host)
                    .addKeyValue("path", maskedPath(base))
                    .addKeyValue("status", attempt.status)
                    .addKeyValue("error", attempt.error)
                    .addKeyValue("durationMs", elapsedMs(started))
                    .log("Postback not delivered")
            }
        } catch (failure: Exception) {
            log.atError()
                .addKeyValue("event", "postback.failed")
                .addKeyValue("postbackId", postback.id)
                .addKeyValue("companyId", postback.companyId)
                .addKeyValue("kind", event.kind)
                .addKeyValue("host", base.host)
                .addKeyValue("path", maskedPath(base))
                .addKeyValue("durationMs", elapsedMs(started))
                .setCause(failure)
                .log("Postback delivery broke")
            runCatching { postbacks.recordAttempt(postback.id, Instant.now(), null, INTERNAL_ERROR) }
        }
    }

    private fun line(postback: Postback, event: PostbackEvent, base: URI, started: Long) =
        log.atInfo()
            .addKeyValue("postbackId", postback.id)
            .addKeyValue("companyId", postback.companyId)
            .addKeyValue("kind", event.kind)
            .addKeyValue("host", base.host)
            .addKeyValue("path", maskedPath(base))
            .addKeyValue("durationMs", elapsedMs(started))

    private fun send(uri: URI): Attempt {
        val request = HttpGet(uri)
        val deadline = deadlines.schedule({ request.cancel() }, TOTAL_DEADLINE_SECONDS, TimeUnit.SECONDS)
        return try {
            val response = http.executeOpen(null, request, null)
            try {
                judge(response)
            } finally {
                // Cancelling discards the connection before closing the response, which would otherwise drain
                // the body: a tracker that streams one without end would hold this thread for as long as it likes.
                request.cancel()
                try {
                    response.close()
                } catch (_: IOException) {
                    // The connection is already discarded; there is nothing left to release.
                }
            }
        } catch (_: BlockedHostException) {
            Attempt(null, "Blocked address")
        } catch (_: UnknownHostException) {
            Attempt(null, "Unknown host")
        } catch (_: InterruptedIOException) {
            Attempt(null, "Timed out")
        } catch (_: ConnectException) {
            Attempt(null, "Connection refused")
        } catch (_: SSLException) {
            Attempt(null, "TLS error")
        } catch (failure: IOException) {
            Attempt(null, "Network error: ${failure.javaClass.simpleName}")
        } finally {
            deadline.cancel(false)
        }
    }

    private fun judge(response: ClassicHttpResponse): Attempt {
        val status = response.code
        return when {
            status in SUCCESS -> Attempt(status, null)
            response.getFirstHeader(CF_MITIGATED)?.value.equals(CF_CHALLENGE, ignoreCase = true) ->
                Attempt(status, "Cloudflare challenge")
            else -> Attempt(status, "HTTP $status")
        }
    }

    /** The outcome of one request: [status] null when no response came, [error] null when it was delivered. */
    private data class Attempt(val status: Int?, val error: String?)

    /** Discard-oldest, with the WARN a silently lost delivery would deserve. */
    private fun dropOldest(task: Runnable, executor: ThreadPoolExecutor) {
        if (executor.isShutdown) {
            throw RejectedExecutionException("The postback dispatcher is shut down")
        }
        executor.queue.poll()
        log.atWarn()
            .addKeyValue("event", "postback.dropped")
            .addKeyValue("queued", executor.queue.size)
            .log("Postback queue is full, dropped the oldest waiting delivery")
        executor.execute(task)
    }

    companion object {

        /** Keitaro converts only from the currencies its rate source knows, and USDT is not one of them. */
        const val POSTBACK_CURRENCY = "EUR"

        private val log = LoggerFactory.getLogger(PostbackDispatcher::class.java)

        private const val THREADS = 4

        private const val QUEUE_CAPACITY = 1000

        private const val TOTAL_DEADLINE_SECONDS = 20L

        private const val SHUTDOWN_GRACE_SECONDS = 5L

        private const val INTERRUPT_GRACE_SECONDS = 1L

        private const val SKIPPED_NO_CLICK_ID = "Skipped: the player arrived without click_id"

        private const val INTERNAL_ERROR = "Internal error"

        private const val CONVERSION_FAILED = "Currency conversion failed"

        private const val CF_MITIGATED = "cf-mitigated"

        private const val CF_CHALLENGE = "challenge"

        private val SUCCESS = 200..299

        private const val NANOS_PER_MILLI = 1_000_000L

        private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / NANOS_PER_MILLI

        /** Host and path only, with every segment but the last masked: the Keitaro key sits in the path. */
        private fun maskedPath(uri: URI): String {
            val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            return segments.mapIndexed { index, segment -> if (index == segments.lastIndex) segment else "***" }
                .joinToString("/", prefix = "/")
        }

        private fun named(prefix: String): ThreadFactory {
            val counter = AtomicInteger()
            return ThreadFactory { runnable ->
                Thread(runnable, "$prefix-${counter.incrementAndGet()}").apply { isDaemon = true }
            }
        }
    }
}
