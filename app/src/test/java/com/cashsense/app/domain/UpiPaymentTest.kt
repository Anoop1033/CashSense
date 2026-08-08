package com.cashsense.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpiPaymentTest {

    @Test
    fun `builds a well formed payment uri`() {
        val uri = UpiPayment.buildPaymentUri(
            payeeVpa = "merchant@bank",
            payeeName = "Ramesh Kirana",
            amountPaise = 25050,
            note = "Groceries",
            transactionRefId = "CSABC123"
        )
        assertEquals(
            "upi://pay?pa=merchant%40bank&pn=Ramesh%20Kirana&am=250.50&cu=INR&tr=CSABC123&tn=Groceries",
            uri
        )
    }

    @Test
    fun `omits the note param when blank`() {
        val uri = UpiPayment.buildPaymentUri("a@bank", "A", 10000, null, "TR1")
        assertEquals("upi://pay?pa=a%40bank&pn=A&am=100.00&cu=INR&tr=TR1", uri)
    }

    @Test
    fun `parses a success response`() {
        val response = UpiPayment.parseResponse("txnId=abc&responseCode=00&txnRef=CSABC123&Status=SUCCESS")
        assertEquals(UpiStatus.SUCCESS, response.status)
        assertEquals("CSABC123", response.txnRef)
    }

    @Test
    fun `parses a failure response`() {
        val response = UpiPayment.parseResponse("Status=FAILURE")
        assertEquals(UpiStatus.FAILURE, response.status)
    }

    @Test
    fun `treats a missing response as unknown`() {
        val response = UpiPayment.parseResponse(null)
        assertEquals(UpiStatus.UNKNOWN, response.status)
    }

    @Test
    fun `parses a upi qr with amount and payee name`() {
        val payload = UpiPayment.parseQrContent("upi://pay?pa=shop%40bank&pn=Corner%20Shop&am=99.50&cu=INR")
        assertEquals("shop@bank", payload.vpa)
        assertEquals("Corner Shop", payload.payeeName)
        assertEquals(9950L, payload.amountPaise)
    }

    @Test
    fun `treats a bare vpa string as a qr fallback`() {
        val payload = UpiPayment.parseQrContent("someone@upi")
        assertEquals("someone@upi", payload.vpa)
        assertNull(payload.amountPaise)
    }

    @Test
    fun `returns nulls for unrecognisable qr content`() {
        val payload = UpiPayment.parseQrContent("https://example.com/not-a-upi-code")
        assertNull(payload.vpa)
    }
}
