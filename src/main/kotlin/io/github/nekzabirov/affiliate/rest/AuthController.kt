package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.errors.InvalidCredentialsException
import io.github.nekzabirov.affiliate.services.AuthService
import io.github.nekzabirov.affiliate.services.SignIn
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Sign-up, sign-in and sign-out. Both sign-up and sign-in answer the account and set the session cookie. */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val auth: AuthService,
    private val limiter: RateLimiter,
    private val cookie: SessionCookie,
    private val properties: AppProperties,
) {

    @PostMapping("/register")
    fun register(
        @RequestBody body: AuthRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<MeResponse> {
        val ip = clientIp(request, properties.trustedClientIpHeader)
        limiter.checkRegistration(ip)
        val signIn = try {
            auth.register(body.login, body.password)
        } catch (failure: Exception) {
            limiter.registrationFailed(ip)
            throw failure
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(start(signIn, request, response))
    }

    @PostMapping("/login")
    fun login(@RequestBody body: AuthRequest, request: HttpServletRequest, response: HttpServletResponse): MeResponse {
        val ip = clientIp(request, properties.trustedClientIpHeader)
        val login = auth.normalizeLogin(body.login).take(LIMIT_KEY_LENGTH)
        limiter.checkLogin(login, ip)
        val signIn = try {
            auth.login(body.login, body.password)
        } catch (failure: InvalidCredentialsException) {
            // A wrong guess: the slots this attempt took stay taken.
            throw failure
        } catch (failure: Exception) {
            limiter.loginAbandoned(login, ip)
            throw failure
        }
        limiter.loginSucceeded(login, ip)
        return start(signIn, request, response)
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        cookie.read(request)?.let(auth::logout)
        cookie.clear(response)
        return ResponseEntity.noContent().build()
    }

    /** The browser's previous session, if it had one, ends here: one browser, one live session. */
    private fun start(signIn: SignIn, request: HttpServletRequest, response: HttpServletResponse): MeResponse {
        cookie.read(request)?.let(auth::logout)
        cookie.write(response, signIn.token)
        return MeResponse.of(auth.me(signIn.account), properties.partnerCurrency)
    }

    private companion object {

        /** Logins are at most 32 characters; a longer one is a guess, and must not grow the limiter's keys. */
        const val LIMIT_KEY_LENGTH = 64
    }
}
