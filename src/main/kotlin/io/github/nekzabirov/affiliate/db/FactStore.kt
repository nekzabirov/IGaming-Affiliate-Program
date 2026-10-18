package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.ProcessedFact
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.Types
import java.time.Instant
import java.time.LocalDate

/**
 * The `processed_fact` table: delivery is at-least-once, so every fact is recorded in the same transaction as
 * its effects, and a fact that is already here changes nothing the second time.
 */
@Component
class FactStore(private val jdbc: JdbcClient) {

    /**
     * Records [fact] as acted on; false when it already was. Two deliveries of one fact racing on two pods meet
     * on the primary key: the second waits for the first to commit and then finds its row.
     */
    fun insert(fact: ProcessedFact): Boolean =
        jdbc.sql(
            """
            INSERT INTO processed_fact (key, turnover, commission, commission_source, company_id, player_id, day)
            VALUES (:key, :turnover, :commission, :source, :companyId, :playerId, :day)
            ON CONFLICT (key) DO NOTHING
            """,
        )
            .param("key", fact.key)
            .param("turnover", fact.turnover)
            .param("commission", fact.commission)
            .param("source", fact.commissionSource, Types.VARCHAR)
            .param("companyId", fact.companyId, Types.BIGINT)
            .param("playerId", fact.playerId, Types.BIGINT)
            .param("day", fact.day)
            .update() == 1

    /** Rewrites what [fact] records, in the transaction that inserted it, once its effects are known. */
    fun update(fact: ProcessedFact) {
        jdbc.sql(
            """
            UPDATE processed_fact
            SET turnover = :turnover, commission = :commission, commission_source = :source,
                company_id = :companyId, player_id = :playerId, day = :day
            WHERE key = :key
            """,
        )
            .param("key", fact.key)
            .param("turnover", fact.turnover)
            .param("commission", fact.commission)
            .param("source", fact.commissionSource, Types.VARCHAR)
            .param("companyId", fact.companyId, Types.BIGINT)
            .param("playerId", fact.playerId, Types.BIGINT)
            .param("day", fact.day)
            .update()
    }

    fun exists(key: String): Boolean =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM processed_fact WHERE key = :key)")
            .param("key", key)
            .query(Boolean::class.javaObjectType)
            .single()

    fun find(key: String): ProcessedFact? =
        jdbc.sql("SELECT key, turnover, commission, commission_source, company_id, player_id, day FROM processed_fact WHERE key = :key")
            .param("key", key)
            .query { rs, _ ->
                ProcessedFact(
                    key = rs.getString("key"),
                    turnover = rs.getLong("turnover"),
                    commission = rs.getLong("commission"),
                    commissionSource = rs.getString("commission_source"),
                    companyId = rs.longOrNull("company_id"),
                    playerId = rs.longOrNull("player_id"),
                    day = rs.localDate("day"),
                )
            }
            .optional()
            .orElse(null)

    /**
     * Deletes up to [batch] facts whose key starts with one of [prefixes] and that were recorded before [before],
     * and answers how many went; the caller repeats until a short batch. SKIP LOCKED lets replicas running the
     * same job at once share the rows instead of queueing behind each other.
     */
    fun deleteRecordedBefore(prefixes: Collection<String>, before: Instant, batch: Int): Int {
        val matches = prefixes.indices.joinToString(" OR ") { "key LIKE :prefix$it" }
        return jdbc.sql(
            """
            DELETE FROM processed_fact WHERE key IN (
                SELECT key FROM processed_fact
                WHERE created_at < :before AND ($matches)
                LIMIT :batch
                FOR UPDATE SKIP LOCKED
            )
            """,
        )
            .params(prefixes.withIndex().associate { (index, prefix) -> "prefix$index" to "$prefix%" })
            .param("before", before.utc())
            .param("batch", batch)
            .update()
    }
}
