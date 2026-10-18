package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/** Signed in, but the role does not allow it (the admin endpoints). */
class ForbiddenException : DomainException("FORBIDDEN", HttpStatus.FORBIDDEN, "Not allowed for this account")
