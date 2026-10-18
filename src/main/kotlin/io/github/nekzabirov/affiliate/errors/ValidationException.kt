package io.github.nekzabirov.affiliate.errors

import org.springframework.http.HttpStatus

/**
 * The one refusal for bad input. [field] and [rule] travel on their own in the error body, so the cabinet puts
 * the message under the right input without parsing a sentence, and a new rule needs no new class.
 */
class ValidationException(
    val field: String,

    val rule: String,

    message: String,
) : DomainException(CODE, HttpStatus.BAD_REQUEST, message) {

    companion object {

        const val CODE = "VALIDATION"

        // The rules, named once: the cabinet may match on them, so they are contract too.
        const val REQUIRED = "required"

        const val LENGTH = "length"

        const val FORMAT = "format"

        const val INVALID = "invalid"

        const val POSITIVE = "positive"

        const val MAX = "max"

        const val RANGE = "range"

        const val TYPE = "type"

        const val UNKNOWN_FIELD = "unknown_field"

        const val BLOCKED_HOST = "blocked_host"

        const val UNRESOLVABLE_HOST = "unresolvable_host"
    }
}
