package io.github.nekzabirov.affiliate.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException

/**
 * Every setting of this app that is not Spring's own, bound from the env names in application.properties.
 *
 * A value that is set but malformed fails the boot here, naming its env variable: a pod that starts with a
 * wrong currency or a broken site URL would pay out and hand out links that are wrong without a single error.
 */
@ConfigurationProperties("affiliate")
data class AppProperties(
    val partnerCurrency: String,

    val cpaMinDeposit: BigDecimal,

    val payoutNetworks: List<String>,

    val siteUrl: String,

    val supportTelegramUrl: String,

    val postbackSourceIp: String,

    val clientIpHeader: String,

    val promoManifestUrl: String,

    val session: Session,

    val events: Events,

    val currency: Currency,
) {

    init {
        require(CURRENCY_CODE.matches(partnerCurrency)) {
            "PARTNER_CURRENCY must be an upper-case currency code of 2 to 10 characters, got '$partnerCurrency'"
        }
        require(cpaMinDeposit.signum() >= 0 && cpaMinDeposit.stripTrailingZeros().scale() <= NANO_DIGITS) {
            "CPA_MIN_DEPOSIT must be a non-negative decimal with at most $NANO_DIGITS fraction digits, got '$cpaMinDeposit'"
        }
        require(payoutNetworks.isNotEmpty() && payoutNetworks.all { NETWORK.matches(it) }) {
            "PAYOUT_NETWORKS must be a comma-separated list of upper-case network names, got '$payoutNetworks'"
        }
        require(payoutNetworks.toSet().size == payoutNetworks.size) { "PAYOUT_NETWORKS lists a network twice: $payoutNetworks" }
        require(isWebUrl(siteUrl) && URI(siteUrl).rawQuery == null && URI(siteUrl).rawFragment == null) {
            "SITE_URL must be an absolute http(s) origin without query or fragment, got '$siteUrl'"
        }
        require(supportTelegramUrl.isEmpty() || isWebUrl(supportTelegramUrl)) {
            "SUPPORT_TELEGRAM_URL must be empty or an absolute http(s) URL"
        }
        require(postbackSourceIp.isEmpty() || IPV4.matches(postbackSourceIp) || IPV6.matches(postbackSourceIp)) {
            "POSTBACK_SOURCE_IP must be empty or one IP address, got '$postbackSourceIp'"
        }
        require(clientIpHeader.isEmpty() || HEADER_NAME.matches(clientIpHeader)) {
            "CLIENT_IP_HEADER must be empty or one HTTP header name, got '$clientIpHeader'"
        }
        require(promoManifestUrl.isEmpty() || isWebUrl(promoManifestUrl)) {
            "PROMO_MANIFEST_URL must be empty or an absolute http(s) URL, got '$promoManifestUrl'"
        }
    }

    /** The site origin partner links are built on, without the trailing slash a hand-written env tends to carry. */
    val siteOrigin: String get() = siteUrl.trimEnd('/')

    val supportUrl: String? get() = supportTelegramUrl.ifEmpty { null }

    val postbackIp: String? get() = postbackSourceIp.ifEmpty { null }

    /** The header a trusted proxy in front of the app puts the client's address in; null trusts no header. */
    val trustedClientIpHeader: String? get() = clientIpHeader.ifEmpty { null }

    /** The promo library's catalogue; null when there is none, and the cabinet hides the Promo screen. */
    val promoManifest: String? get() = promoManifestUrl.ifEmpty { null }

    data class Session(
        val cookieSecure: Boolean,

        val ttlDays: Int,
    ) {
        init {
            require(ttlDays in 1..MAX_SESSION_DAYS) { "SESSION_TTL_DAYS must be 1..$MAX_SESSION_DAYS, got $ttlDays" }
        }
    }

    data class Events(
        /** The player events exchange (PAM_EXCHANGE): register.events and invoice.events. */
        val playerExchange: String,

        /** The casino events exchange (CASINO_EXCHANGE): spin.events and bet.events. */
        val casinoExchange: String,

        val queue: String,
    ) {
        init {
            require(playerExchange.isNotBlank()) { "PAM_EXCHANGE must not be empty" }
            require(casinoExchange.isNotBlank()) { "CASINO_EXCHANGE must not be empty" }
            require(queue.isNotBlank()) { "EVENT_QUEUE must not be empty" }
        }
    }

    data class Currency(
        val host: String,

        val port: Int,

        val plaintext: Boolean,
    ) {
        init {
            require(host.isNotBlank()) { "CURRENCY_GRPC_HOST must not be empty" }
            require(port in 1..MAX_PORT) { "CURRENCY_GRPC_PORT must be 1..$MAX_PORT, got $port" }
        }
    }

    private companion object {

        /** Balances are nano units of the partner currency, so a threshold finer than that cannot be compared. */
        const val NANO_DIGITS = 9

        const val MAX_SESSION_DAYS = 365

        const val MAX_PORT = 65535

        /** payout.currency is VARCHAR(10). */
        val CURRENCY_CODE = Regex("^[A-Z0-9]{2,10}$")

        /** payout.network is VARCHAR(16). */
        val NETWORK = Regex("^[A-Z0-9_]{2,16}$")

        val IPV4 = Regex("^((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])$")

        val IPV6 = Regex("^(?=.*:)[0-9a-fA-F:.]{2,45}$")

        val HEADER_NAME = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun isWebUrl(value: String): Boolean = try {
            val uri = URI(value)
            uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
        } catch (_: URISyntaxException) {
            false
        }
    }
}
