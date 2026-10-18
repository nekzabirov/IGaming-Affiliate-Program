package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.services.AuthService
import io.github.nekzabirov.affiliate.support.CapturedLog
import io.github.nekzabirov.affiliate.support.testProperties
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.json.JsonMapper

/**
 * The filters in front of /api, as the container chains them, when the session lookup itself breaks: the
 * database is away, or the pool has no connection to give within its timeout.
 */
class SessionFilterTest {

    private val json = JsonMapper.builder().build()

    private val auth: AuthService = mock(AuthService::class.java)

    private var reached = false

    private val controllers = object : HttpServlet() {

        override fun service(request: HttpServletRequest, response: HttpServletResponse) {
            reached = true
        }
    }

    @Test
    fun `a session lookup that fails answers INTERNAL in the API's own shape, and the http_in line says so`() {
        doThrow(CannotGetJdbcConnectionException("Connection is not available, request timed out after 3000ms"))
            .`when`(auth).authenticate(TOKEN)
        val request = MockHttpServletRequest("GET", "/api/me").apply {
            servletPath = "/api/me"
            setCookies(Cookie(SessionCookie.NAME, TOKEN))
        }
        val response = MockHttpServletResponse()

        CapturedLog(TraceFilter::class.java).use { log ->
            MockFilterChain(controllers, TraceFilter(), SessionFilter(auth, SessionCookie(testProperties()), json)).doFilter(request, response)

            assertThat(reached).isFalse()
            assertThat(response.status).isEqualTo(500)
            assertThat(response.contentType).startsWith("application/json")
            val body = json.readTree(response.contentAsString)
            assertThat(body["code"].asString()).isEqualTo("INTERNAL")
            assertThat(body["message"].asString()).isEqualTo("Something went wrong on our side")
            val line = log.lines("http.in").single()
            assertThat(line["status"]).isEqualTo(500)
            assertThat(line["code"]).isEqualTo("INTERNAL")
        }
    }

    private companion object {

        const val TOKEN = "a-session-token"
    }
}
