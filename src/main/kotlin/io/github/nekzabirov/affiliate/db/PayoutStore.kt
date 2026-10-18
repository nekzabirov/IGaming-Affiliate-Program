package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Payout
import io.github.nekzabirov.affiliate.model.PayoutStatus
import io.github.nekzabirov.affiliate.model.PayoutTotals
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet

/** The `payout` table: withdrawal requests. Staff set PAID or REJECTED by hand; the app only ever inserts. */
@Component
class PayoutStore(private val jdbc: JdbcClient) {

    fun insert(accountId: Long, amount: Long, currency: String, network: String, address: String): Payout =
        jdbc.sql(
            """
            INSERT INTO payout (account_id, amount, currency, network, address)
            VALUES (:accountId, :amount, :currency, :network, :address)
            RETURNING $COLUMNS
            """,
        )
            .param("accountId", accountId)
            .param("amount", amount)
            .param("currency", currency)
            .param("network", network)
            .param("address", address)
            .query { rs, _ -> read(rs) }
            .single()

    /** An account's payouts, newest first. */
    fun page(accountId: Long, page: Int, size: Int): Page<Payout> {
        val items = jdbc.sql(
            """
            SELECT $COLUMNS FROM payout WHERE account_id = :accountId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .param("accountId", accountId)
            .param("limit", size)
            .param("offset", offset(page, size))
            .query { rs, _ -> read(rs) }
            .list()
        val total = jdbc.sql("SELECT count(*) FROM payout WHERE account_id = :accountId")
            .param("accountId", accountId)
            .query(Long::class.javaObjectType)
            .single()
        return Page(items, page, size, total)
    }

    fun totals(accountId: Long): PayoutTotals =
        jdbc.sql(
            """
            SELECT coalesce(sum(amount) FILTER (WHERE status = 'PENDING'), 0) AS pending,
                   coalesce(sum(amount) FILTER (WHERE status = 'PAID'), 0) AS paid
            FROM payout WHERE account_id = :accountId
            """,
        )
            .param("accountId", accountId)
            .query { rs, _ -> PayoutTotals(pending = rs.getLong("pending"), paid = rs.getLong("paid")) }
            .single()

    companion object {

        const val COLUMNS = "id, account_id, amount, currency, network, address, status, tx_hash, comment, created_at, updated_at"

        fun read(rs: ResultSet) = Payout(
            id = rs.getLong("id"),
            accountId = rs.getLong("account_id"),
            amount = rs.getLong("amount"),
            currency = rs.getString("currency"),
            network = rs.getString("network"),
            address = rs.getString("address"),
            status = PayoutStatus.valueOf(rs.getString("status")),
            txHash = rs.getString("tx_hash"),
            comment = rs.getString("comment"),
            createdAt = rs.instant("created_at"),
            updatedAt = rs.instant("updated_at"),
        )
    }
}
