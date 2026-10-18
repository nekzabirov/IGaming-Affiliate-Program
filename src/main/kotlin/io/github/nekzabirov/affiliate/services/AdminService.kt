package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.db.AccountStore
import io.github.nekzabirov.affiliate.db.CompanyStore
import io.github.nekzabirov.affiliate.db.StatsStore
import io.github.nekzabirov.affiliate.errors.NotFoundException
import io.github.nekzabirov.affiliate.errors.SelfBlockException
import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.CompanyReview
import io.github.nekzabirov.affiliate.model.CompanyStatus
import io.github.nekzabirov.affiliate.model.CompanyTotals
import io.github.nekzabirov.affiliate.model.Owner
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Partner
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** What only an admin does: review companies (terms and status) and block or unblock partners. */
@Service
class AdminService(
    private val accounts: AccountStore,
    private val companies: CompanyStore,
    private val stats: StatsStore,
) {

    fun companies(status: String?, query: String?, page: Int, size: Int): Page<CompanyReview> {
        Paging.check(page, size)
        val owned = companies.reviewPage(status?.takeIf { it.isNotBlank() }?.let(::checkStatus), search(query), page, size)
        val totals = stats.companyTotals(owned.items.map { it.company.id })
        return owned.map { CompanyReview(it.company, totals[it.company.id] ?: CompanyTotals.ZERO, it.owner) }
    }

    /** Approves, disables or re-activates a company and/or sets its final terms, by the same rules a partner proposes them. */
    fun updateCompany(admin: Account, identity: String, status: String?, amount: String?): CompanyReview {
        val company = companies.findByIdentity(identity.trim().lowercase()) ?: throw NotFoundException("No such company")
        val newStatus = status?.let(::checkStatus) ?: company.status
        val newAmount = amount?.let { CompanyService.checkAmount(company.type, it) } ?: company.amount
        val updated = companies.update(company.id, newStatus, newAmount) ?: throw NotFoundException("No such company")
        val owner = checkNotNull(accounts.findById(updated.accountId)) { "Company ${updated.id} has no owner" }
        log.atInfo()
            .addKeyValue("event", "admin.company_updated")
            .addKeyValue("adminId", admin.id)
            .addKeyValue("company", updated.identity)
            .addKeyValue("status", updated.status)
            .addKeyValue("amount", Money.format(updated.amount))
            .log("Company reviewed")
        val totals = stats.companyTotals(listOf(updated.id))[updated.id] ?: CompanyTotals.ZERO
        return CompanyReview(updated, totals, Owner(owner.id, owner.login, owner.active))
    }

    fun partners(query: String?, page: Int, size: Int): Page<Partner> {
        Paging.check(page, size)
        val found = accounts.page(search(query), page, size)
        val counts = companies.countByAccounts(found.items.map { it.id })
        return found.map { Partner(it, counts[it.id] ?: 0) }
    }

    /** Blocks or unblocks an account. A blocked account keeps its sessions, and every request on them answers ACCOUNT_BLOCKED. */
    fun updatePartner(admin: Account, id: Long, active: Boolean?): Partner {
        if (active == null) {
            throw ValidationException(FIELD_ACTIVE, ValidationException.REQUIRED, "active is required")
        }
        if (!active && id == admin.id) {
            throw SelfBlockException()
        }
        val updated = accounts.setActive(id, active) ?: throw NotFoundException("No such partner")
        log.atInfo()
            .addKeyValue("event", "admin.partner_updated")
            .addKeyValue("adminId", admin.id)
            .addKeyValue("accountId", id)
            .addKeyValue("active", active)
            .log(if (active) "Partner unblocked" else "Partner blocked")
        return Partner(updated, companies.countByAccounts(listOf(id))[id] ?: 0)
    }

    private fun checkStatus(status: String): CompanyStatus {
        val name = status.trim().uppercase()
        return CompanyStatus.entries.firstOrNull { it.name == name }
            ?: throw ValidationException(
                FIELD_STATUS,
                ValidationException.INVALID,
                "status must be one of ${CompanyStatus.entries.joinToString()}",
            )
    }

    /** A search text, or null for none; long input is cut rather than refused, it only narrows a list. */
    private fun search(query: String?): String? = query?.trim()?.take(MAX_QUERY_LENGTH)?.ifEmpty { null }

    private companion object {

        val log = LoggerFactory.getLogger(AdminService::class.java)

        const val FIELD_STATUS = "status"

        const val FIELD_ACTIVE = "active"

        const val MAX_QUERY_LENGTH = 64
    }
}
