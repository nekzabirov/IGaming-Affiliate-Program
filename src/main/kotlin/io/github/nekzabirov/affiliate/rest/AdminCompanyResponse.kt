package io.github.nekzabirov.affiliate.rest

import com.fasterxml.jackson.annotation.JsonUnwrapped
import io.github.nekzabirov.affiliate.model.CompanyReview
import io.github.nekzabirov.affiliate.model.CompanySummary

/** A company in the review queue: the partner's view of it, flattened, plus its [owner]. */
data class AdminCompanyResponse(
    @get:JsonUnwrapped
    val company: CompanyResponse,

    val owner: Owner,
) {

    data class Owner(
        val id: Long,

        val login: String,

        val active: Boolean,
    )

    companion object {

        fun of(review: CompanyReview, siteOrigin: String) = AdminCompanyResponse(
            company = CompanyResponse.of(CompanySummary(review.company, review.totals), siteOrigin),
            owner = Owner(review.owner.id, review.owner.login, review.owner.active),
        )
    }
}
