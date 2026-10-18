package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.errors.ForbiddenException
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Role
import io.github.nekzabirov.affiliate.services.AdminService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The admin side of the cabinet. SessionFilter already refuses /api/admin to anyone but an ADMIN; every
 * handler checks the role again, so a routing change can never open these to a partner.
 */
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val admin: AdminService,
    private val properties: AppProperties,
) {

    @GetMapping("/companies")
    fun companies(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<AdminCompanyResponse> {
        requireAdmin(account)
        return admin.companies(status, q, page, size).map { AdminCompanyResponse.of(it, properties.siteOrigin) }
    }

    @PatchMapping("/companies/{identity}")
    fun updateCompany(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @PathVariable identity: String,
        @RequestBody body: AdminCompanyUpdate,
    ): AdminCompanyResponse {
        requireAdmin(account)
        return AdminCompanyResponse.of(admin.updateCompany(account, identity, body.status, body.amount), properties.siteOrigin)
    }

    @GetMapping("/partners")
    fun partners(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<AdminPartnerResponse> {
        requireAdmin(account)
        return admin.partners(q, page, size).map(AdminPartnerResponse::of)
    }

    @PatchMapping("/partners/{id}")
    fun updatePartner(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @PathVariable id: Long,
        @RequestBody body: AdminPartnerUpdate,
    ): AdminPartnerResponse {
        requireAdmin(account)
        return AdminPartnerResponse.of(admin.updatePartner(account, id, body.active))
    }

    private fun requireAdmin(account: Account) {
        if (account.role != Role.ADMIN) throw ForbiddenException()
    }
}
