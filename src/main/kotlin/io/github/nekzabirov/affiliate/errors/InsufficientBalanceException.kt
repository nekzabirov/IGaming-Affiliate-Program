package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

class InsufficientBalanceException :
    DomainException("INSUFFICIENT_BALANCE", HttpStatus.CONFLICT, "The balance is lower than the requested amount")
