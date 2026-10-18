package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

class TooManyRequestsException :
    DomainException("TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, try again later")
