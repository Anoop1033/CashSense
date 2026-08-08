package com.cashsense.app.data

import com.cashsense.app.domain.ParsedTransaction
import com.cashsense.app.domain.TransactionDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Transaction(
    val id: Long,
    val amountPaise: Long,
    val direction: TransactionDirection,
    val source: String,
    val sourcePackage: String?,
    val note: String?,
    val rawText: String?,
    val timestampMillis: Long
)

private fun TransactionEntity.toDomain() = Transaction(
    id = id,
    amountPaise = amountPaise,
    direction = if (direction == TxDirection.CREDIT) TransactionDirection.CREDIT else TransactionDirection.DEBIT,
    source = source,
    sourcePackage = sourcePackage,
    note = note,
    rawText = rawText,
    timestampMillis = timestampMillis
)

class WalletRepository(
    private val dao: TransactionDao,
    private val prefs: WalletPrefs
) {
    val hasOnboarded: Flow<Boolean> = prefs.hasOnboarded

    val confirmedTransactions: Flow<List<Transaction>> =
        dao.confirmedTransactions().map { list -> list.map { it.toDomain() } }

    val pendingTransactions: Flow<List<Transaction>> =
        dao.pendingTransactions().map { list -> list.map { it.toDomain() } }

    val balancePaise: Flow<Long> = confirmedTransactions.map { list ->
        list.sumOf { tx ->
            if (tx.direction == TransactionDirection.CREDIT) tx.amountPaise else -tx.amountPaise
        }.coerceAtLeast(0L)
    }

    suspend fun completeOnboarding(initialBalancePaise: Long) {
        dao.insert(
            TransactionEntity(
                amountPaise = initialBalancePaise,
                direction = TxDirection.CREDIT,
                status = TxStatus.CONFIRMED,
                source = TxSource.MANUAL,
                sourcePackage = null,
                note = "Starting balance",
                rawText = null,
                timestampMillis = System.currentTimeMillis()
            )
        )
        prefs.setOnboarded(true)
    }

    /**
     * Records a notification-detected transaction, unless it looks like another announcement of
     * one already seen.
     *
     * A single payment typically gets announced several times over: the bank's SMS, a caller-ID
     * app mirroring that SMS, and the bank's email all arrive separately, and Android re-posts a
     * notification every time it is updated. Since none of them carry a shared transaction ID we
     * could match on, an identical amount and direction landing inside [DUPLICATE_WINDOW_MILLIS]
     * is treated as the same payment rather than a new one.
     *
     * The trade-off is deliberate: genuinely paying the same amount twice inside that window
     * records only once (recoverable — the user can add the second by hand), whereas the reverse
     * default floods the review list with triplicates on every single payment.
     */
    suspend fun addPendingFromNotification(parsed: ParsedTransaction) {
        val now = System.currentTimeMillis()
        val alreadySeen = dao.countSimilarSince(
            amountPaise = parsed.amountPaise,
            direction = parsed.direction.name,
            sinceMillis = now - DUPLICATE_WINDOW_MILLIS
        ) > 0
        if (alreadySeen) return

        dao.insert(
            TransactionEntity(
                amountPaise = parsed.amountPaise,
                direction = parsed.direction.name,
                status = TxStatus.PENDING,
                source = TxSource.AUTO,
                sourcePackage = parsed.sourcePackage,
                note = null,
                rawText = parsed.rawText,
                timestampMillis = now
            )
        )
    }

    suspend fun confirmTransaction(
        id: Long,
        amountPaise: Long,
        direction: TransactionDirection,
        note: String?
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                amountPaise = amountPaise,
                direction = direction.name,
                status = TxStatus.CONFIRMED,
                note = note
            )
        )
    }

    suspend fun dismissTransaction(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = TxStatus.DISMISSED))
    }

    suspend fun addManualTransaction(amountPaise: Long, direction: TransactionDirection, note: String?) {
        dao.insert(
            TransactionEntity(
                amountPaise = amountPaise,
                direction = direction.name,
                status = TxStatus.CONFIRMED,
                source = TxSource.MANUAL,
                sourcePackage = null,
                note = note,
                rawText = null,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    /**
     * Records a payment made through CashSense's own "Pay via UPI" flow.
     * [confirmed] is false for an ambiguous outcome (the UPI app didn't return a clear
     * SUCCESS) — it lands as a normal pending review rather than being silently recorded.
     */
    suspend fun addUpiPayment(amountPaise: Long, payeeLabel: String, confirmed: Boolean) {
        dao.insert(
            TransactionEntity(
                amountPaise = amountPaise,
                direction = TxDirection.DEBIT,
                status = if (confirmed) TxStatus.CONFIRMED else TxStatus.PENDING,
                source = TxSource.UPI,
                sourcePackage = null,
                note = "Paid to $payeeLabel",
                rawText = null,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun resetWallet() {
        dao.clearAll()
        prefs.setOnboarded(false)
    }

    private companion object {
        /**
         * How long after recording a transaction an identical one is treated as an echo of it.
         * Sized for the slowest of the parallel announcements — a bank's email can trail its SMS
         * by a couple of minutes.
         */
        const val DUPLICATE_WINDOW_MILLIS = 3 * 60 * 1000L
    }
}
