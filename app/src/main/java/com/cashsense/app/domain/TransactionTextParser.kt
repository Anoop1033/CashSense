package com.cashsense.app.domain

enum class TransactionDirection { DEBIT, CREDIT }

data class ParsedTransaction(
    val amountPaise: Long,
    val direction: TransactionDirection,
    val sourcePackage: String,
    val rawText: String
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

    private val debitKeywords = listOf(
        "debited", "paid to", "you paid", "sent to", "spent", "withdrawn", "purchase of"
    )

    private val creditKeywords = listOf(
        "credited", "received from", "you received", "refunded", "deposited", "cashback of"
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

        val match = amountRegex.find(combined) ?: return null
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val direction = when {
            debitKeywords.any { lower.contains(it) } -> TransactionDirection.DEBIT
            creditKeywords.any { lower.contains(it) } -> TransactionDirection.CREDIT
            else -> return null
        }

        val amountPaise = Math.round(amount * 100)
        return ParsedTransaction(amountPaise, direction, packageName, combined)
    }
}
