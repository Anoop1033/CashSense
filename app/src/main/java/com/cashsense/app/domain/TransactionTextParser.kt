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

    /**
     * "paid ... to" / "sent ... to", with the amount allowed to sit between the verb and the
     * preposition — "Paid ₹70 to Ramesh", "You sent ₹70.00 to Ramesh". A plain "paid to" /
     * "sent to" substring match misses these outright, because the amount breaks the two words
     * apart; a real ₹70 spent through GPay went unrecorded for exactly this reason.
     *
     * The gap between the verb and "to" is restricted to a currency amount specifically, so this
     * does not fire on "paid you ... to our store" or similar, where a real word — not a number —
     * sits in between.
     */
    private val paidOrSentToRegex = Regex(
        """\b(?:paid|sent)\b\s*(?:₹|Rs\.?|INR)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*\bto\b""",
        RegexOption.IGNORE_CASE
    )

    private val creditKeywords = listOf(
        "credited", "received from", "you received", "refunded", "deposited", "cashback of",
        // How UPI apps word an incoming payment: "Ansh Soin paid you ₹1.00". Worth reading even
        // though the bank will announce the same payment, because the app's notification arrives
        // at once while the bank's SMS and email can trail it by minutes — without this the
        // wallet looks broken for as long as that takes. Duplicate handling collapses the pair.
        "paid you", "sent you", "money received"
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

    /**
     * Phrases meaning "money did not actually move (yet)". These matter far more now that a
     * detected payment can go straight into the balance: a scheduled debit, a declined payment or
     * someone's collect request all read like a transaction to a keyword match, and silently
     * applying one would leave the balance wrong with nothing prompting the user to look.
     */
    private val notCompletedVetoKeywords = listOf(
        // Yet to happen.
        "will be debited", "will be credited", "will be deducted", "is due", "due on",
        "due date", "scheduled", "mandate", "autopay", "standing instruction",
        // Did not happen.
        "failed", "declined", "unsuccessful", "was not processed", "could not be processed",
        "has been reversed", "will be reversed", "on hold", "pending approval",
        // Somebody asking, not a payment.
        "collect request", "payment request", "has requested", "is requesting",
        "requesting money", "requests rs", "requests ₹",
        // Codes and warnings that quote amounts.
        "otp", "one time password", "one-time password", "do not share", "never share"
    )

    fun parse(packageName: String, title: String?, text: String?): ParsedTransaction? {
        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val lower = combined.lowercase()
        if (promoVetoKeywords.any { lower.contains(it) }) return null
        if (notCompletedVetoKeywords.any { lower.contains(it) }) return null

        val match = amountRegex.find(combined) ?: bareAmountRegex.find(combined) ?: return null
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val direction = when {
            debitKeywords.any { lower.contains(it) } -> TransactionDirection.DEBIT
            creditKeywords.any { lower.contains(it) } -> TransactionDirection.CREDIT
            paidOrSentToRegex.containsMatchIn(combined) -> TransactionDirection.DEBIT
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
