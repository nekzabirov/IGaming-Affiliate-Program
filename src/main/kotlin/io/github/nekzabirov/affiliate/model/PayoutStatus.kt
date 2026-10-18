package io.github.nekzabirov.affiliate.model

/** Staff move a payout by hand in the database; nothing in the app pays or rejects. */
enum class PayoutStatus {
    PENDING,
    PAID,
    REJECTED,
}
