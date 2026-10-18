package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.Company
import io.github.nekzabirov.affiliate.model.CompanyStatus
import io.github.nekzabirov.affiliate.model.CompanyType
import io.github.nekzabirov.affiliate.model.OwnedCompany
import io.github.nekzabirov.affiliate.model.Owner
import io.github.nekzabirov.affiliate.model.Page
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.sql.ResultSet

/** The `company` table: a partner's campaigns, their terms and the review status only an admin changes. */
@Component
class CompanyStore(private val jdbc: JdbcClient) {

    /** A new company IN_REVIEW, or null when the identity is taken by anyone (the unique index decides). */
    fun insert(accountId: Long, identity: String, type: CompanyType, amount: BigDecimal): Company? =
        jdbc.sql(
            """
            INSERT INTO company (identity, account_id, type, amount) VALUES (:identity, :accountId, :type, :amount)
            ON CONFLICT (identity) DO NOTHING
            RETURNING $COLUMNS
            """,
        )
            .param("identity", identity)
            .param("accountId", accountId)
            .param("type", type.name)
            .param("amount", amount)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    fun findByAccount(accountId: Long): List<Company> =
        jdbc.sql("SELECT $COLUMNS FROM company WHERE account_id = :accountId ORDER BY created_at DESC, id DESC")
            .param("accountId", accountId)
            .query { rs, _ -> read(rs) }
            .list()

    /** The company only if [accountId] owns it: another partner's company is as absent as one that never existed. */
    fun findOwned(accountId: Long, identity: String): Company? =
        jdbc.sql("SELECT $COLUMNS FROM company WHERE identity = :identity AND account_id = :accountId")
            .param("identity", identity)
            .param("accountId", accountId)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    fun findByIdentity(identity: String): Company? =
        jdbc.sql("SELECT $COLUMNS FROM company WHERE identity = :identity")
            .param("identity", identity)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    fun findById(id: Long): Company? =
        jdbc.sql("SELECT $COLUMNS FROM company WHERE id = :id")
            .param("id", id)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    /** The company named [identity] with its owner, in one read: a fact accrues only for an ACTIVE company of an active owner. */
    fun findWithOwner(identity: String): OwnedCompany? =
        jdbc.sql("$OWNED_SELECT WHERE c.identity = :identity")
            .param("identity", identity)
            .query { rs, _ -> readOwned(rs) }
            .optional()
            .orElse(null)

    fun findWithOwner(id: Long): OwnedCompany? =
        jdbc.sql("$OWNED_SELECT WHERE c.id = :id")
            .param("id", id)
            .query { rs, _ -> readOwned(rs) }
            .optional()
            .orElse(null)

    /** Holds the company row until the transaction ends, so per-company limits are counted by one writer at a time. */
    fun lock(id: Long) {
        jdbc.sql("SELECT id FROM company WHERE id = :id FOR UPDATE")
            .param("id", id)
            .query(Long::class.javaObjectType)
            .optional()
    }

    fun update(id: Long, status: CompanyStatus, amount: BigDecimal): Company? =
        jdbc.sql("UPDATE company SET status = :status, amount = :amount, updated_at = now() WHERE id = :id RETURNING $COLUMNS")
            .param("id", id)
            .param("status", status.name)
            .param("amount", amount)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    /**
     * The admin review queue: IN_REVIEW first, then newest. [query] matches the identity or the owner's login
     * anywhere in them.
     */
    fun reviewPage(status: CompanyStatus?, query: String?, page: Int, size: Int): Page<OwnedCompany> {
        val conditions = buildList {
            if (status != null) add("c.status = :status")
            if (query != null) add("(c.identity LIKE :pattern ESCAPE '\\' OR a.login LIKE :pattern ESCAPE '\\')")
        }
        val where = if (conditions.isEmpty()) "" else conditions.joinToString(" AND ", prefix = "WHERE ")
        val params = buildMap {
            if (status != null) put("status", status.name)
            if (query != null) put("pattern", containsPattern(query.lowercase()))
        }
        val items = jdbc.sql(
            """
            $OWNED_SELECT
            $where
            ORDER BY CASE WHEN c.status = 'IN_REVIEW' THEN 0 ELSE 1 END, c.created_at DESC, c.id DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .params(params)
            .param("limit", size)
            .param("offset", offset(page, size))
            .query { rs, _ -> readOwned(rs) }
            .list()
        val total = jdbc.sql("SELECT count(*) FROM company c JOIN account a ON a.id = c.account_id $where")
            .params(params)
            .query(Long::class.javaObjectType)
            .single()
        return Page(items, page, size, total)
    }

    /** How many companies each of [accountIds] has; accounts without any are absent from the map. */
    fun countByAccounts(accountIds: Collection<Long>): Map<Long, Long> {
        if (accountIds.isEmpty()) return emptyMap()
        return jdbc.sql("SELECT account_id, count(*) AS companies FROM company WHERE account_id IN (:ids) GROUP BY account_id")
            .param("ids", accountIds)
            .query { rs, _ -> rs.getLong("account_id") to rs.getLong("companies") }
            .list()
            .toMap()
    }

    companion object {

        const val COLUMNS = "id, identity, account_id, type, amount, status, created_at, updated_at"

        /** [COLUMNS] off a join with `account`, which has columns of the same names. */
        private const val JOINED_COLUMNS =
            "c.id, c.identity, c.account_id, c.type, c.amount, c.status, c.created_at, c.updated_at"

        /** A company with its owner; [readOwned] reads it. */
        private const val OWNED_SELECT =
            "SELECT $JOINED_COLUMNS, a.login AS owner_login, a.active AS owner_active FROM company c JOIN account a ON a.id = c.account_id"

        private fun readOwned(rs: ResultSet): OwnedCompany {
            val company = read(rs)
            return OwnedCompany(company, Owner(company.accountId, rs.getString("owner_login"), rs.getBoolean("owner_active")))
        }

        fun read(rs: ResultSet) = Company(
            id = rs.getLong("id"),
            identity = rs.getString("identity"),
            accountId = rs.getLong("account_id"),
            type = CompanyType.valueOf(rs.getString("type")),
            amount = rs.getBigDecimal("amount"),
            status = CompanyStatus.valueOf(rs.getString("status")),
            createdAt = rs.instant("created_at"),
            updatedAt = rs.instant("updated_at"),
        )
    }
}
