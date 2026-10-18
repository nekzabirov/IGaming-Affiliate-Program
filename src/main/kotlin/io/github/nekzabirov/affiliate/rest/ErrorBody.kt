package io.github.nekzabirov.affiliate.rest

import com.fasterxml.jackson.annotation.JsonInclude
import io.github.nekzabirov.affiliate.errors.DomainException
import io.github.nekzabirov.affiliate.errors.ValidationException

/** Every refusal: a stable `code`, an English `message`, and for VALIDATION the `field` and `rule` it is about. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorBody(
    val code: String,

    val message: String,

    val field: String? = null,

    val rule: String? = null,
) {

    companion object {

        /** The code of a failure of ours, as opposed to a refusal. */
        const val INTERNAL = "INTERNAL"

        fun of(failure: DomainException): ErrorBody = when (failure) {
            is ValidationException -> ErrorBody(failure.code, failure.message.orEmpty(), failure.field, failure.rule)
            else -> ErrorBody(failure.code, failure.message.orEmpty())
        }

        /** The answer to a failure of ours: its cause is logged, never sent. */
        fun internal(): ErrorBody = ErrorBody(INTERNAL, "Something went wrong on our side")
    }
}
