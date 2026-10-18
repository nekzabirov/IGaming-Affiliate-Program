package io.github.nekzabirov.affiliate.rest

/**
 * What the cabinet needs before anyone signs in: the currency, the support link, how partner links are built,
 * and where the promo library is (null hides the Promo screen).
 */
data class ConfigResponse(
    val currency: String,

    val supportUrl: String?,

    val siteUrl: String,

    val tagPrefix: String,

    val clickParam: String,

    val cpaMinDeposit: String,

    val payoutNetworks: List<String>,

    val postbackSourceIp: String?,

    val postbackTypes: List<String>,

    val promoManifestUrl: String?,
)
