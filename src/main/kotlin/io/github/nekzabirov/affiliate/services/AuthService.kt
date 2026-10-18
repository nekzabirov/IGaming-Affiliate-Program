package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.db.AccountStore
import io.github.nekzabirov.affiliate.db.CommissionStore
import io.github.nekzabirov.affiliate.db.PayoutStore
import io.github.nekzabirov.affiliate.db.SessionStore
import io.github.nekzabirov.affiliate.errors.AccountBlockedException
import io.github.nekzabirov.affiliate.errors.InvalidCredentialsException
import io.github.nekzabirov.affiliate.errors.LoginTakenException
import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Me
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.HexFormat

/**
 * Partner accounts of this app's own (not the casino's players): login + password, and server-side sessions.
 *
 * A session is 32 random bytes handed to the browser in a cookie; the database keeps only their SHA-256, so a
 * leaked table opens no session.
 */
@Service
class AuthService(
    private val accounts: AccountStore,
    private val sessions: SessionStore,
    private val commissions: CommissionStore,
    private val payouts: PayoutStore,
    private val hasher: PasswordHasher,
    private val properties: AppProperties,
) {

    private val random = SecureRandom()

    /** Creates a partner account and signs it in. */
    fun register(login: String?, password: String?): SignIn {
        val name = checkLogin(login)
        val secret = checkPassword(password)
        val account = accounts.insert(name, hasher.hash(secret)) ?: throw LoginTakenException()
        log.atInfo().addKeyValue("event", "auth.registered").addKeyValue("accountId", account.id).log("Partner registered")
        return SignIn(account, openSession(account))
    }

    /**
     * The same refusal, and the same bcrypt work, for an unknown login and a wrong password; a blocked account
     * is told so only after its password matched, so the answer reveals nothing to someone guessing.
     */
    fun login(login: String?, password: String?): SignIn {
        val name = normalizeLogin(login)
        val secret = password.orEmpty()
        val credentials = if (LOGIN.matches(name) && fitsBcrypt(secret)) accounts.findCredentials(name) else null
        if (credentials == null) {
            hasher.burn()
            throw InvalidCredentialsException()
        }
        if (!hasher.matches(secret, credentials.passwordHash)) {
            throw InvalidCredentialsException()
        }
        if (!credentials.account.active) {
            throw AccountBlockedException()
        }
        log.atInfo().addKeyValue("event", "auth.login").addKeyValue("accountId", credentials.account.id).log("Partner signed in")
        return SignIn(credentials.account, openSession(credentials.account))
    }

    fun logout(token: String) {
        sessions.delete(fingerprint(token))
    }

    /** The account behind a session cookie; null when the session is unknown or expired. */
    fun authenticate(token: String): Account? = sessions.findAccount(fingerprint(token))

    fun me(account: Account): Me {
        val totals = payouts.totals(account.id)
        return Me(
            account = account,
            totalEarned = commissions.totalByAccount(account.id),
            pendingPayouts = totals.pending,
            paidOut = totals.paid,
        )
    }

    /** Logins are compared lower-case, so `Alice` and `alice` are one account. */
    fun normalizeLogin(login: String?): String = login?.trim()?.lowercase().orEmpty()

    private fun openSession(account: Account): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val expiresAt = Instant.now().plus(Duration.ofDays(properties.session.ttlDays.toLong()))
        sessions.insert(fingerprint(token), account.id, expiresAt)
        return token
    }

    private fun checkLogin(login: String?): String {
        val name = normalizeLogin(login)
        if (name.isEmpty()) {
            throw ValidationException(FIELD_LOGIN, ValidationException.REQUIRED, "login is required")
        }
        if (name.length !in LOGIN_MIN..LOGIN_MAX) {
            throw ValidationException(FIELD_LOGIN, ValidationException.LENGTH, "login must be $LOGIN_MIN to $LOGIN_MAX characters")
        }
        if (!LOGIN.matches(name)) {
            throw ValidationException(
                FIELD_LOGIN,
                ValidationException.FORMAT,
                "login may hold latin letters, digits, '.', '_' and '-', and must start and end with a letter or digit",
            )
        }
        return name
    }

    private fun checkPassword(password: String?): String {
        if (password.isNullOrEmpty()) {
            throw ValidationException(FIELD_PASSWORD, ValidationException.REQUIRED, "password is required")
        }
        if (password.length !in PASSWORD_MIN..PASSWORD_MAX || !fitsBcrypt(password)) {
            throw ValidationException(
                FIELD_PASSWORD,
                ValidationException.LENGTH,
                "password must be $PASSWORD_MIN to $PASSWORD_MAX characters ($BCRYPT_MAX_BYTES bytes)",
            )
        }
        return password
    }

    /** bcrypt reads at most 72 bytes, and Spring's encoder refuses a longer password outright. */
    private fun fitsBcrypt(password: String): Boolean =
        password.isNotEmpty() && password.toByteArray(Charsets.UTF_8).size <= BCRYPT_MAX_BYTES

    private fun fingerprint(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)))

    private companion object {

        val log = LoggerFactory.getLogger(AuthService::class.java)

        const val FIELD_LOGIN = "login"

        const val FIELD_PASSWORD = "password"

        const val LOGIN_MIN = 3

        const val LOGIN_MAX = 32

        val LOGIN = Regex("^[a-z0-9](?:[a-z0-9._-]{1,30}[a-z0-9])$")

        const val PASSWORD_MIN = 8

        const val PASSWORD_MAX = 72

        const val BCRYPT_MAX_BYTES = 72

        const val TOKEN_BYTES = 32
    }
}
