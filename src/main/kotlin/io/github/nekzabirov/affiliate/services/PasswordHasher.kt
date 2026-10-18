package io.github.nekzabirov.affiliate.services

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

/**
 * bcrypt on a bounded number of hashes at a time.
 *
 * Requests run on virtual threads, and a virtual thread busy hashing is never preempted: a burst of logins
 * would pin every carrier thread for the length of a hash and stall all other requests. Two permits leave the
 * rest of the carriers to everything else; a request waiting for a permit unmounts and costs nothing.
 */
@Component
class PasswordHasher {

    private val encoder = BCryptPasswordEncoder(COST)

    private val permits = Semaphore(PARALLEL_HASHES)

    /** A real hash to compare against when the login is unknown. */
    private val decoy: String by lazy { bounded { checkNotNull(encoder.encode(DECOY_PASSWORD)) } }

    fun hash(password: String): String = bounded { checkNotNull(encoder.encode(password)) }

    fun matches(password: String, hash: String): Boolean = bounded { encoder.matches(password, hash) }

    /** Spends one comparison on nothing, so an unknown login takes as long to refuse as a wrong password. */
    fun burn() {
        matches(DECOY_ATTEMPT, decoy)
    }

    private fun <T> bounded(work: () -> T): T {
        permits.acquire()
        try {
            return work()
        } finally {
            permits.release()
        }
    }

    private companion object {

        const val COST = 10

        const val PARALLEL_HASHES = 2

        const val DECOY_PASSWORD = "decoy-password-for-unknown-logins"

        const val DECOY_ATTEMPT = "not-the-decoy-password"
    }
}
