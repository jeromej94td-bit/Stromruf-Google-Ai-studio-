package com.example.transcription.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class GermanFollowUpPlannerTest {
    @Test fun `requested callback phrases use call date and exact time`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 6, 9, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val cases = listOf(
            Triple("Wir sprechen uns morgen um 18 Uhr wieder.", 7, 18 to 0),
            Triple("Rufen Sie mich Dienstag um 14:30 Uhr an.", 8, 14 to 30),
            Triple("Melden Sie sich Freitag um 10 Uhr.", 11, 10 to 0),
            Triple("Wir hören uns am 12.09. um 15 Uhr.", 12, 15 to 0),
            Triple("Wir sprechen nächste Woche um 10 Uhr.", 7, 10 to 0)
        )
        cases.forEach { (text, day, time) ->
            val plan = GermanFollowUpPlanner.plan(text, now)
            assertNotNull(text, plan)
            val due = Calendar.getInstance().apply { timeInMillis = plan!!.dueAt }
            assertEquals(text, day, due.get(Calendar.DAY_OF_MONTH))
            assertEquals(text, time.first, due.get(Calendar.HOUR_OF_DAY))
            assertEquals(text, time.second, due.get(Calendar.MINUTE))
        }
        org.junit.Assert.assertNull(GermanFollowUpPlanner.plan("Wir sprechen nächste Woche.", now))
    }

    @Test fun `recognizes tomorrow at precise time`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 5, 9, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val plan = GermanFollowUpPlanner.plan("Wir sprechen uns morgen um 13:20 Uhr wieder.", now)
        assertNotNull(plan)
        val due = Calendar.getInstance().apply { timeInMillis = plan!!.dueAt }
        assertEquals(13, due.get(Calendar.HOUR_OF_DAY))
        assertEquals(20, due.get(Calendar.MINUTE))
    }

    @Test fun `recognizes spoken relative hours and same day callback windows`() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 7, 8, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val relative = GermanFollowUpPlanner.plan("Rufen Sie mich in zwei Stunden an.", now)
        assertNotNull(relative)
        assertEquals(now + 2L * 60L * 60L * 1000L, relative!!.dueAt)

        val window = GermanFollowUpPlanner.plan("Rufen Sie mich zwischen 10 und 11 Uhr an.", now)
        assertNotNull(window)
        val due = Calendar.getInstance().apply { timeInMillis = window!!.dueAt }
        val end = Calendar.getInstance().apply { timeInMillis = window!!.windowEndAt!! }
        assertEquals(10, due.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, due.get(Calendar.MINUTE))
        assertEquals(11, end.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun `does not invent a follow up without callback intent`() {
        org.junit.Assert.assertNull(GermanFollowUpPlanner.plan("Der Kunde hat einen Stromverbrauch von 80000 kWh."))
    }
}
