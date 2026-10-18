package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Referral
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant

/** The `referral` table: which company a player was attributed to, and their first deposit. */
@Component
class ReferralStore(private val jdbc: JdbcClient, private val json: JsonMapper) {

    fun findByPlayer(playerId: Long): Referral? =
        jdbc.sql("SELECT $COLUMNS FROM referral WHERE player_id = :playerId")
            .param("playerId", playerId)
            .query { rs, _ -> read(rs) }
            .optional()
            .orElse(null)

    /**
     * Attributes a player to a company; false when the player already is. The primary key makes it once and for
     * good: a later fact can never hand the same player to a second company.
     */
    fun insert(
        playerId: Long,
        companyId: Long,
        clickId: String?,
        attribution: Map<String, String>,
        country: String?,
        registeredAt: Instant,
    ): Boolean =
        jdbc.sql(
            """
            INSERT INTO referral (player_id, company_id, click_id, attribution, country, registered_at)
            VALUES (:playerId, :companyId, :clickId, CAST(:attribution AS jsonb), :country, :registeredAt)
            ON CONFLICT (player_id) DO NOTHING
            """,
        )
            .param("playerId", playerId)
            .param("companyId", companyId)
            .param("clickId", clickId, Types.VARCHAR)
            .param("attribution", json.writeValueAsString(attribution))
            .param("country", country, Types.VARCHAR)
            .param("registeredAt", registeredAt.utc())
            .update() == 1

    /**
     * Makes [invoiceId] the player's first deposit unless one already is, and answers whether it became the first.
     * Two deposits racing for it: the second waits on the row, then finds it taken and changes nothing.
     */
    fun markFirstDeposit(playerId: Long, invoiceId: Long, amount: Long, at: Instant): Boolean =
        jdbc.sql(
            """
            UPDATE referral
            SET first_deposit_at = :at, first_deposit_amount = :amount, first_deposit_invoice_id = :invoiceId
            WHERE player_id = :playerId AND first_deposit_invoice_id IS NULL
            """,
        )
            .param("playerId", playerId)
            .param("invoiceId", invoiceId)
            .param("amount", amount)
            .param("at", at.utc())
            .update() == 1

    /** A company's players, newest registration first. */
    fun pageByCompany(companyId: Long, page: Int, size: Int): Page<Referral> {
        val items = jdbc.sql(
            """
            SELECT $COLUMNS FROM referral WHERE company_id = :companyId
            ORDER BY registered_at DESC, player_id DESC
            LIMIT :limit OFFSET :offset
            """,
        )
            .param("companyId", companyId)
            .param("limit", size)
            .param("offset", offset(page, size))
            .query { rs, _ -> read(rs) }
            .list()
        val total = jdbc.sql("SELECT count(*) FROM referral WHERE company_id = :companyId")
            .param("companyId", companyId)
            .query(Long::class.javaObjectType)
            .single()
        return Page(items, page, size, total)
    }

    companion object {

        /** Everything but `attribution`: it is kept for the record, and no screen reads it back. */
        const val COLUMNS = "player_id, company_id, click_id, country, registered_at, " +
            "first_deposit_at, first_deposit_amount, first_deposit_invoice_id, created_at"

        fun read(rs: ResultSet) = Referral(
            playerId = rs.getLong("player_id"),
            companyId = rs.getLong("company_id"),
            clickId = rs.getString("click_id"),
            country = rs.getString("country"),
            registeredAt = rs.instant("registered_at"),
            firstDepositAt = rs.instantOrNull("first_deposit_at"),
            firstDepositAmount = rs.longOrNull("first_deposit_amount"),
            firstDepositInvoiceId = rs.longOrNull("first_deposit_invoice_id"),
            createdAt = rs.instant("created_at"),
        )
    }
}
