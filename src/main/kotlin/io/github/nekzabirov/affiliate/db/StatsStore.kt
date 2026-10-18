package io.github.nekzabirov.affiliate.db

import io.github.nekzabirov.affiliate.model.CompanyDayActivity
import io.github.nekzabirov.affiliate.model.CompanyDayCommission
import io.github.nekzabirov.affiliate.model.CompanyDayCount
import io.github.nekzabirov.affiliate.model.CompanyTotals
import io.github.nekzabirov.affiliate.model.CompanyType
import io.github.nekzabirov.affiliate.model.CountryCount
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

/**
 * The analytics reads, across `referral`, `activity_daily` and `commission`. Every query takes the company ids
 * it may see, so a partner's figures can only ever come from their own companies, and every one is grouped by
 * company and UTC day: the service folds those rows into the daily, per-company and total views at once.
 *
 * Registrations count by `registered_at` and first deposits by `first_deposit_at`, both cut at UTC midnight;
 * deposits, turnover and commission are already stored per UTC day.
 */
@Component
class StatsStore(private val jdbc: JdbcClient) {

    /** All-time registrations, first deposits and earnings of each company; every id in [companyIds] is answered. */
    fun companyTotals(companyIds: Collection<Long>): Map<Long, CompanyTotals> {
        if (companyIds.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            SELECT c.id,
                   coalesce(r.registrations, 0) AS registrations,
                   coalesce(r.first_deposits, 0) AS first_deposits,
                   coalesce(m.earned, 0) AS earned
            FROM company c
            LEFT JOIN (
                SELECT company_id, count(*) AS registrations, count(first_deposit_at) AS first_deposits
                FROM referral WHERE company_id IN (:ids) GROUP BY company_id
            ) r ON r.company_id = c.id
            LEFT JOIN (
                SELECT company_id, sum(amount) AS earned
                FROM commission WHERE company_id IN (:ids) GROUP BY company_id
            ) m ON m.company_id = c.id
            WHERE c.id IN (:ids)
            """,
        )
            .param("ids", companyIds)
            .query { rs, _ ->
                rs.getLong("id") to CompanyTotals(
                    registrations = rs.getLong("registrations"),
                    firstDeposits = rs.getLong("first_deposits"),
                    earned = rs.getLong("earned"),
                )
            }
            .list()
            .toMap()
    }

    /** Players registered in [from, until), per company and UTC day. */
    fun registrations(companyIds: Collection<Long>, from: Instant, until: Instant): List<CompanyDayCount> =
        countByDay("registered_at", companyIds, from, until)

    /** First deposits made in [from, until), per company and UTC day. */
    fun firstDeposits(companyIds: Collection<Long>, from: Instant, until: Instant): List<CompanyDayCount> =
        countByDay("first_deposit_at", companyIds, from, until)

    fun activity(companyIds: Collection<Long>, from: LocalDate, to: LocalDate): List<CompanyDayActivity> {
        if (companyIds.isEmpty()) return emptyList()
        return jdbc.sql(
            """
            SELECT company_id, day,
                   sum(deposit_count) AS deposit_count,
                   sum(deposit_amount) AS deposit_amount,
                   sum(turnover_amount) AS turnover
            FROM activity_daily
            WHERE company_id IN (:ids) AND day BETWEEN :from AND :to
            GROUP BY company_id, day
            """,
        )
            .param("ids", companyIds)
            .param("from", from)
            .param("to", to)
            .query { rs, _ ->
                CompanyDayActivity(
                    companyId = rs.getLong("company_id"),
                    day = rs.localDate("day"),
                    depositCount = rs.getLong("deposit_count"),
                    depositAmount = rs.getLong("deposit_amount"),
                    turnover = rs.getLong("turnover"),
                )
            }
            .list()
    }

    fun commissions(companyIds: Collection<Long>, from: LocalDate, to: LocalDate): List<CompanyDayCommission> {
        if (companyIds.isEmpty()) return emptyList()
        return jdbc.sql(
            """
            SELECT company_id, day, type, sum(amount) AS amount
            FROM commission
            WHERE company_id IN (:ids) AND day BETWEEN :from AND :to
            GROUP BY company_id, day, type
            """,
        )
            .param("ids", companyIds)
            .param("from", from)
            .param("to", to)
            .query { rs, _ ->
                CompanyDayCommission(
                    companyId = rs.getLong("company_id"),
                    day = rs.localDate("day"),
                    type = CompanyType.valueOf(rs.getString("type")),
                    amount = rs.getLong("amount"),
                )
            }
            .list()
    }

    /** The [limit] countries with the most registrations in [from, until), with their first deposits in it. */
    fun countries(companyIds: Collection<Long>, from: Instant, until: Instant, limit: Int): List<CountryCount> {
        if (companyIds.isEmpty()) return emptyList()
        return jdbc.sql(
            """
            SELECT country,
                   count(*) FILTER (WHERE registered_at >= :from AND registered_at < :until) AS registrations,
                   count(*) FILTER (WHERE first_deposit_at >= :from AND first_deposit_at < :until) AS first_deposits
            FROM referral
            WHERE company_id IN (:ids)
              AND ((registered_at >= :from AND registered_at < :until)
                OR (first_deposit_at >= :from AND first_deposit_at < :until))
            GROUP BY country
            ORDER BY registrations DESC, first_deposits DESC, country NULLS LAST
            LIMIT :limit
            """,
        )
            .param("ids", companyIds)
            .param("from", from.utc())
            .param("until", until.utc())
            .param("limit", limit)
            .query { rs, _ ->
                CountryCount(
                    country = rs.getString("country"),
                    registrations = rs.getLong("registrations"),
                    firstDeposits = rs.getLong("first_deposits"),
                )
            }
            .list()
    }

    private fun countByDay(column: String, companyIds: Collection<Long>, from: Instant, until: Instant): List<CompanyDayCount> {
        if (companyIds.isEmpty()) return emptyList()
        return jdbc.sql(
            """
            SELECT company_id, ($column AT TIME ZONE 'UTC')::date AS day, count(*) AS players
            FROM referral
            WHERE company_id IN (:ids) AND $column >= :from AND $column < :until
            GROUP BY company_id, day
            """,
        )
            .param("ids", companyIds)
            .param("from", from.utc())
            .param("until", until.utc())
            .query { rs, _ ->
                CompanyDayCount(
                    companyId = rs.getLong("company_id"),
                    day = rs.localDate("day"),
                    count = rs.getLong("players"),
                )
            }
            .list()
    }
}
