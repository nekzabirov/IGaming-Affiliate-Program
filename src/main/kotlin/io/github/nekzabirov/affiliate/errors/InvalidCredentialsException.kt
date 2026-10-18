package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/** The same answer for an unknown login and a wrong password, so the login form cannot be used to find accounts. */
class InvalidCredentialsException :
    DomainException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Wrong login or password")
