package io.github.nekzabirov.affiliate.rest

import io.github.nekzabirov.affiliate.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AdminTest : IntegrationTest() {

    @Test
    fun `a partner gets FORBIDDEN on every admin endpoint`() {
        val session = api.register(uniqueLogin("quinn"))

        listOf(
            api.get("/api/admin/companies", session),
            api.get("/api/admin/partners", session),
            api.patch("/api/admin/companies/any", mapOf("status" to "ACTIVE"), session),
            api.patch("/api/admin/partners/1", mapOf("active" to false), session),
            // The container decodes the path before the guard looks at it.
            api.get("/api/%61dmin/companies", session),
        ).forEach {
            assertThat(it.status).describedAs(it.toString()).isEqualTo(403)
            assertThat(it.code).isEqualTo("FORBIDDEN")
        }
    }

    @Test
    fun `the review queue lists IN_REVIEW first, and an admin approves with the final amount`() {
        val partner = uniqueLogin("rose")
        val partnerSession = api.register(partner)
        api.post("/api/companies", mapOf("identity" to "older-active", "type" to "CPA", "amount" to "30"), partnerSession)
        api.post("/api/companies", mapOf("identity" to "waiting", "type" to "REVSHARE_DEPOSIT", "amount" to "40"), partnerSession)
        jdbc.sql("UPDATE company SET status = 'ACTIVE', created_at = now() + interval '1 minute' WHERE identity = 'older-active'").update()
        val adminLogin = uniqueLogin("root")
        val admin = newClient().register(adminLogin)
        makeAdmin(adminLogin)

        val queue = api.get("/api/admin/companies", admin)

        assertThat(queue.status).isEqualTo(200)
        assertThat(queue.body["total"].asLong()).isEqualTo(2)
        assertThat(queue.body["items"].values().map { it["identity"].asString() }).containsExactly("waiting", "older-active")
        val first = queue.body["items"][0]
        assertThat(first["status"].asString()).isEqualTo("IN_REVIEW")
        assertThat(first["owner"]["login"].asString()).isEqualTo(partner)
        assertThat(first["owner"]["active"].asBoolean()).isTrue()
        assertThat(first["link"].asString()).isEqualTo("https://example.com/?affiliate=waiting&click_id={subid}")
        assertThat(api.get("/api/admin/companies?status=ACTIVE", admin).body["items"].values().map { it["identity"].asString() })
            .containsExactly("older-active")
        assertThat(api.get("/api/admin/companies?q=${partner.uppercase()}", admin).body["total"].asLong()).isEqualTo(2)
        assertThat(api.get("/api/admin/companies?q=wait", admin).body["total"].asLong()).isEqualTo(1)
        assertThat(api.get("/api/admin/companies?q=%25", admin).body["total"].asLong()).isZero()

        val approved = api.patch("/api/admin/companies/waiting", mapOf("status" to "ACTIVE", "amount" to "35.5"), admin)

        assertThat(approved.status).isEqualTo(200)
        assertThat(approved.body["status"].asString()).isEqualTo("ACTIVE")
        assertThat(approved.body["amount"].asString()).isEqualTo("35.5")
        val seenByPartner = api.get("/api/companies/waiting", partnerSession)
        assertThat(seenByPartner.body["status"].asString()).isEqualTo("ACTIVE")
        assertThat(seenByPartner.body["amount"].asString()).isEqualTo("35.5")

        val tooMuch = api.patch("/api/admin/companies/waiting", mapOf("amount" to "120"), admin)
        assertThat(tooMuch.field to tooMuch.rule).isEqualTo("amount" to "max")
        val badStatus = api.patch("/api/admin/companies/waiting", mapOf("status" to "DELETED"), admin)
        assertThat(badStatus.field to badStatus.rule).isEqualTo("status" to "invalid")
        assertThat(api.patch("/api/admin/companies/nothing", mapOf("status" to "ACTIVE"), admin).status).isEqualTo(404)
    }

    @Test
    fun `an admin sets terms of zero for every type, and never below zero`() {
        val partnerSession = api.register(uniqueLogin("tina"))
        val companies = listOf("gift-cpa" to "CPA", "gift-deposits" to "REVSHARE_DEPOSIT", "gift-turnover" to "REVSHARE_TURNOVER")
        companies.forEach { (identity, type) ->
            api.post("/api/companies", mapOf("identity" to identity, "type" to type, "amount" to "10"), partnerSession)
        }
        val adminLogin = uniqueLogin("chief")
        val admin = newClient().register(adminLogin)
        makeAdmin(adminLogin)

        companies.forEach { (identity, _) ->
            listOf(mapOf("status" to "ACTIVE", "amount" to "0.00"), mapOf("amount" to "0")).forEach { body ->
                val reviewed = api.patch("/api/admin/companies/$identity", body, admin)
                assertThat(reviewed.status).describedAs(reviewed.toString()).isEqualTo(200)
                assertThat(reviewed.body["amount"].asString()).isEqualTo("0")
                assertThat(reviewed.body["status"].asString()).isEqualTo("ACTIVE")
            }
            assertThat(api.get("/api/companies/$identity", partnerSession).body["amount"].asString()).isEqualTo("0")

            listOf("-1", "-0.01").forEach { amount ->
                val negative = api.patch("/api/admin/companies/$identity", mapOf("amount" to amount), admin)
                assertThat(negative.status).describedAs(negative.toString()).isEqualTo(400)
                assertThat(negative.field to negative.rule).isEqualTo("amount" to "format")
            }
        }
        assertThat(jdbc.sql("SELECT count(*) FROM company WHERE amount = 0 AND status = 'ACTIVE'").query(Long::class.javaObjectType).single())
            .isEqualTo(3)
    }

    @Test
    fun `an admin blocks and unblocks a partner, but never themself`() {
        val partner = uniqueLogin("sam")
        val partnerSession = api.register(partner)
        api.post("/api/companies", mapOf("identity" to "sams-co", "type" to "CPA", "amount" to "10"), partnerSession)
        val adminLogin = uniqueLogin("boss")
        val admin = newClient().register(adminLogin)
        makeAdmin(adminLogin)
        val partnerId = accountId(partner)

        val partners = api.get("/api/admin/partners?q=${partner.take(3)}", admin)
        val row = partners.body["items"].first { it["login"].asString() == partner }
        assertThat(row["companies"].asLong()).isEqualTo(1)
        assertThat(row["role"].asString()).isEqualTo("PARTNER")
        assertThat(row["balance"].asString()).isEqualTo("0")

        val blocked = api.patch("/api/admin/partners/$partnerId", mapOf("active" to false), admin)
        assertThat(blocked.status).isEqualTo(200)
        assertThat(blocked.body["active"].asBoolean()).isFalse()
        assertThat(api.get("/api/me", partnerSession).code).isEqualTo("ACCOUNT_BLOCKED")

        assertThat(api.patch("/api/admin/partners/$partnerId", mapOf("active" to true), admin).body["active"].asBoolean()).isTrue()
        assertThat(api.get("/api/me", partnerSession).status).isEqualTo(200)

        val self = api.patch("/api/admin/partners/${accountId(adminLogin)}", mapOf("active" to false), admin)
        assertThat(self.status).isEqualTo(409)
        assertThat(self.code).isEqualTo("SELF_BLOCK")
        assertThat(api.patch("/api/admin/partners/999999", mapOf("active" to false), admin).status).isEqualTo(404)
        assertThat(api.patch("/api/admin/partners/$partnerId", mapOf("active" to "no"), admin).status).isEqualTo(400)
        listOf(emptyMap<String, Any?>(), mapOf("active" to null)).forEach { body ->
            val missing = api.patch("/api/admin/partners/$partnerId", body, admin)
            assertThat(missing.status).describedAs(missing.toString()).isEqualTo(400)
            assertThat(missing.field to missing.rule).isEqualTo("active" to "required")
        }
    }
}
