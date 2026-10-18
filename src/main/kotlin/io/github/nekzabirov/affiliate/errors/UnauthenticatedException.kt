package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/** No session, or one that expired or was logged out. */
class UnauthenticatedException : DomainException("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "Sign in to continue")
