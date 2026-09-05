package com.example.transcription.offline

import org.junit.Assert.assertTrue
import org.junit.Test

class GermanCallSummaryTest {
    @Test fun `keeps offer and callback details`() {
        val summary = GermanCallSummary.create("[00:01] Herr Müller möchte ein Angebot für Strom. [00:14] Wir telefonieren nächste Woche wieder.")
        assertTrue(summary.contains("Angebot"))
        assertTrue(summary.contains("Wiedervorlage"))
        assertTrue(summary.contains("Herr Müller"))
    }

    @Test fun `does not return empty summary`() {
        assertTrue(GermanCallSummary.create("").isNotBlank())
    }
}
