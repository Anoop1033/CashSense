package com.cashsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTextParserTest {

    @Test
    fun `parses a debited notification`() {
        val result = TransactionTextParser.parse(
            packageName = "com.google.android.apps.nbu.paisa.user",
            title = "Payment successful",
            text = "₹250 paid to Ramesh Kirana Store"
        )
        assertNotNull(result)
        assertEquals(25000L, result!!.amountPaise)
        assertEquals(TransactionDirection.DEBIT, result.direction)
    }

    @Test
    fun `parses a credited bank sms style notification`() {
        val result = TransactionTextParser.parse(
            packageName = "com.some.bank",
            title = null,
            text = "Rs.1,500.00 credited to your account XX1234 on 08-08-26"
        )
        assertNotNull(result)
        assertEquals(150000L, result!!.amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.direction)
    }

    @Test
    fun `ignores promotional notifications`() {
        val result = TransactionTextParser.parse(
            packageName = "com.phonepe.app",
            title = "Special offer",
            text = "Get ₹50 cashback up to Rs.500 on your next payment"
        )
        assertNull(result)
    }

    @Test
    fun `ignores notifications without a clear direction`() {
        val result = TransactionTextParser.parse(
            packageName = "com.some.bank",
            title = "Statement ready",
            text = "Your statement for Rs.4,500 is now available"
        )
        assertNull(result)
    }

    @Test
    fun `ignores notifications without an amount`() {
        val result = TransactionTextParser.parse(
            packageName = "com.phonepe.app",
            title = "Payment successful",
            text = "Your payment to Ramesh was successful"
        )
        assertNull(result)
    }

    // The three notifications a single real HDFC credit produced on a test device. The SMS and the
    // email must yield the same reference, because that is what collapses them into one record.

    private val realCreditSms =
        "Credit Alert! Rs.236.00 credited to HDFC Bank A/c XX2260 on 09-08-26 " +
            "from VPA 8003078388@pthdfc (UPI 622116409481)"

    private val realCreditEmail =
        "HDFC Bank InstaAlerts View: Account update for your HDFC Bank A/c We're writing to " +
            "inform you that Rs.236.00 has been successfully credited to your HDFC Bank account " +
            "ending in 2260.b. Sender: MAYANK SHARMA (VPA: 8003078388@pthdfc) " +
            "c. UPI Reference No.: 622116409481 For more details on Service charges and Fees."

    @Test
    fun `reads the real bank credit sms`() {
        val result = TransactionTextParser.parse("com.google.android.apps.messaging", null, realCreditSms)
        assertNotNull(result)
        assertEquals(23600L, result!!.amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals("622116409481", result.referenceId)
    }

    @Test
    fun `reads the real bank credit email`() {
        val result = TransactionTextParser.parse("com.google.android.gm", null, realCreditEmail)
        assertNotNull(result)
        assertEquals(23600L, result!!.amountPaise)
        assertEquals(TransactionDirection.CREDIT, result.direction)
        assertEquals("622116409481", result.referenceId)
    }

    @Test
    fun `the sms and the email agree on the reference so one payment stays one record`() {
        val fromSms = TransactionTextParser.parse("com.google.android.apps.messaging", null, realCreditSms)
        val fromEmail = TransactionTextParser.parse("com.google.android.gm", null, realCreditEmail)
        assertNotNull(fromSms?.referenceId)
        assertEquals(fromSms!!.referenceId, fromEmail!!.referenceId)
    }

    private fun isDetected(text: String): Boolean =
        TransactionTextParser.parse("com.some.bank", null, text) != null

    // These matter because a detected payment is applied to the balance without asking: anything
    // that reads like a transaction but is not one would silently make the balance wrong.

    @Test
    fun `ignores a payment that has not happened yet`() {
        assertFalse(isDetected("Rs 500 will be debited from a/c XX1234 on 15-Aug for your SIP"))
        assertFalse(isDetected("Your autopay mandate of Rs 1,200 is due on 20-Aug"))
    }

    @Test
    fun `ignores a payment that did not go through`() {
        assertFalse(isDetected("Your payment of Rs 500 to SHOP failed. Ref 521234567890"))
        assertFalse(isDetected("Transaction declined: Rs 2,000 debited amount has been reversed"))
    }

    @Test
    fun `ignores somebody requesting money`() {
        assertFalse(isDetected("RAMESH has requested Rs 500 via UPI collect request"))
    }

    @Test
    fun `ignores an otp message quoting an amount`() {
        assertFalse(isDetected("OTP 458213 for your payment of Rs 500. Do not share with anyone"))
    }

    @Test
    fun `still detects a genuine completed payment`() {
        assertTrue(isDetected("Rs 500.00 debited from a/c XX1234 to SHOP. Ref 521234567890"))
    }

    @Test
    fun `parses an sbi style amount written without a currency marker`() {
        val result = TransactionTextParser.parse(
            packageName = "com.some.bank",
            title = null,
            text = "Dear UPI user A/C X1234 debited by 777.0 on date 09Aug25 trf to RAMESH STORE Refno 521234567890"
        )
        assertNotNull(result)
        assertEquals(77700L, result!!.amountPaise)
        assertEquals(TransactionDirection.DEBIT, result.direction)
    }

    @Test
    fun `does not treat a bare number as an amount without the keyword before it`() {
        val result = TransactionTextParser.parse(
            packageName = "com.some.bank",
            title = null,
            text = "Your account X1234 was debited on 09Aug25 by our systems, 777.0 pending review"
        )
        assertNull(result)
    }

    private fun referenceIn(text: String): String? =
        TransactionTextParser.parse("com.some.bank", null, text)?.referenceId

    @Test
    fun `reads a reference labelled Ref`() {
        assertEquals(
            "521234567890",
            referenceIn("Rs.777.00 debited from a/c **1234 to VPA ramesh@okaxis. Ref 521234567890.")
        )
    }

    @Test
    fun `reads a reference labelled Refno`() {
        assertEquals(
            "521234567890",
            referenceIn("A/C X1234 debited by 777.0 trf to RAMESH STORE Refno 521234567890")
        )
    }

    @Test
    fun `reads a reference written as UPI colon`() {
        assertEquals(
            "521234567890",
            referenceIn("Acct XX123 debited for Rs 777.00; ramesh@okaxis credited. UPI:521234567890")
        )
    }

    @Test
    fun `reads a reference from a slash delimited upi descriptor`() {
        assertEquals(
            "521234567890",
            referenceIn("INR 777.00 debited A/c no. XX1234 UPI/P2M/521234567890/RAMESH")
        )
    }

    @Test
    fun `reads a reference labelled RRN`() {
        assertEquals(
            "521234567890",
            referenceIn("Rs 777 debited from your account. RRN: 521234567890")
        )
    }

    @Test
    fun `the same payment announced by sms and email yields the same reference`() {
        val sms = referenceIn("Rs.777.00 debited from a/c **1234 to VPA ramesh@okaxis. Ref 521234567890.")
        val email = referenceIn(
            "Dear Customer, INR 777.00 has been debited from your account XX1234 " +
                "and paid to RAMESH STORE. Reference No. 521234567890"
        )
        assertNotNull(sms)
        assertEquals(sms, email)
    }

    @Test
    fun `reports no reference when the message quotes none`() {
        assertNull(referenceIn("₹777 paid to Ramesh Kirana Store"))
    }

    @Test
    fun `does not mistake a masked account number for a reference`() {
        assertNull(referenceIn("Rs 777 debited from a/c no 123456789012 today"))
    }

    @Test
    fun `does not mistake a helpline number for a reference`() {
        assertNull(referenceIn("Rs 777 debited. Not you? Call 18002586161 to report"))
    }
}
