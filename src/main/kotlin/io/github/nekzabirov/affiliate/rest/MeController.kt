package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.services.AuthService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MeController(
    private val auth: AuthService,
    private val properties: AppProperties,
) {

    @GetMapping("/me")
    fun me(@RequestAttribute(SessionFilter.ACCOUNT) account: Account) = MeResponse.of(auth.me(account), properties.partnerCurrency)
}
