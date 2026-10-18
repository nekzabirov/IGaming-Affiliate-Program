package io.github.nekzabirov.affiliate.model

import java.time.LocalDate

/** What one company earned of one commission type on one UTC day, in nano units of the partner currency. */
data class CompanyDayCommission(
    val companyId: Long,

    val day: LocalDate,

    val type: CompanyType,

    val amount: Long,
)
