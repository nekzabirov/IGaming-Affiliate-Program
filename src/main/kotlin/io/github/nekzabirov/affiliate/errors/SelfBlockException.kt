package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/** An admin blocking themself would lock the last way back in. */
class SelfBlockException : DomainException("SELF_BLOCK", HttpStatus.CONFLICT, "An admin cannot block their own account")
