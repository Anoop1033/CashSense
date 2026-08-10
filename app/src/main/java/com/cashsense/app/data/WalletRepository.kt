package com.cashsense.app.data

import com.cashsense.app.domain.ParsedTransaction
import com.cashsense.app.domain.TransactionDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val prefs: WalletPreferences
) {
    /**
     * Serialises detection so that checking for a duplicate and recording the transaction happen
     * as one step.
     *
     * Without this they race: apps re-post a notification the instant they update it, so two
     * handlers can run at once, both look for an existing copy, both find none because neither has
     * inserted yet, and both then insert. Observed on real payments as pairs of identical rows
     * milliseconds apart — inside the duplicate window, yet sailing straight through the check.
     */
    private val detectionLock = Mutex()

    val hasOnboarded: Flow<Boolean> = prefs.hasOnboarded

    val autoApplyDetected: Flow<Boolean> = prefs.autoApplyDetected

    suspend fun setAutoApplyDetected(value: Boolean) = prefs.setAutoApplyDetected(value)

    val lastSeenBalancePaise: Flow<Long?> = prefs.lastSeenBalancePaise

    suspend fun setLastSeenBalancePaise(value: Long) = prefs.setLastSeenBalancePaise(value)

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
     * Records a notification-detected transaction, unless it is another announcement of one
     * already seen.
     *
     * A single payment gets announced repeatedly: the bank's SMS, a caller-ID app mirroring that
     * SMS, the bank's email, and the UPI app's own notification — plus Android re-posting any of
     * them on update. Two checks separate those echoes from genuinely new payments:
     *
     *  1. **By the bank's reference.** Every message the bank originates quotes the same UPI
     *     reference, so matching on it is exact. Because it is exact, it can look back a full day
     *     and still catch an email that trailed its SMS by minutes.
     *
     *  2. **By amount within [FINGERPRINT_WINDOW_MILLIS].** UPI apps' own notifications quote no
     *     reference, so pairing one with the bank's SMS needs this fallback. It is a genuine
     *     guess, which is why the window is deliberately short — long enough for the two to
     *     arrive, short enough that paying the same amount twice a couple of minutes apart is
     *     still recorded as two payments.
     */
    suspend fun addPendingFromNotification(parsed: ParsedTransaction): Unit = detectionLock.withLock {
        val now = System.currentTimeMillis()

        val reference = parsed.referenceId
        if (reference != null) {
            val seenByReference =
                dao.countByReferenceSince(reference, now - REFERENCE_WINDOW_MILLIS) > 0
            if (seenByReference) return@withLock
        }

        val neighbours = dao.referencesOfSimilarSince(
            amountPaise = parsed.amountPaise,
            direction = parsed.direction.name,
            sinceMillis = now - FINGERPRINT_WINDOW_MILLIS
        )
        val looksLikeEcho = if (reference != null) {
            // Carrying a reference, this is only an echo of a neighbour that carries none — an
            // app like a UPI wallet that omits it. A neighbour quoting a *different* reference is
            // proof of a separate payment, so paying the same amount twice still records twice.
            neighbours.any { it == null }
        } else {
            neighbours.isNotEmpty()
        }
        if (looksLikeEcho) return@withLock

        // Applied straight to the balance unless the user asked to vet detections first. Nothing
        // is destroyed either way: an applied transaction can be removed again from History,
        // which is what makes trusting the parser by default reasonable.
        val autoApply = prefs.autoApplyDetected.first()

        dao.insert(
            TransactionEntity(
                amountPaise = parsed.amountPaise,
                direction = parsed.direction.name,
                status = if (autoApply) TxStatus.CONFIRMED else TxStatus.PENDING,
                source = TxSource.AUTO,
                sourcePackage = parsed.sourcePackage,
                note = null,
                rawText = parsed.rawText,
                timestampMillis = now,
                referenceId = reference
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
         * How far back to look for a matching bank reference. Generous because the match is an
         * exact identity, not a guess — the only cost of a long window is catching a slow email.
         */
        const val REFERENCE_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * How far back the amount-only fallback looks, for messages quoting no reference. Kept
         * short precisely because this one can be wrong: everything past it is treated as a
         * genuinely new payment.
         */
        const val FINGERPRINT_WINDOW_MILLIS = 90 * 1000L
    }
}
