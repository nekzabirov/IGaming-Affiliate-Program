package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

class LimitReachedException(message: String) : DomainException("LIMIT_REACHED", HttpStatus.CONFLICT, message)
