package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.clients.CurrencyClient
import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.db.AccountStore
import io.github.nekzabirov.affiliate.db.ActivityStore
import io.github.nekzabirov.affiliate.db.CommissionStore
import io.github.nekzabirov.affiliate.db.CompanyStore
import io.github.nekzabirov.affiliate.db.FactStore
import io.github.nekzabirov.affiliate.db.ReferralStore
import io.github.nekzabirov.affiliate.model.AffiliateTag
import io.github.nekzabirov.affiliate.model.Company
import io.github.nekzabirov.affiliate.model.CompanyStatus
import io.github.nekzabirov.affiliate.model.CompanyType
import io.github.nekzabirov.affiliate.model.OwnedCompany
import io.github.nekzabirov.affiliate.model.ProcessedFact
import io.github.nekzabirov.affiliate.model.Referral
import io.github.nekzabirov.affiliate.model.Registration
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import org.slf4j.LoggerFactory
import org.slf4j.spi.LoggingEventBuilder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * What the platform's facts earn partners: attribution of new players, first deposits, CPA and revenue-share
 * commissions, real-money turnover and its rollbacks, and the postbacks that tell the partners' trackers.
 *
 * Every fact is acted on once: its key goes into `processed_fact` in the same transaction as its effects, so a
 * redelivery finds it there and changes nothing. Amounts are converted to the partner currency BEFORE the
 * transaction (a remote call never holds a database transaction open) and postbacks go out AFTER it committed
 * (a tracker never hears of a conversion that was rolled back). A fact accrues only for an ACTIVE company of an
 * active partner; a reversal applies whatever their status is by then, because it corrects an accrual already made.
 * The trackers hear of registrations, deposits and stakes with the deposit or the whole stake as the payout, never
 * the commission.
 *
 * The facts of one player come in no guaranteed order: the routes travel apart, a rollout splits the queue between
 * two pods, and a fact that failed waits in the dead queue until it is replayed. A deposit may come before its
 * registration and a rollback before its stake, and either order ends the same.
 */
@Service
class AccrualService(
    private val referrals: ReferralStore,
    private val companies: CompanyStore,
    private val facts: FactStore,
    private val activity: ActivityStore,
    private val commissions: CommissionStore,
    private val accounts: AccountStore,
    private val currency: CurrencyClient,
    private val postbacks: PostbackDispatcher,
    transactionManager: PlatformTransactionManager,
    properties: AppProperties,
) {

    private val transactions = TransactionTemplate(transactionManager)

    /** CPA_MIN_DEPOSIT in the unit deposits are compared in. */
    private val cpaMinDeposit = Money.toNano(properties.cpaMinDeposit)

    /**
     * A new player: one who arrived with `partner:<identity>` of an ACTIVE company of an active partner becomes
     * that company's referral, with the tracker's click id from the attribution, and the trackers hear of a lead.
     */
    fun register(player: Registration) {
        attribute(player, VIA_REGISTRATION)
    }

    /**
     * A deposit that landed, of a referral: the first one is recorded on the referral, every one counts in the
     * day's activity, and the company's terms decide the commission - CPA once per player, on a first deposit of
     * at least CPA_MIN_DEPOSIT; REVSHARE_DEPOSIT a percent of every deposit. The trackers hear of a deposit with
     * its whole amount as the payout. [player] is the invoice's own copy of its owner, which attributes
     * the player when their registration has not been handled yet.
     */
    fun deposit(player: Registration, invoiceId: Long, amount: Long, currencyCode: String, at: Instant) {
        if (amount <= 0) return
        val playerId = player.playerId
        val referral = referrals.findByPlayer(playerId) ?: attributedAtDeposit(player) ?: return
        val owned = companyOf(referral)
        val refusal = refusal(owned)
        if (refusal != null) {
            ignored(log.atInfo(), "accrual.deposit_ignored", playerId, owned.company.identity, refusal)
            return
        }
        val company = owned.company
        val converted = currency.toPartnerCurrency(amount, currencyCode)
        val day = dayOf(at)
        val counted = transactions.execute {
            if (!facts.insert(factOf(INVOICE + invoiceId, company.id, playerId, day))) return@execute null
            val first = referrals.markFirstDeposit(playerId, invoiceId, converted, at)
            activity.addDeposit(playerId, day, company.id, converted)
            val commission = depositCommission(company, playerId, invoiceId, converted, first, day)
            if (commission != 0L) accounts.addToBalance(company.accountId, commission)
            Deposit(first, commission)
        }
        if (counted == null) {
            duplicate(INVOICE + invoiceId)
            return
        }
        log.atInfo()
            .addKeyValue("event", "accrual.deposit_counted")
            .addKeyValue("playerId", playerId)
            .addKeyValue("company", company.identity)
            .addKeyValue("invoiceId", invoiceId)
            .addKeyValue("amount", Money.format(converted))
            .addKeyValue("first", counted.first)
            .addKeyValue("commission", Money.format(counted.commission))
            .log("Deposit counted")
        postbacks.dispatch(company.id, PostbackEvent(PostbackEvent.Kind.DEPOSIT, playerId, referral.clickId, invoiceId, amount, currencyCode))
    }

    /**
     * A stake (a spin's PLACE, a sportsbook BET) of a referral, leg [legId] keyed [fact]. Only [realAmount] earns
     * (owner rule: a partner earns on the player's own money): it adds to the day's turnover and, for
     * REVSHARE_TURNOVER, its percent to the player's commission row of that day. [fact] keeps both amounts, so a
     * ROLLBACK takes back exactly them. The trackers hear of the stake with [amount], the whole leg, as the payout -
     * a stake of lock or bonus money too.
     */
    fun stake(fact: String, kind: PostbackEvent.Kind, legId: Long, playerId: Long, realAmount: Long, amount: Long, currencyCode: String, at: Instant) {
        if (amount <= 0) return
        val referral = referrals.findByPlayer(playerId) ?: return
        val owned = companyOf(referral)
        val refusal = refusal(owned)
        if (refusal != null) {
            // Every stake of such a player would say so; that volume is not worth an INFO line.
            ignored(log.atDebug(), "accrual.stake_ignored", playerId, owned.company.identity, refusal)
            return
        }
        val company = owned.company
        val converted = if (realAmount > 0) currency.toPartnerCurrency(realAmount, currencyCode) else 0L
        val day = dayOf(at)
        val commission = if (company.type == CompanyType.REVSHARE_TURNOVER) Money.percentOf(converted, company.amount) else 0L
        // A share of 0 (terms of 0, or under half a nano) names no row: the ledger gets no line of nothing, and the
        // fact records no source, so its rollback takes back the turnover alone.
        val source = if (commission != 0L) "$TURNOVER_SOURCE${company.id}:$playerId:$day" else null
        val counted = transactions.execute {
            if (!facts.insert(ProcessedFact(fact, converted, commission, source, company.id, playerId, day))) return@execute false
            if (converted != 0L) activity.addTurnover(playerId, day, company.id, converted)
            if (source != null) {
                commissions.accrue(company.accountId, company.id, playerId, CompanyType.REVSHARE_TURNOVER, commission, source, day)
                accounts.addToBalance(company.accountId, commission)
            }
            true
        } == true
        if (!counted) {
            duplicate(fact)
            return
        }
        log.atDebug()
            .addKeyValue("event", "accrual.stake_counted")
            .addKeyValue("fact", fact)
            .addKeyValue("playerId", playerId)
            .addKeyValue("company", company.identity)
            .addKeyValue("turnover", Money.format(converted))
            .addKeyValue("commission", Money.format(commission))
            .log("Stake counted")
        postbacks.dispatch(company.id, PostbackEvent(kind, playerId, referral.clickId, legId, amount, currencyCode))
    }

    /**
     * A ROLLBACK [fact] of the stake [stake], dated [at] (its round's or its bet's time, as the stake was): takes
     * back exactly what that stake added - its turnover on its day, its commission on its row and from the
     * balance - with the amounts recorded then, so neither today's rate nor today's terms make the reversal differ
     * from the accrual.
     *
     * The rollback may be handled before its stake: the stake can wait in the other pod's prefetch during a
     * rollout, or in the dead queue after a refusal of the currency service, while the rollback, which needs no
     * conversion, is acked - and a game engine sends one rollback per leg, never a second. So a rollback that finds
     * no counted stake records the stake's key itself, empty: the stake meets it whenever it comes and never
     * counts, as if it had been counted and reversed. A stake committing on another pod at that moment makes that
     * insert wait for it, and is then reversed like any counted one. A stake that was never going to count (not a
     * referral's, a company that was not active) loses nothing by it.
     */
    fun reverse(fact: String, stake: String, at: Instant) {
        val day = dayOf(at)
        val reversal = checkNotNull(
            transactions.execute<Reversal> {
                if (!facts.insert(ProcessedFact(fact, 0L, 0L, null, null, null, day))) return@execute Reversal.Repeated
                if (facts.insert(ProcessedFact(stake, 0L, 0L, null, null, null, day))) return@execute Reversal.Voided
                // Read here, inside the transaction, after the insert above waited for whoever wrote that key.
                val staked = facts.find(stake)
                val companyId = staked?.companyId
                val playerId = staked?.playerId
                if (staked == null || companyId == null || playerId == null || (staked.turnover == 0L && staked.commission == 0L)) {
                    // Another rollback's empty key, a stake that added nothing, or one the daily purge took meanwhile.
                    return@execute Reversal.Voided
                }
                activity.addTurnover(playerId, staked.day, companyId, -staked.turnover)
                if (staked.commission != 0L) {
                    val source = checkNotNull(staked.commissionSource) { "Fact $stake added commission without a source" }
                    val accountId = commissions.reverse(source, staked.commission)
                        ?: error("Commission $source that fact $stake added to is gone")
                    accounts.addToBalance(accountId, -staked.commission)
                }
                facts.update(ProcessedFact(fact, -staked.turnover, -staked.commission, staked.commissionSource, companyId, playerId, staked.day))
                Reversal.Reversed(staked)
            },
        )
        when (reversal) {
            Reversal.Repeated -> duplicate(fact)
            Reversal.Voided ->
                // Every rollback of a stake nobody counted ends here, a referral's or not: not worth an INFO line.
                log.atDebug()
                    .addKeyValue("event", "accrual.stake_voided")
                    .addKeyValue("fact", fact)
                    .addKeyValue("stake", stake)
                    .log("No counted stake to roll back; the stake can no longer count")
            is Reversal.Reversed ->
                log.atInfo()
                    .addKeyValue("event", "accrual.stake_reversed")
                    .addKeyValue("fact", fact)
                    .addKeyValue("stake", stake)
                    .addKeyValue("playerId", reversal.stake.playerId)
                    .addKeyValue("turnover", Money.format(reversal.stake.turnover))
                    .addKeyValue("commission", Money.format(reversal.stake.commission))
                    .log("Stake rolled back")
        }
    }

    /**
     * Decides, once per player, whether a partner brought them; the `register:<player>` fact is that decision. A
     * `partner:` tag it refuses (no such company, one not ACTIVE, a blocked partner) is recorded as refused too, so
     * a company approved later never claims the player through a deposit. Any other tag is not this programme's
     * and leaves nothing behind. [via] names the route that decided, for the log.
     */
    private fun attribute(player: Registration, via: String) {
        val playerId = player.playerId
        val identity = identityOf(player.affiliateTag) ?: return
        val key = REGISTER + playerId
        val day = dayOf(player.registeredAt)
        val owned = companies.findWithOwner(identity)
        val refusal = if (owned == null) UNKNOWN_COMPANY else refusal(owned)
        if (owned == null || refusal != null) {
            if (facts.insert(factOf(key, null, playerId, day))) {
                ignored(log.atInfo(), "accrual.referral_ignored", playerId, identity, refusal)
            } else {
                duplicate(key)
            }
            return
        }
        val company = owned.company
        val clickId = player.attribution[AffiliateTag.CLICK_PARAM]?.takeIf { CLICK_ID.matches(it) }
        val attributed = transactions.execute {
            facts.insert(factOf(key, company.id, playerId, day)) &&
                referrals.insert(playerId, company.id, clickId, player.attribution, countryCode(player.country), player.registeredAt)
        } == true
        if (!attributed) {
            duplicate(key)
            return
        }
        log.atInfo()
            .addKeyValue("event", "accrual.referral_created")
            .addKeyValue("playerId", playerId)
            .addKeyValue("company", company.identity)
            .addKeyValue("clickId", clickId != null)
            .addKeyValue("via", via)
            .log("Player attributed to a company")
        postbacks.dispatch(
            company.id,
            PostbackEvent(PostbackEvent.Kind.REGISTRATION, playerId, clickId, null, 0L, PostbackDispatcher.POSTBACK_CURRENCY),
        )
    }

    /**
     * The referral a deposit makes of its own copy of the player, when their registration was not handled yet:
     * register.events travels apart from invoice.events, and a registration held on another pod during a rollout,
     * or dead-lettered and replayed later, would otherwise lose the first deposit - the FTD and the CPA - for good,
     * and make the next deposit the "first". The registration's own rules decide, under its own fact, so whichever
     * of the two comes first attributes the player and the other changes nothing. The invoice carries the player's
     * tag as it is now, which an operator may have edited since the registration. Stakes cannot do the same: a spin
     * or a bet carries no tag.
     */
    private fun attributedAtDeposit(player: Registration): Referral? {
        if (identityOf(player.affiliateTag) == null || facts.exists(REGISTER + player.playerId)) return null
        attribute(player, VIA_DEPOSIT)
        // The registration may have been handled on another pod at the same moment and attributed the player itself.
        return referrals.findByPlayer(player.playerId)
    }

    /**
     * The commission this deposit earns (0 for none), written once per source; the caller credits the balance.
     * One that comes to 0 - terms of 0, or a share under half a nano - writes no ledger row: the ledger lists what
     * the partner earned, and the fact's own key already keeps the deposit from counting twice.
     */
    private fun depositCommission(company: Company, playerId: Long, invoiceId: Long, converted: Long, first: Boolean, day: LocalDate): Long =
        when (company.type) {
            CompanyType.CPA -> {
                val amount = Money.toNano(company.amount)
                val earned = first && converted >= cpaMinDeposit && amount != 0L &&
                    commissions.insertOnce(company.accountId, company.id, playerId, CompanyType.CPA, amount, CPA_SOURCE + playerId, day)
                if (earned) amount else 0L
            }
            CompanyType.REVSHARE_DEPOSIT -> {
                val amount = Money.percentOf(converted, company.amount)
                val source = DEPOSIT_SOURCE + invoiceId
                val earned = amount != 0L &&
                    commissions.insertOnce(company.accountId, company.id, playerId, CompanyType.REVSHARE_DEPOSIT, amount, source, day)
                if (earned) amount else 0L
            }
            CompanyType.REVSHARE_TURNOVER -> 0L
        }

    private fun companyOf(referral: Referral): OwnedCompany =
        checkNotNull(companies.findWithOwner(referral.companyId)) { "Company ${referral.companyId} of player ${referral.playerId} is gone" }

    /** Why [owned] may not accrue, or null when it may: only an ACTIVE company of an active partner earns. */
    private fun refusal(owned: OwnedCompany): String? = when {
        owned.company.status != CompanyStatus.ACTIVE -> COMPANY_NOT_ACTIVE
        !owned.owner.active -> OWNER_BLOCKED
        else -> null
    }

    private fun ignored(line: LoggingEventBuilder, event: String, playerId: Long, company: String, reason: String?) {
        line.addKeyValue("event", event)
            .addKeyValue("playerId", playerId)
            .addKeyValue("company", company)
            .addKeyValue("reason", reason)
            .log("Fact not counted")
    }

    private fun duplicate(fact: String) {
        log.atDebug().addKeyValue("event", "accrual.duplicate").addKeyValue("fact", fact).log("Fact already counted")
    }

    /** What a deposit came to: whether it was the player's first, and the commission it earned. */
    private data class Deposit(val first: Boolean, val commission: Long)

    /** What a rollback came to. */
    private sealed interface Reversal {

        /** This rollback was handled before. */
        data object Repeated : Reversal

        /** There was no counted stake: its key now holds nothing, and the stake can never count. */
        data object Voided : Reversal

        /** The amounts the stake recorded were taken back. */
        data class Reversed(val stake: ProcessedFact) : Reversal
    }

    companion object {

        // The keys of processed_fact: one per fact the platform publishes, by the publisher's own id.

        const val REGISTER = "register:"

        const val INVOICE = "invoice:"

        const val SPIN = "spin:"

        const val BET = "bet:"

        /**
         * The facts kept only as long as the broker could still replay them: spins, the bulk of the traffic, whose
         * rollbacks follow their stakes within seconds. Sportsbook facts stay, with registrations and invoices: a
         * bet can be voided weeks after it was placed (a long-dated or outright market), and its rollback must
         * still find what the bet added. They are few.
         */
        val PURGED_FACTS = listOf(SPIN)

        // Which route attributed a player, for the log: normally the registration, or a deposit that came first.

        private const val VIA_REGISTRATION = "register.events"

        private const val VIA_DEPOSIT = "invoice.events"

        // The sources of commission rows: one CPA per player, one share per deposit, one turnover row per day.

        private const val CPA_SOURCE = "cpa:"

        private const val DEPOSIT_SOURCE = "deposit:"

        private const val TURNOVER_SOURCE = "turnover:"

        private const val UNKNOWN_COMPANY = "unknown_company"

        private const val COMPANY_NOT_ACTIVE = "company_not_active"

        private const val OWNER_BLOCKED = "owner_blocked"

        /** What a tracker's click id may look like; anything else is never passed on to a partner's tracker. */
        private val CLICK_ID = Regex("^[A-Za-z0-9._~-]{1,128}$")

        /** referral.country is two letters; a longer code would fail the insert and lose the whole registration. */
        private val COUNTRY = Regex("^[A-Z]{2}$")

        private val log = LoggerFactory.getLogger(AccrualService::class.java)

        /** `partner:<identity>`, the prefix in any letter case, gives the identity; any other tag is not this programme's. */
        private fun identityOf(tag: String?): String? =
            tag?.takeIf { it.startsWith(AffiliateTag.PREFIX, ignoreCase = true) }?.substring(AffiliateTag.PREFIX.length)?.lowercase()

        private fun countryCode(country: String?): String? = country?.uppercase()?.takeIf { COUNTRY.matches(it) }

        private fun dayOf(at: Instant): LocalDate = LocalDate.ofInstant(at, ZoneOffset.UTC)

        private fun factOf(key: String, companyId: Long?, playerId: Long, day: LocalDate) =
            ProcessedFact(key, 0L, 0L, null, companyId, playerId, day)
    }
}
