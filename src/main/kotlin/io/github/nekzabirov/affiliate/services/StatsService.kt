package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.db.CommissionStore
import io.github.nekzabirov.affiliate.db.CompanyStore
import io.github.nekzabirov.affiliate.db.StatsStore
import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.CommissionEntry
import io.github.nekzabirov.affiliate.model.CompanyType
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Stats
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * A partner's analytics over a range of UTC days, across all their companies or one of them, and the ledger
 * of what they earned.
 *
 * Registrations count on the day the player registered and first deposits on the day of that deposit, so a
 * range's conversion compares two different groups of players and can pass 100%.
 */
@Service
class StatsService(
    private val companies: CompanyService,
    private val companyStore: CompanyStore,
    private val stats: StatsStore,
    private val commissions: CommissionStore,
) {

    /** The earnings ledger, newest day first, across all companies or the one named by [company]. */
    fun ledger(account: Account, company: String?, page: Int, size: Int): Page<CommissionEntry> {
        Paging.check(page, size)
        val companyId = if (company.isNullOrBlank()) null else companies.owned(account, company).id
        return commissions.page(account.id, companyId, page, size)
    }

    fun stats(account: Account, from: String?, to: String?, company: String?): Stats {
        val last = day(FIELD_TO, to) ?: LocalDate.now(ZoneOffset.UTC)
        val first = day(FIELD_FROM, from) ?: last.minusDays(DEFAULT_DAYS - 1)
        if (first > last) {
            throw ValidationException(FIELD_FROM, ValidationException.RANGE, "from must not be after to")
        }
        if (ChronoUnit.DAYS.between(first, last) + 1 > MAX_DAYS) {
            throw ValidationException(FIELD_FROM, ValidationException.RANGE, "the range must be at most $MAX_DAYS days")
        }
        val scope = if (company.isNullOrBlank()) companyStore.findByAccount(account.id) else listOf(companies.owned(account, company))
        val ids = scope.map { it.id }
        val since = first.atStartOfDay(ZoneOffset.UTC).toInstant()
        val until = last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val registrations = stats.registrations(ids, since, until)
        val firstDeposits = stats.firstDeposits(ids, since, until)
        val activity = stats.activity(ids, first, last)
        val earnings = stats.commissions(ids, first, last)

        val registeredOn = registrations.sumPer({ it.day }) { it.count }
        val depositedFirstOn = firstDeposits.sumPer({ it.day }) { it.count }
        val depositsOn = activity.sumPer({ it.day }) { it.depositAmount }
        val turnoverOn = activity.sumPer({ it.day }) { it.turnover }
        val earnedOn = earnings.sumPer({ it.day }) { it.amount }
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { day ->
            Stats.Day(
                day = day,
                registrations = registeredOn[day] ?: 0,
                firstDeposits = depositedFirstOn[day] ?: 0,
                depositAmount = depositsOn[day] ?: 0,
                turnover = turnoverOn[day] ?: 0,
                earned = earnedOn[day] ?: 0,
            )
        }.toList()

        val registeredAt = registrations.sumPer({ it.companyId }) { it.count }
        val depositedFirstAt = firstDeposits.sumPer({ it.companyId }) { it.count }
        val depositsAt = activity.sumPer({ it.companyId }) { it.depositAmount }
        val turnoverAt = activity.sumPer({ it.companyId }) { it.turnover }
        val earnedAt = earnings.sumPer({ it.companyId }) { it.amount }
        val lines = scope.map { company ->
            Stats.CompanyLine(
                company = company,
                registrations = registeredAt[company.id] ?: 0,
                firstDeposits = depositedFirstAt[company.id] ?: 0,
                depositAmount = depositsAt[company.id] ?: 0,
                turnover = turnoverAt[company.id] ?: 0,
                earned = earnedAt[company.id] ?: 0,
            )
        }.sortedWith(BY_EARNINGS)

        val registered = registrations.sumOf { it.count }
        val deposited = firstDeposits.sumOf { it.count }
        val earnedAs = earnings.sumPer({ it.type }) { it.amount }
        val totals = Stats.Totals(
            registrations = registered,
            firstDeposits = deposited,
            conversion = conversion(deposited, registered),
            depositCount = activity.sumOf { it.depositCount },
            depositAmount = activity.sumOf { it.depositAmount },
            turnover = activity.sumOf { it.turnover },
            earned = earnings.sumOf { it.amount },
            earnedByType = CompanyType.entries.associateWith { earnedAs[it] ?: 0 },
        )
        return Stats(first, last, totals, days, lines, stats.countries(ids, since, until, TOP_COUNTRIES))
    }

    /** Sums [value] of the rows sharing a [key]. */
    private fun <T, K> List<T>.sumPer(key: (T) -> K, value: (T) -> Long): Map<K, Long> =
        groupingBy(key).fold(0L) { sum, row -> sum + value(row) }

    private fun conversion(firstDeposits: Long, registrations: Long): BigDecimal =
        if (registrations == 0L) {
            BigDecimal.ZERO
        } else {
            BigDecimal.valueOf(firstDeposits).multiply(Money.HUNDRED).divide(BigDecimal.valueOf(registrations), CONVERSION_DIGITS, RoundingMode.HALF_UP)
        }

    private fun day(field: String, value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value.trim())
        } catch (_: DateTimeParseException) {
            throw ValidationException(field, ValidationException.FORMAT, "$field must be a day as YYYY-MM-DD")
        }
    }

    private companion object {

        const val FIELD_FROM = "from"

        const val FIELD_TO = "to"

        /** The default range: the last 30 days, today included. */
        const val DEFAULT_DAYS = 30L

        const val MAX_DAYS = 366L

        const val TOP_COUNTRIES = 10

        const val CONVERSION_DIGITS = 2

        /** The companies table: the best earner first, then the most registrations, then by name. */
        val BY_EARNINGS = compareByDescending<Stats.CompanyLine> { it.earned }
            .thenByDescending { it.registrations }
            .thenBy { it.company.identity }
    }
}
