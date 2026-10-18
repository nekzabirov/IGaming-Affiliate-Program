package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.errors.AccountBlockedException
import io.github.nekzabirov.affiliate.errors.DomainException
import io.github.nekzabirov.affiliate.errors.ForbiddenException
import io.github.nekzabirov.affiliate.errors.UnauthenticatedException
import io.github.nekzabirov.affiliate.errors.UnsupportedMediaTypeException
import io.github.nekzabirov.affiliate.model.Role
import io.github.nekzabirov.affiliate.services.AuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

/**
 * The gate in front of /api: writes must be JSON, everything but the four public endpoints needs a live
 * session of an active account, and /api/admin needs an ADMIN. The account is resolved once, here, with one
 * query, and handed to the controllers as the [ACCOUNT] request attribute.
 *
 * A blocked account is treated as if it had no access at all: its sessions answer ACCOUNT_BLOCKED on every
 * call until an admin unblocks it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class SessionFilter(
    private val auth: AuthService,
    private val cookie: SessionCookie,
    private val json: JsonMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !isApi(apiPath(request))

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!isJsonWrite(request)) {
            refuse(request, response, UnsupportedMediaTypeException())
            return
        }
        val path = apiPath(request)
        if (request.method to path in PUBLIC) {
            chain.doFilter(request, response)
            return
        }
        val token = cookie.read(request)
        val account = try {
            token?.let(auth::authenticate)
        } catch (failure: Exception) {
            // A filter runs outside the DispatcherServlet, so ApiExceptionHandler never sees this failure: left to
            // the container, the database being away would answer Boot's error page and an http.in line saying 200.
            failed(request, response, failure)
            return
        }
        when {
            account == null -> {
                // A cookie naming no live session is dead weight the browser would send forever.
                if (token != null) cookie.clear(response)
                refuse(request, response, UnauthenticatedException())
            }
            !account.active -> refuse(request, response, AccountBlockedException())
            isAdmin(path) && account.role != Role.ADMIN -> refuse(request, response, ForbiddenException())
            else -> {
                request.setAttribute(ACCOUNT, account)
                chain.doFilter(request, response)
            }
        }
    }

    /**
     * A write must be JSON. One with neither a body nor a content type (logout, a DELETE) passes: an HTML
     * form always sends a content type, and that is the request this rule exists to stop.
     */
    private fun isJsonWrite(request: HttpServletRequest): Boolean {
        if (request.method !in WRITES) return true
        val type = request.contentType
            ?: return request.contentLengthLong <= 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) == null
        return try {
            MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(type))
        } catch (_: InvalidMediaTypeException) {
            false
        }
    }

    private fun isAdmin(path: String): Boolean = path == ADMIN_ROOT || path.startsWith("$ADMIN_ROOT/")

    private fun refuse(request: HttpServletRequest, response: HttpServletResponse, failure: DomainException) {
        answer(request, response, failure.status, ErrorBody.of(failure))
    }

    private fun failed(request: HttpServletRequest, response: HttpServletResponse, failure: Exception) {
        log.atError()
            .addKeyValue("event", "http.failed")
            .addKeyValue("method", request.method)
            .addKeyValue("path", request.requestURI)
            .setCause(failure)
            .log("Request failed")
        answer(request, response, HttpStatus.INTERNAL_SERVER_ERROR, ErrorBody.internal())
    }

    private fun answer(request: HttpServletRequest, response: HttpServletResponse, status: HttpStatus, body: ErrorBody) {
        request.setAttribute(TraceFilter.ERROR_CODE, body.code)
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        json.writeValue(response.outputStream, body)
    }

    companion object {

        /** The request attribute holding the signed-in [io.github.nekzabirov.affiliate.model.Account]. */
        const val ACCOUNT = "affiliate.account"

        private const val ADMIN_ROOT = "/api/admin"

        private val WRITES = setOf("POST", "PUT", "PATCH", "DELETE")

        private val PUBLIC = setOf(
            "GET" to "/api/config",
            "POST" to "/api/auth/register",
            "POST" to "/api/auth/login",
            "POST" to "/api/auth/logout",
        )

        private val log = LoggerFactory.getLogger(SessionFilter::class.java)
    }
}
