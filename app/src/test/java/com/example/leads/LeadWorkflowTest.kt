package com.example.leads

import com.example.database.NeukundeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LeadWorkflowTest {
    private fun lead(status: String = LeadWorkflow.CALL) = NeukundeEntity(
        id = "lead-1",
        dateCreated = 1L,
        customerNumber = "482731",
        phone = "491701234567",
        callAttempts = 0,
        status = status
    )

    @Test fun `not reached schedules one retry two hours later`() {
        val now = 10_000L
        val updated = LeadWorkflow.missed(lead(), now)
        assertEquals(1, updated.callAttempts)
        assertEquals(now + 2 * 60 * 60 * 1000L, updated.nextActionAt)
    }

    @Test fun `offer sent schedules status check two days at 17`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 6, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val updated = LeadWorkflow.next(lead(LeadWorkflow.OFFER), now)
        val due = Calendar.getInstance().apply { timeInMillis = updated.nextActionAt!! }
        assertEquals(LeadWorkflow.OFFER_SENT, updated.status)
        assertEquals(8, due.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, due.get(Calendar.HOUR_OF_DAY))
    }

    @Test fun `past daily reminder rolls to the next day`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 6, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val due = Calendar.getInstance().apply { timeInMillis = LeadWorkflow.atToday(15, 50, now) }
        assertEquals(7, due.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, due.get(Calendar.HOUR_OF_DAY))
        assertEquals(50, due.get(Calendar.MINUTE))
        assertTrue(due.timeInMillis > now)
    }
}
