package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.errors.ValidationException

/** The bounds every paged endpoint shares. */
object Paging {

    const val MAX_SIZE = 200

    fun check(page: Int, size: Int) {
        if (page < 0) {
            throw ValidationException("page", ValidationException.RANGE, "page must be 0 or more")
        }
        if (size !in 1..MAX_SIZE) {
            throw ValidationException("size", ValidationException.RANGE, "size must be 1..$MAX_SIZE")
        }
    }
}
