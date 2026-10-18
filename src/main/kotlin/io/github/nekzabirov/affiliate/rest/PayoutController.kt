package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.services.PayoutService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/payouts")
class PayoutController(private val payouts: PayoutService) {

    @GetMapping
    fun list(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): Page<PayoutResponse> = payouts.list(account, page, size).map(PayoutResponse::of)

    @PostMapping
    fun request(
        @RequestAttribute(SessionFilter.ACCOUNT) account: Account,
        @RequestBody body: CreatePayoutRequest,
    ): ResponseEntity<PayoutResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(PayoutResponse.of(payouts.request(account, body.amount, body.network, body.address)))
}
