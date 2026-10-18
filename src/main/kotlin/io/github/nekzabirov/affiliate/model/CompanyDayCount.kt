package io.github.nekzabirov.affiliate.model

import java.time.LocalDate

/** How many players did something (registered, made their first deposit) for one company on one UTC day. */
data class CompanyDayCount(
    val companyId: Long,

    val day: LocalDate,

    val count: Long,
)
