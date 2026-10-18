package io.github.nekzabirov.affiliate.services

import io.github.nekzabirov.affiliate.config.AppProperties
import io.github.nekzabirov.affiliate.db.AccountStore
import io.github.nekzabirov.affiliate.db.PayoutStore
import io.github.nekzabirov.affiliate.errors.InsufficientBalanceException
import io.github.nekzabirov.affiliate.errors.ValidationException
import io.github.nekzabirov.affiliate.model.Account
import io.github.nekzabirov.affiliate.model.Page
import io.github.nekzabirov.affiliate.model.Payout
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * USDT withdrawals. The amount is RESERVED - debited from the balance - in the same transaction that records the
 * request, so the partner can never ask for the same money twice. Staff pay by hand and mark the row PAID, or
 * REJECTED with the balance refunded (README).
 */
@Service
class PayoutService(
    private val accounts: AccountStore,
    private val payouts: PayoutStore,
    private val properties: AppProperties,
) {

    fun list(account: Account, page: Int, size: Int): Page<Payout> {
        Paging.check(page, size)
        return payouts.page(account.id, page, size)
    }

    @Transactional
    fun request(account: Account, amount: String?, network: String?, address: String?): Payout {
        val value = Money.parsePositive(FIELD_AMOUNT, amount)
        val chain = checkNetwork(network)
        val wallet = checkAddress(chain, address)
        val nano = Money.toNano(value)
        if (!accounts.reserve(account.id, nano)) {
            throw InsufficientBalanceException()
        }
        val payout = payouts.insert(account.id, nano, properties.partnerCurrency, chain, wallet)
        log.atInfo()
            .addKeyValue("event", "payout.requested")
            .addKeyValue("accountId", account.id)
            .addKeyValue("payoutId", payout.id)
            .addKeyValue("amount", Money.format(nano))
            .addKeyValue("currency", payout.currency)
            .addKeyValue("network", chain)
            .log("Payout requested, amount reserved")
        return payout
    }

    private fun checkNetwork(network: String?): String {
        val name = network?.trim()?.uppercase().orEmpty()
        if (name.isEmpty()) {
            throw ValidationException(FIELD_NETWORK, ValidationException.REQUIRED, "network is required")
        }
        if (name !in properties.payoutNetworks) {
            throw ValidationException(
                FIELD_NETWORK,
                ValidationException.INVALID,
                "network must be one of ${properties.payoutNetworks.joinToString()}",
            )
        }
        return name
    }

    /** A malformed address is refused here: a payout sent to it by hand would be lost for good. */
    private fun checkAddress(network: String, address: String?): String {
        val value = address?.trim().orEmpty()
        if (value.isEmpty()) {
            throw ValidationException(FIELD_ADDRESS, ValidationException.REQUIRED, "address is required")
        }
        val format = ADDRESS_FORMATS[network] ?: OTHER_ADDRESS
        if (!format.matches(value)) {
            throw ValidationException(FIELD_ADDRESS, ValidationException.FORMAT, "address is not a valid $network address")
        }
        return value
    }

    private companion object {

        val log = LoggerFactory.getLogger(PayoutService::class.java)

        const val FIELD_AMOUNT = "amount"

        const val FIELD_NETWORK = "network"

        const val FIELD_ADDRESS = "address"

        val EVM_ADDRESS = Regex("^0x[0-9a-fA-F]{40}$")

        val ADDRESS_FORMATS = mapOf(
            "TRC20" to Regex("^T[1-9A-HJ-NP-Za-km-z]{33}$"),
            "ERC20" to EVM_ADDRESS,
            "BEP20" to EVM_ADDRESS,
            "TON" to Regex("^(?:EQ|UQ)[A-Za-z0-9_-]{46}$"),
        )

        /** A network added to PAYOUT_NETWORKS without a format here: at least a plausible length of printable text. */
        val OTHER_ADDRESS = Regex("^[\\x21-\\x7E]{10,128}$")
    }
}
