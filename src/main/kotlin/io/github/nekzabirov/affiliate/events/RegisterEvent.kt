package io.github.nekzabirov.affiliate.events

import java.time.Instant

/**
 * `data` of register.events: the player service's whole Player snapshot on the wire, of which attribution reads
 * only these.
 * `affiliateTag` is what the brand site stored from `?affiliate=`, `affiliateAttribution` the free-form keys it
 * sent along (the tracker's `click_id` among them), `{}` or absent when there are none.
 */
data class RegisterEvent(
    val id: Long,

    val affiliateTag: String?,

    val affiliateAttribution: Map<String, String>?,

    val country: String?,

    val createdAt: Instant,
)
