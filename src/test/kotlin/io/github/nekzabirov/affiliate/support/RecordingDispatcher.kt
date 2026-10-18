package io.github.nekzabirov.affiliate.support

import io.github.nekzabirov.affiliate.clients.CurrencyClient
import io.github.nekzabirov.affiliate.db.PostbackStore
import io.github.nekzabirov.affiliate.postback.PostbackAdapters
import io.github.nekzabirov.affiliate.postback.PostbackEvent
import io.github.nekzabirov.affiliate.services.PostbackDispatcher
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The production dispatcher, which also remembers what it was asked to send and whether the caller was still
 * inside a transaction at that moment: a postback must leave only after its fact committed.
 */
class RecordingDispatcher(
    postbacks: PostbackStore,
    adapters: PostbackAdapters,
    http: CloseableHttpClient,
    currency: CurrencyClient,
) : PostbackDispatcher(postbacks, adapters, http, currency) {

    val dispatched = CopyOnWriteArrayList<Dispatch>()

    override fun dispatch(companyId: Long, event: PostbackEvent) {
        dispatched += Dispatch(companyId, event, TransactionSynchronizationManager.isActualTransactionActive())
        super.dispatch(companyId, event)
    }

    data class Dispatch(
        val companyId: Long,

        val event: PostbackEvent,

        val insideTransaction: Boolean,
    )
}
