package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/** Absent, or someone else's: another partner's company answers exactly like one that does not exist. */
class NotFoundException(message: String) : DomainException(CODE, HttpStatus.NOT_FOUND, message) {

    companion object {

        const val CODE = "NOT_FOUND"
    }
}
