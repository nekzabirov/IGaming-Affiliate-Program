package io.github.nekzabirov.affiliate.postback

import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.model.PostbackType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.net.UnknownHostException

/**
 * Keitaro S2S postbacks: `GET <scheme>://<tracker>/<postback key>/postback?subid=…&status=…`.
 *
 * The partner pastes the URL Keitaro shows under Settings → Postback URL; its query (`subid=REPLACE&…`) is a
 * template for humans and is dropped, because this adapter writes the query itself for every event:
 * a registration is Keitaro's built-in `registration`, every deposit its built-in `deposit` with the deposit as the
 * payout, and every stake `spin` (casino) or `bet` (sportsbook) with the whole stake as the payout - two custom
 * conversion types the partner creates in Keitaro. Every conversion but the registration carries a unique tid,
 * so none overwrites another. `cost`, `sub_id_N` and `source` are never sent - Keitaro would overwrite the
 * partner's click data with them.
 */
@Component
class KeitaroAdapter(private val guard: UrlGuard) : PostbackAdapter {

    override val type = PostbackType.KEITARO

    override fun normalize(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) {
            throw invalid(ValidationException.REQUIRED, "url is required")
        }
        if (text.length > MAX_URL_LENGTH) {
            throw invalid(ValidationException.LENGTH, "url must be at most $MAX_URL_LENGTH characters")
        }
        if (text.any { it.code !in PRINTABLE_ASCII }) {
            throw invalid(ValidationException.FORMAT, "url must be plain ASCII without spaces; write an IDN host in punycode")
        }
        if ('#' in text) {
            throw invalid(ValidationException.FORMAT, "url must not carry a #fragment")
        }
        val uri = try {
            URI(text.substringBefore('?'))
        } catch (_: URISyntaxException) {
            throw invalid(ValidationException.FORMAT, "url is not a valid URL")
        }
        val scheme = uri.scheme?.lowercase()?.takeIf { uri.isAbsolute && it in DEFAULT_PORTS }
            ?: throw invalid(ValidationException.FORMAT, "url must start with http:// or https://")
        if (uri.rawUserInfo != null) {
            throw invalid(ValidationException.FORMAT, "url must not carry a user name or password")
        }
        val host = uri.host?.lowercase() ?: throw invalid(ValidationException.FORMAT, "url has no valid host")
        if (uri.port != NO_PORT && uri.port !in ALLOWED_PORTS) {
            throw invalid(ValidationException.FORMAT, "url port must be one of ${ALLOWED_PORTS.joinToString()} or none")
        }
        val path = uri.rawPath.orEmpty()
        if (!POSTBACK_PATH.matches(path)) {
            throw invalid(
                ValidationException.FORMAT,
                "url must be the Keitaro postback URL, like https://your-tracker.com/abc1234/postback",
            )
        }
        try {
            guard.resolve(host)
        } catch (_: BlockedHostException) {
            throw invalid(ValidationException.BLOCKED_HOST, "url must point at a public tracker address")
        } catch (_: UnknownHostException) {
            throw invalid(ValidationException.UNRESOLVABLE_HOST, "url host does not resolve")
        }
        val port = if (uri.port == NO_PORT || uri.port == DEFAULT_PORTS[scheme]) "" else ":${uri.port}"
        return "$scheme://$host$port$path"
    }

    override fun build(base: String, event: PostbackEvent): URI {
        val clickId = requireNotNull(event.clickId) { "A Keitaro postback needs the click id as its subid" }
        val query = buildList {
            add(SUBID to clickId)
            add(STATUS to STATUSES.getValue(event.kind))
            if (event.kind != PostbackEvent.Kind.REGISTRATION) {
                val factId = requireNotNull(event.factId) { "A ${event.kind} postback needs the id of its fact" }
                add(PAYOUT to payout(event))
                add(CURRENCY to event.currency.lowercase())
                add(TID to "${TID_PREFIXES.getValue(event.kind)}-$factId")
            }
        }
        return URI.create(base + "?" + query.joinToString("&") { (name, value) -> "$name=${encode(value)}" })
    }

    private fun payout(event: PostbackEvent): String =
        BigDecimal.valueOf(event.amount, NANO_SCALE).setScale(PAYOUT_FRACTION_DIGITS, RoundingMode.HALF_UP).toPlainString()

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private fun invalid(rule: String, message: String) = ValidationException(FIELD_URL, rule, message)

    private companion object {

        const val FIELD_URL = "url"

        /** postback.url is VARCHAR(2048); the query this adapter adds stays far below any server's limit. */
        const val MAX_URL_LENGTH = 2048

        val PRINTABLE_ASCII = 0x21..0x7E

        const val NO_PORT = -1

        val DEFAULT_PORTS = mapOf("http" to 80, "https" to 443)

        val ALLOWED_PORTS = setOf(80, 443, 8080, 8443)

        /** Keitaro's key is 7 hex characters by default but can be changed to anything; only its place is fixed. */
        val POSTBACK_PATH = Regex("^/[^/]+/postback$")

        const val SUBID = "subid"

        const val STATUS = "status"

        const val PAYOUT = "payout"

        const val CURRENCY = "currency"

        const val TID = "tid"

        /** registration and deposit are Keitaro's own; spin and bet are custom types the partner creates. */
        val STATUSES = mapOf(
            PostbackEvent.Kind.REGISTRATION to "registration",
            PostbackEvent.Kind.DEPOSIT to "deposit",
            PostbackEvent.Kind.SPIN to "spin",
            PostbackEvent.Kind.BET to "bet",
        )

        val TID_PREFIXES = mapOf(
            PostbackEvent.Kind.DEPOSIT to "dep",
            PostbackEvent.Kind.SPIN to "spin",
            PostbackEvent.Kind.BET to "bet",
        )

        /** Amounts travel in nano units. */
        const val NANO_SCALE = 9

        const val PAYOUT_FRACTION_DIGITS = 2
    }
}
