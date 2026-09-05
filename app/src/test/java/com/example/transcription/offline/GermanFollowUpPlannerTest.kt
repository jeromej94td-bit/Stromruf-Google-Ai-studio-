package com.example.transcription.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class GermanFollowUpPlannerTest {
    @Test fun `recognizes tomorrow at precise time`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 5, 9, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val plan = GermanFollowUpPlanner.plan("Wir sprechen uns morgen um 13:20 Uhr wieder.", now)
        assertNotNull(plan)
        val due = Calendar.getInstance().apply { timeInMillis = plan!!.dueAt }
        assertEquals(13, due.get(Calendar.HOUR_OF_DAY))
        assertEquals(20, due.get(Calendar.MINUTE))
    }

    @Test fun `does not invent a follow up without callback intent`() {
        org.junit.Assert.assertNull(GermanFollowUpPlanner.plan("Der Kunde hat einen Stromverbrauch von 80000 kWh."))
    }
}
