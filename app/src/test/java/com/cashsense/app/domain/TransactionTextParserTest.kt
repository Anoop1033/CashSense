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
}
