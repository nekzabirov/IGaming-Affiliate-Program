package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.db.CompanyStore
import io.github.nekzabirov.affiliate.db.PostbackStore
import io.github.nekzabirov.affiliate.errors.LimitReachedException
import io.github.nekzabirov.affiliate.errors.NotFoundException
import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Postback
import io.github.nekzabirov.affiliate.model.PostbackType
import io.github.nekzabirov.affiliate.postback.PostbackAdapters
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.net.URI

/** A company's postbacks: what the partner configures, and the exact requests each one would send. */
@Service
class PostbackService(
    private val companies: CompanyService,
    private val companyStore: CompanyStore,
    private val postbacks: PostbackStore,
    private val adapters: PostbackAdapters,
    private val transactions: TransactionTemplate,
) {

    fun list(account: Account, identity: String): List<Postback> =
        postbacks.findByCompany(companies.owned(account, identity).id)

    /**
     * At most [MAX_PER_COMPANY] per company; the company row is locked so two racing creates cannot both slip
     * under. The URL is checked (a DNS lookup) before the transaction opens, never while it holds a connection.
     */
    fun create(account: Account, identity: String, type: String?, url: String?): Postback {
        val company = companies.owned(account, identity)
        val kind = checkType(type)
        val base = adapters.of(kind).normalize(url.orEmpty())
        val postback = checkNotNull(
            transactions.execute {
                companyStore.lock(company.id)
                if (postbacks.countByCompany(company.id) >= MAX_PER_COMPANY) {
                    throw LimitReachedException("A company can have at most $MAX_PER_COMPANY postbacks")
                }
                postbacks.insert(company.id, kind, base)
            },
        )
        log.atInfo()
            .addKeyValue("event", "postback.created")
            .addKeyValue("accountId", account.id)
            .addKeyValue("company", company.identity)
            .addKeyValue("postbackId", postback.id)
            .addKeyValue("type", kind)
            .log("Postback added")
        return postback
    }

    /** Switches it on or off and/or points it at a new URL; a new URL forgets the last attempt, which was for the old one. */
    fun update(account: Account, identity: String, id: Long, active: Boolean?, url: String?): Postback {
        val company = companies.owned(account, identity)
        val postback = postbacks.find(company.id, id) ?: throw NotFoundException("No such postback")
        val base = url?.let { adapters.of(postback.type).normalize(it) } ?: postback.url
        val updated = postbacks.update(postback.id, active ?: postback.active, base, forgetLastAttempt = base != postback.url)
        log.atInfo()
            .addKeyValue("event", "postback.updated")
            .addKeyValue("accountId", account.id)
            .addKeyValue("company", company.identity)
            .addKeyValue("postbackId", postback.id)
            .addKeyValue("active", updated.active)
            .log("Postback updated")
        return updated
    }

    fun delete(account: Account, identity: String, id: Long) {
        val company = companies.owned(account, identity)
        if (!postbacks.delete(company.id, id)) {
            throw NotFoundException("No such postback")
        }
        log.atInfo()
            .addKeyValue("event", "postback.deleted")
            .addKeyValue("accountId", account.id)
            .addKeyValue("company", company.identity)
            .addKeyValue("postbackId", id)
            .log("Postback deleted")
    }

    /**
     * The requests this postback would send for each kind of event, built by the real adapter from sample values:
     * the cabinet shows the partner precisely what their tracker will receive.
     */
    fun examples(postback: Postback): List<Pair<PostbackEvent.Kind, URI>> {
        val adapter = adapters.of(postback.type)
        return SAMPLES.map { it.kind to adapter.build(postback.url, it) }
    }

    private fun checkType(type: String?): PostbackType {
        val name = type?.trim()?.uppercase().orEmpty()
        if (name.isEmpty()) {
            throw ValidationException(FIELD_TYPE, ValidationException.REQUIRED, "type is required")
        }
        return PostbackType.entries.firstOrNull { it.name == name }
            ?: throw ValidationException(
                FIELD_TYPE,
                ValidationException.INVALID,
                "type must be one of ${PostbackType.entries.joinToString()}",
            )
    }

    private companion object {

        val log = LoggerFactory.getLogger(PostbackService::class.java)

        const val FIELD_TYPE = "type"

        const val MAX_PER_COMPANY = 10

        const val SAMPLE_PLAYER_ID = 100500L

        const val SAMPLE_CLICK_ID = "8f3k2a"

        const val SAMPLE_INVOICE_ID = 1001L

        const val SAMPLE_SPIN_ID = 5001L

        const val SAMPLE_BET_ID = 7001L

        val SAMPLE_DEPOSIT = Money.toNano(BigDecimal("100.00"))

        val SAMPLE_SPIN = Money.toNano(BigDecimal("1.00"))

        val SAMPLE_BET = Money.toNano(BigDecimal("10.00"))

        const val SAMPLE_CURRENCY = PostbackDispatcher.POSTBACK_CURRENCY

        val SAMPLES = listOf(
            PostbackEvent(PostbackEvent.Kind.REGISTRATION, SAMPLE_PLAYER_ID, SAMPLE_CLICK_ID, null, 0L, SAMPLE_CURRENCY),
            PostbackEvent(PostbackEvent.Kind.DEPOSIT, SAMPLE_PLAYER_ID, SAMPLE_CLICK_ID, SAMPLE_INVOICE_ID, SAMPLE_DEPOSIT, SAMPLE_CURRENCY),
            PostbackEvent(PostbackEvent.Kind.SPIN, SAMPLE_PLAYER_ID, SAMPLE_CLICK_ID, SAMPLE_SPIN_ID, SAMPLE_SPIN, SAMPLE_CURRENCY),
            PostbackEvent(PostbackEvent.Kind.BET, SAMPLE_PLAYER_ID, SAMPLE_CLICK_ID, SAMPLE_BET_ID, SAMPLE_BET, SAMPLE_CURRENCY),
        )
    }
}
