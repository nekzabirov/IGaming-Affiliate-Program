package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.model.AffiliateTag
import io.github.nekzabirov.affiliate.model.PostbackType
import io.github.nekzabirov.affiliate.services.Money
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ConfigController(private val properties: AppProperties) {

    @GetMapping("/config")
    fun config() = ConfigResponse(
        currency = properties.partnerCurrency,
        supportUrl = properties.supportUrl,
        siteUrl = properties.siteOrigin,
        tagPrefix = AffiliateTag.PREFIX,
        clickParam = AffiliateTag.CLICK_PARAM,
        cpaMinDeposit = Money.format(properties.cpaMinDeposit),
        payoutNetworks = properties.payoutNetworks,
        postbackSourceIp = properties.postbackIp,
        postbackTypes = PostbackType.entries.map { it.name },
        promoManifestUrl = properties.promoManifest,
    )
}
