package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.errors.DomainException
import io.github.nekzabirov.affiliate.errors.NotFoundException
import io.github.nekzabirov.affiliate.errors.UnsupportedMediaTypeException
import io.github.nekzabirov.affiliate.errors.ValidationException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.core.JacksonException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.exc.UnrecognizedPropertyException

/**
 * Every failure leaves as `{code, message, field?, rule?}` with the status of its category; the code is also
 * left on the request for the `http.in` line. Spring's own refusals (a body that does not bind, a parameter that
 * does not convert, an unknown path) are translated into the same codes, so the cabinet reads one shape.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(DomainException::class)
    fun domain(failure: DomainException, request: HttpServletRequest): ResponseEntity<ErrorBody> = answer(request, failure)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(failure: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorBody> =
        answer(request, unreadableBody(failure))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun mismatch(failure: MethodArgumentTypeMismatchException, request: HttpServletRequest): ResponseEntity<ErrorBody> =
        answer(request, ValidationException(failure.name, ValidationException.TYPE, "${failure.name} has an invalid value"))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missing(failure: MissingServletRequestParameterException, request: HttpServletRequest): ResponseEntity<ErrorBody> =
        answer(request, ValidationException(failure.parameterName, ValidationException.REQUIRED, "${failure.parameterName} is required"))

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun mediaType(request: HttpServletRequest): ResponseEntity<ErrorBody> = answer(request, UnsupportedMediaTypeException())

    /** An unknown path, or a known one with the wrong method: either way there is no such endpoint. */
    @ExceptionHandler(NoResourceFoundException::class, HttpRequestMethodNotSupportedException::class)
    fun noEndpoint(request: HttpServletRequest): ResponseEntity<ErrorBody> = answer(request, NotFoundException("No such endpoint"))

    @ExceptionHandler(Exception::class)
    fun unexpected(failure: Exception, request: HttpServletRequest): ResponseEntity<ErrorBody> {
        log.atError()
            .addKeyValue("event", "http.failed")
            .addKeyValue("method", request.method)
            .addKeyValue("path", request.requestURI)
            .setCause(failure)
            .log("Request failed")
        request.setAttribute(TraceFilter.ERROR_CODE, ErrorBody.INTERNAL)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorBody.internal())
    }

    private fun answer(request: HttpServletRequest, failure: DomainException): ResponseEntity<ErrorBody> {
        request.setAttribute(TraceFilter.ERROR_CODE, failure.code)
        return ResponseEntity.status(failure.status).body(ErrorBody.of(failure))
    }

    /** Names the field where the mapper knows it: `{"login": 5}` is about `login`, not about "the body". */
    private fun unreadableBody(failure: HttpMessageNotReadableException): ValidationException {
        val cause = failure.cause as? JacksonException
            ?: return ValidationException(BODY, ValidationException.REQUIRED, "The request body is missing or is not JSON")
        val field = cause.path.mapNotNull { it.propertyName }.joinToString(".")
        return when {
            cause is UnrecognizedPropertyException ->
                ValidationException(field, ValidationException.UNKNOWN_FIELD, "$field is not a field of this request")
            field.isNotEmpty() ->
                ValidationException(field, ValidationException.TYPE, "$field has a value of the wrong type")
            cause is MismatchedInputException ->
                ValidationException(BODY, ValidationException.REQUIRED, "The request body must be a JSON object")
            else -> ValidationException(BODY, ValidationException.FORMAT, "The request body is not valid JSON")
        }
    }

    private companion object {

        val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

        const val BODY = "body"
    }
}
