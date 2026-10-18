package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.Postback
import io.github.nekzabirov.affiliate.model.PostbackType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant

/** The `postback` table: a company's trackers and the outcome of the last request sent to each. */
@Component
class PostbackStore(private val jdbc: JdbcClient) {

    fun countByCompany(companyId: Long): Int =
        jdbc.sql("SELECT count(*) FROM postback WHERE company_id = :companyId")
            .param("companyId", companyId)
            .query(Int::class.javaObjectType)
            .single()

    fun insert(companyId: Long, type: PostbackType, url: String): Postback =
        jdbc.sql("INSERT INTO postback (company_id, type, url) VALUES (:companyId, :type, :url) RETURNING $COLUMNS")
            .param("companyId", companyId)
            .param("type", type.name)
            .param("url", url)
            .query { rs, _ -> read(rs) }
            .single()

    fun findByCompany(companyId: Long): List<Postback> =
        jdbc.sql("SELECT $COLUMNS FROM postback WHERE company_id = :companyId ORDER BY id")
            .param("companyId", companyId)
            .query { rs, _ -> read(rs) }
            .list()

    fun findActiveByCompany(companyId: Long): List<Postback> =
        jdbc.sql("SELECT $COLUMNS FROM postback WHERE company_id = :companyId AND active ORDER BY id")
            .param("companyId", companyId)
            .query { rs, _ -> read(rs) }
            .list()

    /** The postback only when it belongs to [companyId], so an id from another company's list addresses nothing. */
    fun find(companyId: Long, id: Long): Postback? =
        jdbc.sql("SELECT $COLUMNS FROM postback WHERE id = :id AND company_id = :companyId")
            .param("id", id)
            .param("companyId", companyId)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    /**
     * Writes the switch and the URL. [forgetLastAttempt] clears the last-attempt fields: after a URL change they
     * describe a tracker this postback no longer points at.
     */
    fun update(id: Long, active: Boolean, url: String, forgetLastAttempt: Boolean): Postback =
        jdbc.sql(
            """
            UPDATE postback SET
                active = :active,
                url = :url,
                last_sent_at = CASE WHEN :forget THEN NULL ELSE last_sent_at END,
                last_status = CASE WHEN :forget THEN NULL ELSE last_status END,
                last_error = CASE WHEN :forget THEN NULL ELSE last_error END,
                updated_at = now()
            WHERE id = :id
            RETURNING $COLUMNS
            """,
        )
            .param("id", id)
            .param("active", active)
            .param("url", url)
            .param("forget", forgetLastAttempt)
            .query { rs, _ -> read(rs) }
            .single()

    fun delete(companyId: Long, id: Long): Boolean =
        jdbc.sql("DELETE FROM postback WHERE id = :id AND company_id = :companyId")
            .param("id", id)
            .param("companyId", companyId)
            .update() == 1

    /** The outcome of one attempt: [status] is null when no response came, [error] null after a delivery. */
    fun recordAttempt(id: Long, sentAt: Instant, status: Int?, error: String?) {
        jdbc.sql("UPDATE postback SET last_sent_at = :sentAt, last_status = :status, last_error = :error WHERE id = :id")
            .param("id", id)
            .param("sentAt", sentAt.utc())
            .param("status", status, Types.INTEGER)
            .param("error", error?.take(ERROR_MAX_LENGTH), Types.VARCHAR)
            .update()
    }

    companion object {

        const val COLUMNS =
            "id, company_id, type, url, active, last_sent_at, last_status, last_error, created_at, updated_at"

        /** postback.last_error is VARCHAR(300). */
        const val ERROR_MAX_LENGTH = 300

        fun read(rs: ResultSet) = Postback(
            id = rs.getLong("id"),
            companyId = rs.getLong("company_id"),
            type = PostbackType.valueOf(rs.getString("type")),
            url = rs.getString("url"),
            active = rs.getBoolean("active"),
            lastSentAt = rs.instantOrNull("last_sent_at"),
            lastStatus = rs.intOrNull("last_status"),
            lastError = rs.getString("last_error"),
            createdAt = rs.instant("created_at"),
            updatedAt = rs.instant("updated_at"),
        )
    }
}
