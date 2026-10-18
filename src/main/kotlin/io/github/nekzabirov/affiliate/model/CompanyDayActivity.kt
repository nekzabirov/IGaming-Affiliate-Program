package io.github.nekzabirov.affiliate.model

import java.time.LocalDate

/** One company's deposits and turnover on one UTC day, in nano units of the partner currency. */
data class CompanyDayActivity(
    val companyId: Long,

    val day: LocalDate,

    val depositCount: Long,

    val depositAmount: Long,

    val turnover: Long,
)
