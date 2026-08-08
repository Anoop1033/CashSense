package com.cashsense.app.domain

enum class TransactionDirection { DEBIT, CREDIT }

data class ParsedTransaction(
    val amountPaise: Long,
    val direction: TransactionDirection,
    val sourcePackage: String,
    val rawText: String,
    /**
     * The bank's own reference for the payment (UPI RRN / UTR), when the message quotes one.
     *
     * This is what makes duplicate detection exact rather than guesswork: the bank's SMS, a
     * caller-ID app mirroring that SMS, and the bank's email all quote the same reference, so
     * matching on it identifies re-announcements of one payment with certainty — no guessing
     * from amounts and timing.
     */
    val referenceId: String?
)

/**
 * Best-effort extraction of a rupee amount + direction from a notification's
 * title/text. Deliberately conservative: returns null unless both an amount
 * AND an unambiguous debit/credit keyword are present, and bails out entirely
 * on promotional language. Every non-null result is still confirmed by the
 * user in the UI before it touches the wallet balance.
 */
object TransactionTextParser {

    private val amountRegex = Regex(
        """(?:₹|Rs\.?|INR)\s?([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * An amount written with no currency marker at all, as SBI and some others phrase it:
     * "A/C X1234 debited by 777.0". Without this their messages are dropped outright.
     *
     * The keyword has to sit immediately before the number, which is what keeps this from
     * latching onto the dates, masked account numbers and reference numbers that also fill
     * these messages.
     */
    private val bareAmountRegex = Regex(
        """\b(?:debited|credited)\s+by\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val debitKeywords = listOf(
        "debited", "paid to", "you paid", "sent to", "spent", "withdrawn", "purchase of"
    )

    private val creditKeywords = listOf(
        "credited", "received from", "you received", "refunded", "deposited", "cashback of"
    )

    /**
     * Reference embedded in a slash-delimited UPI descriptor, e.g. "UPI/P2M/521234567890/RAMESH".
     * Tried before [labelledReferenceRegex] because the leading "UPI" would otherwise match there
     * and capture the wrong digits.
     */
    private val upiPathReferenceRegex = Regex(
        """\bupi/[a-z0-9]+/([0-9]{9,18})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Reference introduced by a label — "Ref 521234567890", "Refno. 521234567890",
     * "RRN: 521234567890", "UTR 521234567890", "UPI:521234567890".
     *
     * The label is required: bare long digit runs in these messages are just as likely to be an
     * account number or a phone number, and mistaking one for a reference would collapse
     * unrelated payments into each other.
     */
    private val labelledReferenceRegex = Regex(
        """\b(?:ref(?:erence)?\s*(?:no\.?|number)?|rrn|utr|txn\s*id|transaction\s*id|upi)\s*[:.#-]?\s*([0-9]{9,18})\b""",
        RegexOption.IGNORE_CASE
    )

    private val promoVetoKeywords = listOf(
        "offer", "cashback up to", "cashback upto", "win ", "scratch card",
        "voucher", "lucky draw", "% off", "discount", "coupon", "reward points"
    )

    fun parse(packageName: String, title: String?, text: String?): ParsedTransaction? {
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val lower = combined.lowercase()
        if (promoVetoKeywords.any { lower.contains(it) }) return null

        val match = amountRegex.find(combined) ?: bareAmountRegex.find(combined) ?: return null
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val direction = when {
            debitKeywords.any { lower.contains(it) } -> TransactionDirection.DEBIT
            creditKeywords.any { lower.contains(it) } -> TransactionDirection.CREDIT
            else -> return null
        }

        val amountPaise = Math.round(amount * 100)
        return ParsedTransaction(
            amountPaise = amountPaise,
            direction = direction,
            sourcePackage = packageName,
            rawText = combined,
            referenceId = findReference(combined)
        )
    }

    private fun findReference(text: String): String? {
        val match = upiPathReferenceRegex.find(text) ?: labelledReferenceRegex.find(text)
        return match?.groupValues?.get(1)
    }
}
