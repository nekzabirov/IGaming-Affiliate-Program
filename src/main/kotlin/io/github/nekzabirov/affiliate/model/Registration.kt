package io.github.nekzabirov.affiliate.model

import java.time.Instant

/**
 * How a player arrived, as the player service's Player snapshot tells it: register.events carries the snapshot, and every
 * invoice embeds its owner's, so either fact can attribute the player. [attribution] is the free-form keys the brand
 * site sent along (the tracker's click id among them).
 */
data class Registration(
    val playerId: Long,

    val affiliateTag: String?,

    val attribution: Map<String, String>,

    val country: String?,

    val registeredAt: Instant,
)
