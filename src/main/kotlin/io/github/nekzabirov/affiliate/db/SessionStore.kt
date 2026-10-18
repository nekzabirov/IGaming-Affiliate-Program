package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.Account
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Instant

/** The `session` table. Rows are keyed by the SHA-256 of the cookie value; the raw token is never stored. */
@Component
class SessionStore(private val jdbc: JdbcClient) {

    fun insert(tokenHash: String, accountId: Long, expiresAt: Instant) {
        jdbc.sql("INSERT INTO session (token_hash, account_id, expires_at) VALUES (:tokenHash, :accountId, :expiresAt)")
            .param("tokenHash", tokenHash)
            .param("accountId", accountId)
            .param("expiresAt", expiresAt.utc())
            .update()
    }

    /** The account behind a live session, in the one query every API request pays; null when absent or expired. */
    fun findAccount(tokenHash: String): Account? =
        jdbc.sql(
            """
            SELECT a.id, a.login, a.role, a.active, a.balance, a.created_at, a.updated_at
            FROM session s JOIN account a ON a.id = s.account_id
            WHERE s.token_hash = :tokenHash AND s.expires_at > now()
            """,
        )
            .param("tokenHash", tokenHash)
            .query { rs, _ -> AccountStore.read(rs) }
            .optional()
            .orElse(null)

    fun delete(tokenHash: String) {
        jdbc.sql("DELETE FROM session WHERE token_hash = :tokenHash")
            .param("tokenHash", tokenHash)
            .update()
    }

    /**
     * Deletes up to [batch] expired sessions and answers how many went; the caller repeats until a short batch.
     * Bounded so a backlog never becomes one long delete holding its locks.
     */
    fun deleteExpired(batch: Int): Int =
        jdbc.sql(
            """
            DELETE FROM session WHERE token_hash IN (
                SELECT token_hash FROM session WHERE expires_at <= now() LIMIT :batch
            )
            """,
        )
            .param("batch", batch)
            .update()
}
