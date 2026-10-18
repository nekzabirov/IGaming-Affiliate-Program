package io.github.nekzabirov.affiliate.model

/** How a company earns: a fixed amount per qualified first deposit, or a percent of deposits or of real-money turnover. */
enum class CompanyType {
    CPA,
    REVSHARE_DEPOSIT,
    REVSHARE_TURNOVER,
}
