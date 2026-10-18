package io.github.nekzabirov.affiliate.model

import java.time.Instant

/** A partner tracker that is told about a company's conversions, with the outcome of the last attempt. */
data class Postback(
    val id: Long,

    val companyId: Long,

    val type: PostbackType,

    /** The normalized base URL the adapter builds every request from. */
    val url: String,

    val active: Boolean,

    val lastSentAt: Instant?,

    /** HTTP status of the last attempt; null when there was none or it failed before a response. */
    val lastStatus: Int?,

    /** Short reason when the last attempt failed or was skipped; null after a delivery. */
    val lastError: String?,

    val createdAt: Instant,

    val updatedAt: Instant,
)
