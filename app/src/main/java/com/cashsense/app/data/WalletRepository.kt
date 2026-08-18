package com.cashsense.app.data

import com.cashsense.app.domain.ParsedTransaction
import com.cashsense.app.domain.TransactionDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

            // The UPI app announces a payment immediately but quotes no reference; the bank's own
            // message follows with one, and can lag by minutes. Rather than treat the later, fuller
            // message as a second payment, it completes the provisional row the app left behind.
            // Only reference-less rows can be claimed this way — one quoting a different reference
            // is a genuinely separate payment, so paying the same amount twice still records twice.
            val provisional = dao.latestUnreferencedSimilarSince(
                amountPaise = parsed.amountPaise,
                direction = parsed.direction.name,
                sinceMillis = now - PROVISIONAL_CLAIM_WINDOW_MILLIS
            )
            if (provisional != null) {
                dao.attachReference(provisional, reference)
                return@withLock
            }
        } else {
            val neighbours = dao.referencesOfSimilarSince(
                amountPaise = parsed.amountPaise,
                direction = parsed.direction.name,
                sinceMillis = now - FINGERPRINT_WINDOW_MILLIS
            )
            if (neighbours.isNotEmpty()) return@withLock
        }

        // Applied straight to the balance unless the user asked to vet detections first — and
        // only when the message corroborated itself, by quoting a reference, naming the account,
        // or arriving from a payment app.
        //
        // Reading arbitrary notification text will occasionally misfire; a Spotify advert was
        // once recorded as a ₹799 payment. What matters is that a misfire cannot move money on
        // its own. An uncorroborated reading waits on the Wallet screen to be confirmed, so the
        // worst it can do is ask.
        val autoApply = prefs.autoApplyDetected.first() && parsed.corroborated

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

    /**
     * Re-anchors the wallet to the balance the bank actually reports, by recording the difference
     * as a single correcting entry. Returns the signed amount applied, in paise; zero when the two
     * already agree.
     *
     * The wallet's balance is a running total of detected events, and a running total can only ever
     * be as good as the events it saw. A payment made while the phone was off, a bank whose wording
     * nothing matches, a duplicate that slipped through before the reference check existed — any of
     * these leaves the figure drifted from the truth, with no way back short of deleting everything
     * and starting again. Re-entering the real balance costs one number and keeps the history.
     *
     * The difference is recorded rather than silently applied so that History still adds up: a
     * balance that changed with nothing to explain it would be a worse kind of wrong than the drift.
     */
    suspend fun correctBalanceTo(actualBalancePaise: Long): Long {
        // Deliberately the uncoerced sum, not `balancePaise`, which floors at zero — correcting
        // against a floored figure would bake the floored-away amount into the adjustment.
        val current = confirmedTransactions.first().sumOf { tx ->
            if (tx.direction == TransactionDirection.CREDIT) tx.amountPaise else -tx.amountPaise
        }
        val delta = actualBalancePaise - current
        if (delta == 0L) return 0L

        dao.insert(
            TransactionEntity(
                amountPaise = kotlin.math.abs(delta),
                direction = if (delta > 0) TxDirection.CREDIT else TxDirection.DEBIT,
                status = TxStatus.CONFIRMED,
                source = TxSource.CORRECTION,
                sourcePackage = null,
                note = "Balance correction",
                rawText = null,
                timestampMillis = System.currentTimeMillis()
            )
        )
        return delta
    }

    /** The window during which detection was off, or null if there is nothing to report. */
    val detectionGap: Flow<ClosedRange<Long>?> =
        combine(prefs.detectionGapStart, prefs.detectionGapEnd) { start, end ->
            if (start != null && end != null && end > start) start..end else null
        }

    suspend fun noteListenerDisconnected(atMillis: Long) {
        prefs.setListenerDisconnectedAt(atMillis)
    }

    /**
     * Closes off a period during which the listener was unbound, recording it if it lasted long
     * enough to have hidden a payment.
     *
     * Android takes the listener away whenever it puts the app to sleep — which vendor battery
     * managers do aggressively — and while it is gone nothing arrives at all. There is no failed
     * parse to inspect afterwards and no notification left to find, which is exactly why several
     * missing payments could never be explained. The app cannot stop the system doing this, but it
     * can refuse to pretend it saw everything.
     */
    suspend fun noteListenerConnected(atMillis: Long) {
        val since = prefs.listenerDisconnectedAt.first()
        prefs.setListenerDisconnectedAt(null)
        if (since != null && atMillis - since >= MIN_REPORTABLE_GAP_MILLIS) {
            prefs.setDetectionGap(since, atMillis)
        }
    }

    suspend fun dismissDetectionGap() = prefs.clearDetectionGap()

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

        /**
         * How far back the bank's message may reach to claim a provisional row a UPI app left.
         * Wider than the plain amount check because the bank's email is the laggard — observed
         * arriving a hundred seconds behind the app's own notification — and safer, because only
         * a row carrying no reference at all can be claimed.
         */
        const val PROVISIONAL_CLAIM_WINDOW_MILLIS = 10 * 60 * 1000L

        /**
         * How long the listener must have been away before the gap is worth telling the user
         * about. Short unbinds happen routinely — on update, on reboot — and reporting those
         * would train the user to ignore the warning that matters.
         */
        const val MIN_REPORTABLE_GAP_MILLIS = 5 * 60 * 1000L
    }
}
