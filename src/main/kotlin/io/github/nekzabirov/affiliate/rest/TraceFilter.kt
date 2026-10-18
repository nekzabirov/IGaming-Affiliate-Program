package io.github.nekzabirov.affiliate.rest

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * The edge of every call: a `traceId` for the request's log lines (the caller's `X-Request-ID`, or a new one,
 * echoed back so a screen error can quote it), and exactly one `http.in` line per API request with its status,
 * refusal code and duration. Never the query string.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TraceFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val traceId = request.getHeader(REQUEST_ID)?.takeIf { TRACE_ID_FORMAT.matches(it) } ?: UUID.randomUUID().toString()
        MDC.put(TRACE_ID, traceId)
        response.setHeader(REQUEST_ID, traceId)
        val started = System.nanoTime()
        try {
            chain.doFilter(request, response)
        } finally {
            if (isApi(apiPath(request))) {
                log.atInfo()
                    .addKeyValue("event", "http.in")
                    .addKeyValue("method", request.method)
                    .addKeyValue("path", request.requestURI)
                    .addKeyValue("status", response.status)
                    .addKeyValue("code", request.getAttribute(ERROR_CODE))
                    .addKeyValue("durationMs", (System.nanoTime() - started) / NANOS_PER_MILLI)
                    .log("{} {} {}", request.method, request.requestURI, response.status)
            }
            MDC.remove(TRACE_ID)
        }
    }

    companion object {

        /** The refusal code of a request, left by whoever refused it, for the `http.in` line. */
        const val ERROR_CODE = "affiliate.errorCode"

        const val TRACE_ID = "traceId"

        private const val REQUEST_ID = "X-Request-ID"

        /** A caller's id is taken only when it is short and harmless in a log line and a header. */
        private val TRACE_ID_FORMAT = Regex("^[A-Za-z0-9._:-]{1,128}$")

        private const val NANOS_PER_MILLI = 1_000_000L

        private val log = LoggerFactory.getLogger(TraceFilter::class.java)
    }
}
