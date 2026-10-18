package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.db.FactStore
import io.github.nekzabirov.affiliate.db.SessionStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * The daily clean-up. Plain batched DELETEs of rows nobody can use any more, so every replica may run it at the
 * same time: they share the work and none of them can delete a row twice. Each part fails on its own, so a broken
 * one never keeps the other from running.
 */
@Component
class Maintenance(
    private val sessions: SessionStore,
    private val facts: FactStore,
) {

    /** Once a day, at night in Europe and off the round minutes scheduled jobs usually take. */
    @Scheduled(cron = "0 17 3 * * *", zone = "UTC")
    fun daily() {
        purgeSessions()
        purgeSpinFacts()
    }

    private fun purgeSessions() {
        try {
            val deleted = inBatches { sessions.deleteExpired(BATCH) }
            log.atInfo()
                .addKeyValue("event", "maintenance.sessions_purged")
                .addKeyValue("deleted", deleted)
                .log("Expired sessions deleted")
        } catch (failure: Exception) {
            failed("sessions", "Deleting expired sessions failed", failure)
        }
    }

    /**
     * Forgets spins (stakes, their rollbacks, and the empty keys a rollback leaves for a stake it came before)
     * older than the replay window. The broker keeps a dead letter for 14 days, so no delivery of such a fact can
     * arrive after 15, and a casino rollback follows its stake within seconds. Sportsbook facts are kept: a bet
     * can be voided weeks after it was placed, and its rollback must still find what the bet added. Registrations
     * and invoices are kept too: they are few, and a settled invoice may be republished at any time.
     */
    private fun purgeSpinFacts() {
        try {
            val before = Instant.now().minus(REPLAY_WINDOW)
            val deleted = inBatches { facts.deleteRecordedBefore(AccrualService.PURGED_FACTS, before, BATCH) }
            log.atInfo()
                .addKeyValue("event", "maintenance.facts_purged")
                .addKeyValue("deleted", deleted)
                .log("Facts past the replay window deleted")
        } catch (failure: Exception) {
            failed("facts", "Deleting old facts failed", failure)
        }
    }

    /** Repeats [batch] until it deletes less than a full batch; answers the total. */
    private fun inBatches(batch: () -> Int): Int {
        var deleted = 0
        do {
            val count = batch()
            deleted += count
        } while (count == BATCH)
        return deleted
    }

    private fun failed(job: String, message: String, failure: Exception) {
        log.atError()
            .addKeyValue("event", "maintenance.failed")
            .addKeyValue("job", job)
            .setCause(failure)
            .log(message)
    }

    private companion object {

        val log = LoggerFactory.getLogger(Maintenance::class.java)

        const val BATCH = 5000

        val REPLAY_WINDOW: Duration = Duration.ofDays(15)
    }
}
