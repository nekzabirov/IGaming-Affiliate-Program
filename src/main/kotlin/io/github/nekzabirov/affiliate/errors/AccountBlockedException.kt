package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/** An admin blocked the account; it can neither sign in nor use a session it already had. */
class AccountBlockedException : DomainException("ACCOUNT_BLOCKED", HttpStatus.FORBIDDEN, "The account is blocked")
