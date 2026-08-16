package com.cashsense.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object TxDirection {
    const val DEBIT = "DEBIT"
    const val CREDIT = "CREDIT"
}

object TxStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
    const val DISMISSED = "DISMISSED"
}

object TxSource {
    const val AUTO = "AUTO"
    const val MANUAL = "MANUAL"
    const val UPI = "UPI"

    /**
     * A single entry that re-anchors the wallet to the balance the bank actually reports.
     *
     * Kept distinct from [MANUAL] so a correction reads as what it is in History — an admission
     * that the running total had drifted — rather than as a payment the user made.
     */
    const val CORRECTION = "CORRECTION"
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val direction: String,
    val status: String,
    val source: String,
    val sourcePackage: String?,
    val note: String?,
    val rawText: String?,
    val timestampMillis: Long,
    /** The bank's reference (UPI RRN / UTR) when one was quoted — see ParsedTransaction. */
    val referenceId: String? = null
)
