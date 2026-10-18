package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.postback.PostbackEvent
import io.github.nekzabirov.affiliate.support.FakeDns
import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * The dispatcher through the real HTTP client: the guard decides again at connect time, whatever the URL
 * looked like when it was saved, and every outcome lands on the postback row.
 */
class PostbackDispatcherTest : IntegrationTest() {

    @Autowired
    private lateinit var dispatcher: PostbackDispatcher

    private var company = 0L

    @BeforeEach
    fun company() {
        val session = api.register(uniqueLogin("xena"))
        api.post("/api/companies", mapOf("identity" to "dispatched", "type" to "CPA", "amount" to "10"), session)
        company = companyId("dispatched")
    }

    @Test
    fun `a player without a click id is recorded as skipped, without a request`() {
        val id = insertPostback(company, "https://${FakeDns.TRACKER}/k/postback")

        dispatcher.dispatch(company, event(clickId = null))

        val (status, error) = awaitAttempt(id)
        assertThat(status).isNull()
        assertThat(error).isEqualTo("Skipped: the player arrived without click_id")
    }

    @Test
    fun `a name that now resolves to a private address is never dialled`() {
        val rebound = insertPostback(company, "https://${FakeDns.INTERNAL}/k/postback")
        val gone = insertPostback(company, "https://${FakeDns.NOWHERE}/k/postback")
        val inactive = insertPostback(company, "https://${FakeDns.MIXED}/k/postback", active = false)

        dispatcher.dispatch(company, event(clickId = "abc"))

        assertThat(awaitAttempt(rebound)).isEqualTo(null to "Blocked address")
        assertThat(awaitAttempt(gone)).isEqualTo(null to "Unknown host")
        assertThat(attempted(inactive)).isFalse()
    }

    private fun event(clickId: String?) =
        PostbackEvent(PostbackEvent.Kind.REGISTRATION, playerId = 7, clickId = clickId, factId = null, amount = 0, currency = PostbackDispatcher.POSTBACK_CURRENCY)
}
