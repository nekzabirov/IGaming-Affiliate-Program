package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.db.ActivityStore
import io.github.nekzabirov.affiliate.db.CommissionStore
import io.github.nekzabirov.affiliate.db.CompanyStore
import io.github.nekzabirov.affiliate.db.ReferralStore
import io.github.nekzabirov.affiliate.db.StatsStore
import io.github.nekzabirov.affiliate.errors.IdentityTakenException
import io.github.nekzabirov.affiliate.errors.NotFoundException
import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Company
import io.github.nekzabirov.affiliate.model.CompanySummary
import io.github.nekzabirov.affiliate.model.CompanyTotals
import io.github.nekzabirov.affiliate.model.CompanyType
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Player
import io.github.nekzabirov.affiliate.model.PlayerActivity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * A partner's companies. The partner proposes the type and the amount; a company is born IN_REVIEW and from
 * then on only an admin changes its amount and status. Every lookup is scoped by the signed-in account.
 */
@Service
class CompanyService(
    private val companies: CompanyStore,
    private val referrals: ReferralStore,
    private val activity: ActivityStore,
    private val commissions: CommissionStore,
    private val stats: StatsStore,
) {

    fun list(account: Account): List<CompanySummary> {
        val owned = companies.findByAccount(account.id)
        val totals = stats.companyTotals(owned.map { it.id })
        return owned.map { CompanySummary(it, totals[it.id] ?: CompanyTotals.ZERO) }
    }

    fun create(account: Account, identity: String?, type: String?, amount: String?): CompanySummary {
        val name = checkIdentity(identity)
        val kind = checkType(type)
        val terms = checkAmount(kind, amount)
        val company = companies.insert(account.id, name, kind, terms) ?: throw IdentityTakenException()
        log.atInfo()
            .addKeyValue("event", "company.created")
            .addKeyValue("accountId", account.id)
            .addKeyValue("company", company.identity)
            .addKeyValue("type", company.type)
            .addKeyValue("amount", Money.format(company.amount))
            .log("Company created, waiting for review")
        return CompanySummary(company, CompanyTotals.ZERO)
    }

    fun get(account: Account, identity: String): CompanySummary {
        val company = owned(account, identity)
        return CompanySummary(company, stats.companyTotals(listOf(company.id))[company.id] ?: CompanyTotals.ZERO)
    }

    /** The company if [account] owns it; another partner's company is a 404, exactly like a missing one. */
    fun owned(account: Account, identity: String): Company =
        companies.findOwned(account.id, identity.trim().lowercase()) ?: throw NotFoundException("No such company")

    /** A company's players, newest registration first, each with their all-time activity and earnings. */
    fun players(account: Account, identity: String, page: Int, size: Int): Page<Player> {
        Paging.check(page, size)
        val company = owned(account, identity)
        val referrals = referrals.pageByCompany(company.id, page, size)
        val ids = referrals.items.map { it.playerId }
        val activities = activity.totalsByPlayers(ids)
        val earned = commissions.earnedByPlayers(company.id, ids)
        return referrals.map { Player(it, activities[it.playerId] ?: PlayerActivity.NONE, earned[it.playerId] ?: 0) }
    }

    companion object {

        private val log = LoggerFactory.getLogger(CompanyService::class.java)

        private const val FIELD_IDENTITY = "identity"

        private const val FIELD_TYPE = "type"

        private const val FIELD_AMOUNT = "amount"

        private const val IDENTITY_MIN = 3

        private const val IDENTITY_MAX = 48

        /** A slug: it becomes part of the player's affiliate tag and of every partner link. */
        private val IDENTITY = Regex("^[a-z0-9]+(?:[-_][a-z0-9]+)*$")

        fun checkIdentity(identity: String?): String {
            val name = identity?.trim()?.lowercase().orEmpty()
            if (name.isEmpty()) {
                throw ValidationException(FIELD_IDENTITY, ValidationException.REQUIRED, "identity is required")
            }
            if (name.length !in IDENTITY_MIN..IDENTITY_MAX) {
                throw ValidationException(
                    FIELD_IDENTITY,
                    ValidationException.LENGTH,
                    "identity must be $IDENTITY_MIN to $IDENTITY_MAX characters",
                )
            }
            if (!IDENTITY.matches(name)) {
                throw ValidationException(
                    FIELD_IDENTITY,
                    ValidationException.FORMAT,
                    "identity may hold latin letters and digits, joined by single '-' or '_'",
                )
            }
            return name
        }

        fun checkType(type: String?): CompanyType {
            val name = type?.trim()?.uppercase().orEmpty()
            if (name.isEmpty()) {
                throw ValidationException(FIELD_TYPE, ValidationException.REQUIRED, "type is required")
            }
            return CompanyType.entries.firstOrNull { it.name == name }
                ?: throw ValidationException(
                    FIELD_TYPE,
                    ValidationException.INVALID,
                    "type must be one of ${CompanyType.entries.joinToString()}",
                )
        }

        /**
         * CPA: an amount of the partner currency; revenue share: a percent of at most 100. Zero is valid for either
         * (a product rule): a partner paid up front keeps players, figures and postbacks, and earns nothing.
         */
        fun checkAmount(type: CompanyType, amount: String?): BigDecimal = when (type) {
            CompanyType.CPA -> Money.parseNonNegative(FIELD_AMOUNT, amount)
            CompanyType.REVSHARE_DEPOSIT, CompanyType.REVSHARE_TURNOVER ->
                Money.parseNonNegative(FIELD_AMOUNT, amount, max = Money.HUNDRED)
        }
    }
}
