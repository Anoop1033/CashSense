package com.cashsense.app.domain

import java.net.URLDecoder
import java.net.URLEncoder

enum class UpiStatus { SUCCESS, FAILURE, SUBMITTED, UNKNOWN }

data class UpiResponse(
    val status: UpiStatus,
    val txnRef: String?,
    val approvalRefNo: String?,
    val responseCode: String?
)

data class UpiQrPayload(val vpa: String?, val payeeName: String?, val amountPaise: Long?)

/**
 * Builds and interprets standard UPI deep-link ("upi://pay?...") requests, per NPCI's UPI
 * Linking Specification — the same mechanism every "Pay via UPI" button in Indian apps uses.
 * CashSense never touches the PIN or the money movement itself: firing the intent hands off
 * to whichever UPI app the user picks, which returns a result once the user completes or
 * cancels the payment there.
 */
object UpiPayment {

    fun buildPaymentUri(
        payeeVpa: String,
        payeeName: String,
        amountPaise: Long,
        note: String?,
        transactionRefId: String
    ): String {
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        val params = mutableListOf(
            "pa=${encode(payeeVpa)}",
            "pn=${encode(payeeName)}",
            "am=${encode(paiseToAmountString(amountPaise))}",
            "cu=INR",
            "tr=${encode(transactionRefId)}"
        )
        if (!note.isNullOrBlank()) {
            params.add("tn=${encode(note)}")
        }
        return "upi://pay?" + params.joinToString("&")
    }

    /** Parses the "response" extra a UPI app returns once the user completes or cancels. */
    fun parseResponse(raw: String?): UpiResponse {
        if (raw.isNullOrBlank()) return UpiResponse(UpiStatus.UNKNOWN, null, null, null)

        val params = raw.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()

        fun field(vararg keys: String): String? = keys.firstNotNullOfOrNull { params[it] }

        val status = when (field("Status", "status")?.uppercase()) {
            "SUCCESS" -> UpiStatus.SUCCESS
            "FAILURE", "FAILED" -> UpiStatus.FAILURE
            "SUBMITTED" -> UpiStatus.SUBMITTED
            else -> UpiStatus.UNKNOWN
        }

        return UpiResponse(
            status = status,
            txnRef = field("txnRef", "tr"),
            approvalRefNo = field("ApprovalRefNo", "approvalRefNo"),
            responseCode = field("responseCode", "ResponseCode")
        )
    }

    /**
     * Reads a scanned QR code's raw content. Handles the standard "upi://pay?pa=...&pn=...&am=..."
     * form, and falls back to treating the whole string as a bare VPA if it merely looks like
     * one (some QR generators encode just the address, no scheme).
     */
    fun parseQrContent(content: String): UpiQrPayload {
        val trimmed = content.trim()

        if (trimmed.startsWith("upi://", ignoreCase = true)) {
            val query = trimmed.substringAfter('?', missingDelimiterValue = "")
            val params = query.split("&").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) null else part.substring(0, idx) to runCatching {
                    URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                }.getOrDefault(part.substring(idx + 1))
            }.toMap()

            val amountPaise = params["am"]?.toDoubleOrNull()?.let { Math.round(it * 100) }
            return UpiQrPayload(vpa = params["pa"], payeeName = params["pn"], amountPaise = amountPaise)
        }

        val looksLikeBareVpa = trimmed.contains("@") && !trimmed.contains(" ") && !trimmed.contains("://")
        return if (looksLikeBareVpa) {
            UpiQrPayload(vpa = trimmed, payeeName = null, amountPaise = null)
        } else {
            UpiQrPayload(vpa = null, payeeName = null, amountPaise = null)
        }
    }

    private fun paiseToAmountString(paise: Long): String {
        val rupees = paise / 100
        val remainder = paise % 100
        return "$rupees.${remainder.toString().padStart(2, '0')}"
    }
}
