package io.github.nekzabirov.affiliate.postback

/**
 * A conversion to tell a partner's tracker about. [amount] is what the tracker hears as the payout, in nano units of
 * [currency]: the whole deposit, or the whole stake of a spin or a bet (every funding part, not only real money); 0
 * for a registration. The dispatcher converts it to the postback currency before an adapter sees it.
 */
data class PostbackEvent(
    val kind: Kind,

    val playerId: Long,

    /** The tracker's click id the player arrived with; without it no tracker can attribute the conversion. */
    val clickId: String?,

    /** The publisher's id of the fact: the invoice of a deposit, the leg of a spin or a bet; null for a registration. */
    val factId: Long?,

    val amount: Long,

    val currency: String,
) {

    enum class Kind {
        REGISTRATION,
        DEPOSIT,
        SPIN,
        BET,
    }
}
