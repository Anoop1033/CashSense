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
    val referenceId: String?,
    /**
     * Whether something in the message independently corroborates that this is a real payment,
     * rather than a number and a verb happening to appear in the same text.
     *
     * Genuine bank alerts carry a reference, or name the account, or arrive from a payment app.
     * Marketing mail carries none of those. Only a corroborated detection is applied to the
     * balance on its own; the rest wait to be confirmed, so a misread can never move money
     * silently — it can only ask.
     */
    val corroborated: Boolean
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
     * Marks a message as bulk mail rather than a payment alert. A Spotify advert was recorded as
     * a ₹799 payment because its price supplied the amount and its unsubscribe footer — "this
     * message was sent to …" — supplied the verb. No bank alert carries any of this language.
     */
    private val bulkMailVetoKeywords = listOf(
        "unsubscribe", "this message was sent to", "edit your profile",
        "terms of use", "terms and conditions", "privacy policy",
        "limited eligibility", "t&c apply", "manage preferences",
        "view in browser", "no longer wish to receive"
    )

    /** Names the account the money moved in or out of: "A/c XX2260", "Acct no. 1234". */
    private val accountFragmentRegex = Regex(
        """\b(?:a/?c|acct|account)\b[^0-9a-z]{0,12}(?:no\.?|number)?[^0-9a-z]{0,6}[x*]{0,6}\d{3,}""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Apps whose notifications are only ever about money, so their wording needs no further
     * corroboration. Mail and SMS apps are deliberately absent: they carry everything else too,
     * which is exactly how the Spotify advert got in.
     */
    private val paymentAppPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",                     // BHIM
        "in.amazon.mShop.android.shopping",       // Amazon Pay
        "com.dreamplug.androidapp",               // CRED
        "money.super.payments",
        "com.mobikwik_new",
        "com.freecharge.android"
    )

    /**
     * Apps that carry a bank's own alerts among everything else they deliver. Their notifications
     * are read, but never trusted on wording alone — a message here must corroborate itself with a
     * reference or an account number, because the same inbox also carries adverts quoting prices.
     */
    private val alertCarrierPackages = setOf(
        "com.google.android.gm",                  // Gmail
        "com.google.android.apps.messaging",      // Messages
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.microsoft.office.outlook",
        "com.android.shell"                       // adb-posted notifications, for testing
    )

    /**
     * Whether this app's notifications are considered at all.
     *
     * Reading every app on the phone is what produced the two worst failures: a Spotify advert
     * became a ₹799 payment, and a WhatsApp group chat mentioning a transfer became a ₹211 one.
     * Neither app has any business describing the state of a bank account. Only payment apps and
     * the carriers of bank alerts are read now; a chat app relaying talk about money is not a
     * record of money moving.
     */
    private fun isTrustedSource(packageName: String): Boolean =
        packageName in paymentAppPackages ||
            packageName in alertCarrierPackages ||
            // Banks' own apps, which name themselves plainly and vary too much to list.
            packageName.contains("bank", ignoreCase = true)

    /**
     * "…to you" — the money came in, whatever verb introduced it.
     *
     * A payment app writes both directions with the same verbs: "sent ₹211 to Ramesh" is money
     * out, "sent ₹211 to You" is money in. Reading only the verb inverted a real ₹211 credit into
     * a debit, which is the costliest kind of mistake — the balance moves by twice the amount.
     */
    private val toYouRegex = Regex("""\bto\s+you\b""", RegexOption.IGNORE_CASE)

    /**
     * How far from the amount a transaction verb may sit and still be describing it.
     *
     * The Spotify advert's price and the word "sent to" in its footer were six hundred characters
     * and several unrelated sentences apart; in a genuine alert the two are always adjacent —
     * "Rs.799 debited from", "paid ₹70 to", "₹236.00 credited to". Requiring them close together
     * is what separates a sentence about a payment from a page that merely mentions a price.
     */
    private const val VERB_PROXIMITY_CHARS = 45

    /**
     * Phrases meaning "money did not actually move (yet)". These matter far more now that a
     * detected payment can go straight into the balance: a scheduled debit, a declined payment or
     * someone's collect request all read like a transaction to a keyword match, and silently
     * applying one would leave the balance wrong with nothing prompting the user to look.
     */
    private val notCompletedVetoKeywords = listOf(
        // Yet to happen. Strictly phrases about *tense*: a word naming the mechanism a payment was
        // set up through — mandate, autopay, standing instruction, scheduled — says nothing about
        // whether it has run yet, and vetoing on those silently dropped every completed recurring
        // debit. "Rs.34.00 debited towards your registered mandate" is money that has gone, and
        // small subscription charges are exactly the amounts least likely to be missed by eye.
        // The genuinely-future notices still carry one of these tense phrases and are still vetoed.
        "will be debited", "will be credited", "will be deducted", "will be deducted from",
        "is due", "due on", "due date", "is scheduled", "will be auto",
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
        if (!isTrustedSource(packageName)) return null

        val combined = listOfNotNull(title, text).joinToString(" ").trim()
        if (combined.isEmpty()) return null

        val lower = combined.lowercase()
        if (promoVetoKeywords.any { lower.contains(it) }) return null
        if (notCompletedVetoKeywords.any { lower.contains(it) }) return null
        if (bulkMailVetoKeywords.any { lower.contains(it) }) return null

        // Every amount in the message is considered, and each is kept only if a transaction verb
        // sits beside it. Taking the first amount and then hunting the whole message for any verb
        // is what let an advert's price pair with an unsubscribe footer's "sent to".
        val amounts = (amountRegex.findAll(combined) + bareAmountRegex.findAll(combined))
            .sortedBy { it.range.first }

        for (match in amounts) {
            val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            if (amount <= 0.0) continue
            val direction = directionBeside(combined, lower, match.range) ?: continue

            return ParsedTransaction(
                amountPaise = Math.round(amount * 100),
                direction = direction,
                sourcePackage = packageName,
                rawText = combined,
                referenceId = findReference(combined),
                corroborated = findReference(combined) != null ||
                    accountFragmentRegex.containsMatchIn(combined) ||
                    packageName in paymentAppPackages
            )
        }
        return null
    }

    /** The transaction verb governing an amount, or null if none sits close enough to it. */
    private fun directionBeside(
        text: String,
        lower: String,
        amountRange: IntRange
    ): TransactionDirection? {
        val from = (amountRange.first - VERB_PROXIMITY_CHARS).coerceAtLeast(0)
        val to = (amountRange.last + 1 + VERB_PROXIMITY_CHARS).coerceAtMost(text.length)
        val nearbyLower = lower.substring(from, to)
        val nearby = text.substring(from, to)

        // Checked before any verb: the recipient settles the direction outright. "sent ₹211 to
        // You" and "paid ₹211 to You" are money in, however much they read like money out.
        if (toYouRegex.containsMatchIn(nearby)) return TransactionDirection.CREDIT

        return when {
            debitKeywords.any { nearbyLower.contains(it) } -> TransactionDirection.DEBIT
            creditKeywords.any { nearbyLower.contains(it) } -> TransactionDirection.CREDIT
            paidOrSentToRegex.containsMatchIn(nearby) -> TransactionDirection.DEBIT
            else -> null
        }
    }

    private fun findReference(text: String): String? {
        val match = upiPathReferenceRegex.find(text) ?: labelledReferenceRegex.find(text)
        return match?.groupValues?.get(1)
    }
}
