package io.github.nekzabirov.affiliate.model

/**
 * How a player is tied to a company: the brand site stores `?affiliate=<tag>` in the player service as the
 * player's affiliate tag, and `?click_id=` in the player's affiliate attribution. The tag is the company's identity
 * itself, with no prefix: every tag is looked up among the companies.
 */
object AffiliateTag {

    /** The query parameter, and the attribution key, carrying the partner tracker's click id. */
    const val CLICK_PARAM = "click_id"

    /**
     * The link a partner puts in their tracker's offer: `{subid}` is Keitaro's macro for its click id, which the
     * tracker substitutes on every click.
     */
    fun link(siteOrigin: String, identity: String): String = "$siteOrigin/?affiliate=$identity&$CLICK_PARAM={subid}"
}
