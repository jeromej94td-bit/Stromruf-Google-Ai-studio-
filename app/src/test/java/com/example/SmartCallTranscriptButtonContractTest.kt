package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SmartCallTranscriptButtonContractTest {

    @Test
    fun `saved transcribed conversation reopens its local full transcript`() {
        val source = File("src/main/java/com/example/ui/screens/SmartCallsTab.kt").readText()

        assertTrue(source.contains("val localTranscript = cachedTranscripts[note.sourceFileName]"))
        assertTrue(source.contains("activeTranscriptResult = localTranscript"))
        assertTrue(source.contains("Volltranskript öffnen"))
    }

    @Test
    fun `conversation explains when full transcript is only local and unavailable`() {
        val source = File("src/main/java/com/example/ui/screens/SmartCallsTab.kt").readText()

        assertTrue(source.contains("Volltranskript nur lokal auf diesem Gerät"))
    }
}
