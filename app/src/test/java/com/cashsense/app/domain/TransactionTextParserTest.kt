package com.cashsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
