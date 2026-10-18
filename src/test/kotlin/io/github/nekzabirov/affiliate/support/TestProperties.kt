package io.github.nekzabirov.affiliate.support

import io.github.nekzabirov.affiliate.config.AppProperties
import java.math.BigDecimal

/** The app's settings for a test that builds its objects by hand, outside a Spring context. */
fun testProperties(): AppProperties = AppProperties(
    partnerCurrency = "USDT",
    cpaMinDeposit = BigDecimal.TEN,
    payoutNetworks = listOf("TRC20"),
    siteUrl = "https://example.com",
    supportTelegramUrl = "",
    postbackSourceIp = "",
    clientIpHeader = "",
    promoManifestUrl = "",
    session = AppProperties.Session(cookieSecure = true, ttlDays = 30),
    events = AppProperties.Events(playerExchange = "pam.events", casinoExchange = "casino.events", queue = "affiliate.events"),
    currency = AppProperties.Currency(host = "localhost", port = 443, plaintext = false),
)
