package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.CommissionEntry
import io.github.nekzabirov.affiliate.model.CompanyType
import io.github.nekzabirov.affiliate.model.Page
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDate

/** The `commission` table: the partner's earnings ledger, one row per idempotency source. */
@Component
class CommissionStore(private val jdbc: JdbcClient) {

    /**
     * A commission earned once per [source] (a player's CPA, one deposit's share); false when that source has
     * earned already, and nothing is written then.
     */
    fun insertOnce(accountId: Long, companyId: Long, playerId: Long, type: CompanyType, amount: Long, source: String, day: LocalDate): Boolean =
        jdbc.sql(
            """
            INSERT INTO commission (account_id, company_id, player_id, type, amount, source, day)
            VALUES (:accountId, :companyId, :playerId, :type, :amount, :source, :day)
            ON CONFLICT (source) DO NOTHING
            """,
        )
            .param("accountId", accountId)
            .param("companyId", companyId)
            .param("playerId", playerId)
            .param("type", type.name)
            .param("amount", amount)
            .param("source", source)
            .param("day", day)
            .update() == 1

    /**
     * Adds [amount] to the running commission of [source] - one row per company, player and day of turnover
     * share, rather than a ledger line per stake - creating it with the first stake of the day.
     */
    fun accrue(accountId: Long, companyId: Long, playerId: Long, type: CompanyType, amount: Long, source: String, day: LocalDate) {
        jdbc.sql(
            """
            INSERT INTO commission (account_id, company_id, player_id, type, amount, source, day)
            VALUES (:accountId, :companyId, :playerId, :type, :amount, :source, :day)
            ON CONFLICT (source) DO UPDATE SET amount = commission.amount + EXCLUDED.amount, updated_at = now()
            """,
        )
            .param("accountId", accountId)
            .param("companyId", companyId)
            .param("playerId", playerId)
            .param("type", type.name)
            .param("amount", amount)
            .param("source", source)
            .param("day", day)
            .update()
    }

    /** Takes [amount] back out of the commission row [source]; answers the account it was earned by, or null when there is no such row. */
    fun reverse(source: String, amount: Long): Long? =
        jdbc.sql("UPDATE commission SET amount = amount - :amount, updated_at = now() WHERE source = :source RETURNING account_id")
            .param("source", source)
            .param("amount", amount)
            .query(Long::class.javaObjectType)
            .optional()
            .orElse(null)

    /** Everything an account ever earned, net of reversals, in nano units of the partner currency. */
    fun totalByAccount(accountId: Long): Long =
        jdbc.sql("SELECT coalesce(sum(amount), 0) FROM commission WHERE account_id = :accountId")
            .param("accountId", accountId)
            .query(Long::class.javaObjectType)
            .single()

    /** What each player in [playerIds] earned on [companyId]; players without commission are absent from the map. */
    fun earnedByPlayers(companyId: Long, playerIds: Collection<Long>): Map<Long, Long> {
        if (playerIds.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            SELECT player_id, sum(amount) AS earned FROM commission
            WHERE company_id = :companyId AND player_id IN (:ids)
            GROUP BY player_id
            """,
        )
            .param("companyId", companyId)
            .param("ids", playerIds)
            .query { rs, _ -> rs.getLong("player_id") to rs.getLong("earned") }
            .list()
            .toMap()
    }

    /** An account's ledger, newest day first; [companyId] narrows it to one company. */
    fun page(accountId: Long, companyId: Long?, page: Int, size: Int): Page<CommissionEntry> {
        val filter = if (companyId == null) "" else "AND m.company_id = :companyId"
        val params = buildMap {
            put("accountId", accountId)
            if (companyId != null) put("companyId", companyId)
        }
        val items = jdbc.sql(
            """
            SELECT m.id, m.day, c.identity, m.player_id, m.type, m.amount, m.updated_at
            FROM commission m JOIN company c ON c.id = m.company_id
            WHERE m.account_id = :accountId $filter
            ORDER BY m.day DESC, m.id DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .params(params)
            .param("limit", size)
            .param("offset", offset(page, size))
            .query { rs, _ ->
                CommissionEntry(
                    id = rs.getLong("id"),
                    day = rs.localDate("day"),
                    company = rs.getString("identity"),
                    playerId = rs.getLong("player_id"),
                    type = CompanyType.valueOf(rs.getString("type")),
                    amount = rs.getLong("amount"),
                    updatedAt = rs.instant("updated_at"),
                )
            }
            .list()
        val total = jdbc.sql("SELECT count(*) FROM commission m WHERE m.account_id = :accountId $filter")
            .params(params)
            .query(Long::class.javaObjectType)
            .single()
        return Page(items, page, size, total)
    }
}
