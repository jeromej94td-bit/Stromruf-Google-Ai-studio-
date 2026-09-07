package com.example.leads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeadClipboardParserTest {
    @Test fun `six digits are a customer number`() {
        val result = LeadClipboardParser.parse("Kd.-Nr. 482731")
        assertEquals("482731", result.customerNumber)
        assertNull(result.phone)
    }

    @Test fun `formatted phone is cleaned`() {
        assertEquals("+491701234567", LeadClipboardParser.parse("+49 (170) 123-45-67").phone)
    }

    @Test fun `email is detected`() {
        assertEquals("kunde@firma.de", LeadClipboardParser.parse("kunde@firma.de").email)
    }
}
