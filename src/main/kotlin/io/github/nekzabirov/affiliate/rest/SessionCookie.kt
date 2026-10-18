package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.config.AppProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * The `ap_session` cookie. HttpOnly, so no script on the page can read the token; SameSite=Lax, so another
 * site's form cannot post with it; Secure everywhere but a local http stand.
 */
@Component
class SessionCookie(private val properties: AppProperties) {

    fun read(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == NAME }?.value?.takeIf { it.isNotEmpty() }

    fun write(response: HttpServletResponse, token: String) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, Duration.ofDays(properties.session.ttlDays.toLong())))
    }

    fun clear(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
    }

    private fun cookie(value: String, maxAge: Duration): String =
        ResponseCookie.from(NAME, value)
            .httpOnly(true)
            .secure(properties.session.cookieSecure)
            .sameSite("Lax")
            .path("/")
            .maxAge(maxAge)
            .build()
            .toString()

    companion object {

        const val NAME = "ap_session"
    }
}
