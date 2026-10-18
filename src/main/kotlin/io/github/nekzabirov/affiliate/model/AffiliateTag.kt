package io.github.nekzabirov.affiliate.model

/**
 * How a player is tied to a company: the brand site stores `?affiliate=<tag>` in the player service as the
 * player's affiliate tag, and `?click_id=` in the player's affiliate attribution. A tag of this programme is [PREFIX] + identity.
 */
object AffiliateTag {

    const val PREFIX = "partner:"

    /** The query parameter, and the attribution key, carrying the partner tracker's click id. */
    const val CLICK_PARAM = "click_id"

    fun of(identity: String): String = PREFIX + identity

    /**
     * The link a partner puts in their tracker's offer: `{subid}` is Keitaro's macro for its click id, which the
     * tracker substitutes on every click.
     */
    fun link(siteOrigin: String, identity: String): String = "$siteOrigin/?affiliate=${of(identity)}&$CLICK_PARAM={subid}"
}
