package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.errors.TooManyRequestsException
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Brute-force and sign-up throttling, in memory and per pod: failed logins per login and per client address,
 * and registrations per client address. With a handful of replicas the effective limit is that many times
 * higher, which still turns password guessing into a crawl.
 *
 * An attempt takes its slot when it is checked, before any bcrypt work, in the same step that counts the slots
 * already taken. Requests run on virtual threads with nothing in front of them, so a check that only looked would
 * let a whole burst through before the first of its failures was written down. A slot is given back when the
 * attempt did not end in what is being counted - a login that got in or was refused for another reason, a sign-up
 * that created no account - so only failures and new accounts use the allowance up.
 */
@Component
class RateLimiter {

    private val loginFailuresByLogin = SlidingWindow(LOGIN_FAILURES_PER_LOGIN, Duration.ofMinutes(LOGIN_WINDOW_MINUTES))

    private val loginFailuresByIp = SlidingWindow(LOGIN_FAILURES_PER_IP, Duration.ofMinutes(LOGIN_WINDOW_MINUTES))

    private val registrationsByIp = SlidingWindow(REGISTRATIONS_PER_IP, Duration.ofHours(REGISTRATION_WINDOW_HOURS))

    /**
     * Takes a slot of the login's allowance and one of the address's for this attempt, or refuses when either is
     * used up. A wrong password keeps both; the caller settles every other outcome with [loginSucceeded] or
     * [loginAbandoned].
     */
    fun checkLogin(login: String, ip: String) {
        if (!loginFailuresByLogin.tryAcquire(login)) {
            throw TooManyRequestsException()
        }
        if (!loginFailuresByIp.tryAcquire(ip)) {
            loginFailuresByLogin.release(login)
            throw TooManyRequestsException()
        }
    }

    /** The owner got in, so the guesses against this login are over; the address's slot was not a failure. */
    fun loginSucceeded(login: String, ip: String) {
        loginFailuresByLogin.clear(login)
        loginFailuresByIp.release(ip)
    }

    /** The attempt ended in neither a wrong password nor a sign-in (a blocked account, a failure of ours). */
    fun loginAbandoned(login: String, ip: String) {
        loginFailuresByLogin.release(login)
        loginFailuresByIp.release(ip)
    }

    /** Takes a slot of the address's sign-ups, or refuses; [registrationFailed] gives it back. */
    fun checkRegistration(ip: String) {
        if (!registrationsByIp.tryAcquire(ip)) {
            throw TooManyRequestsException()
        }
    }

    /** The sign-up created no account (invalid input, a taken login, a failure of ours): only accounts count. */
    fun registrationFailed(ip: String) {
        registrationsByIp.release(ip)
    }

    /**
     * At most [limit] events per key within [window]. A key's timestamps are only touched inside the map's
     * per-key compute, so the check and the count are one step and concurrent requests on one key never lose
     * or overtake a count.
     */
    private class SlidingWindow(private val limit: Int, window: Duration) {

        private val windowNanos = window.toNanos()

        private val events = ConcurrentHashMap<String, ArrayDeque<Long>>()

        private val writes = AtomicInteger()

        /** Counts one more event for [key] if the window has room for it; false, and nothing counted, when it has not. */
        fun tryAcquire(key: String): Boolean {
            val now = System.nanoTime()
            if (writes.incrementAndGet() % SWEEP_EVERY == 0) {
                sweep(now)
            }
            // Under a flood of distinct keys memory stays bounded: new keys go uncounted until old ones expire.
            if (events.size >= MAX_KEYS && !events.containsKey(key)) {
                return true
            }
            var acquired = false
            events.compute(key) { _, times ->
                (times ?: ArrayDeque()).also {
                    prune(it, now)
                    if (it.size < limit) {
                        it.addLast(now)
                        acquired = true
                    }
                }.ifEmpty { null }
            }
            return acquired
        }

        /**
         * Un-counts one event of [key], taken by [tryAcquire] for an attempt that, in the end, does not count. The
         * newest goes: the slots of attempts still in flight are all recent, and which of them expires first is
         * a matter of milliseconds.
         */
        fun release(key: String) {
            events.computeIfPresent(key) { _, times ->
                times.removeLastOrNull()
                times.ifEmpty { null }
            }
        }

        fun clear(key: String) {
            events.remove(key)
        }

        private fun prune(times: ArrayDeque<Long>, now: Long) {
            while (times.isNotEmpty() && now - times.first() >= windowNanos) {
                times.removeFirst()
            }
        }

        /** Forgets keys whose events all expired, so one-off logins and addresses do not pile up. */
        private fun sweep(now: Long) {
            events.keys.forEach { key ->
                events.computeIfPresent(key) { _, times ->
                    prune(times, now)
                    times.ifEmpty { null }
                }
            }
        }
    }

    private companion object {

        const val LOGIN_FAILURES_PER_LOGIN = 10

        const val LOGIN_FAILURES_PER_IP = 30

        const val LOGIN_WINDOW_MINUTES = 15L

        const val REGISTRATIONS_PER_IP = 5

        const val REGISTRATION_WINDOW_HOURS = 1L

        const val SWEEP_EVERY = 1024

        const val MAX_KEYS = 100_000
    }
}
