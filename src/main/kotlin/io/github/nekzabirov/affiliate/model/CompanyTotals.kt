package io.github.nekzabirov.affiliate.model

/** A company's all-time figures; [earned] is in nano units of the partner currency. */
data class CompanyTotals(
    val registrations: Long,

    val firstDeposits: Long,

    val earned: Long,
) {

    companion object {

        val ZERO = CompanyTotals(0, 0, 0)
    }
}
