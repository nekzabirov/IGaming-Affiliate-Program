package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.services.CompanyService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The signed-in partner's companies; another partner's company answers 404. */
@RestController
@RequestMapping("/api/companies")
class CompanyController(
    private val companies: CompanyService,
    private val properties: AppProperties,
) {

    @GetMapping
    fun list(@RequestAttribute(SessionFilter.ACCOUNT) account: Account): List<CompanyResponse> =
        companies.list(account).map { CompanyResponse.of(it, properties.siteOrigin) }

    @PostMapping
    fun create(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @RequestBody body: CreateCompanyRequest,
    ): ResponseEntity<CompanyResponse> {
        val created = companies.create(account, body.identity, body.type, body.amount)
        return ResponseEntity.status(HttpStatus.CREATED).body(CompanyResponse.of(created, properties.siteOrigin))
    }

    @GetMapping("/{identity}")
    fun get(@RequestAttribute(SessionFilter.ACCOUNT) account: Account, @PathVariable identity: String): CompanyResponse =
        CompanyResponse.of(companies.get(account, identity), properties.siteOrigin)

    @GetMapping("/{identity}/players")
    fun players(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @PathVariable identity: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<PlayerResponse> = companies.players(account, identity, page, size).map(PlayerResponse::of)
}
