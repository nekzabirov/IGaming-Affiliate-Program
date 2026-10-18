package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.services.StatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The earnings ledger, newest first; `company` narrows it to one identity. */
@RestController
@RequestMapping("/api")
class CommissionController(private val stats: StatsService) {

    @GetMapping("/commissions")
    fun commissions(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) company: String?,
    ): Page<CommissionResponse> = stats.ledger(account, company, page, size).map(CommissionResponse::of)
}
