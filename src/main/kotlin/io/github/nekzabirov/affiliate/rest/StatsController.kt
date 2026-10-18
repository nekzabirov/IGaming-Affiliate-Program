package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.services.StatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class StatsController(private val stats: StatsService) {

    /** `from`/`to` are UTC days (YYYY-MM-DD), the last 30 days by default; `company` narrows to one identity. */
    @GetMapping("/stats")
    fun stats(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) company: String?,
    ): StatsResponse = StatsResponse.of(stats.stats(account, from, to, company))
}
