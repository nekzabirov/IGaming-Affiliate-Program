package io.github.nekzabirov.affiliate.events

/** A player's fact as the platform publishes it: `{"playerId": 7, "data": {...}}`, read by [EventReader]. */
data class Envelope<T>(
    val playerId: Long,

    val data: T,
)
