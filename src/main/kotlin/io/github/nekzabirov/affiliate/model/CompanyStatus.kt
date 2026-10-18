package io.github.nekzabirov.affiliate.model

/** A company is born IN_REVIEW and only an admin moves it; only an ACTIVE one attributes players and accrues. */
enum class CompanyStatus {
    IN_REVIEW,
    ACTIVE,
    DISABLED,
}
