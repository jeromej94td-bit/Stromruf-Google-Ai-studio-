package com.example.leads

import com.example.database.ContactEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HotBoxReachabilityTest {
    private val contact = ContactEntity(
        id = "contact-1", name = "Meyer", phone = "01701234567",
        company = null, email = null, lastCallAt = null, lastOutcome = null,
        isHotBox = true,
        hotBoxStartHour = 9 * 60,
        hotBoxEndHour = 12 * 60,
        hotBoxWeekdays = Calendar.MONDAY.toString()
    )

    private fun time(day: Int, hour: Int) = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, day, hour, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test fun `contact is eligible inside configured weekday and time`() {
        assertTrue(contact.isReachableNow(time(7, 10))) // Monday
    }

    @Test fun `contact is blocked outside configured time`() {
        assertFalse(contact.isReachableNow(time(7, 14)))
    }

    @Test fun `contact is blocked on another weekday`() {
        assertFalse(contact.isReachableNow(time(8, 10))) // Tuesday
    }
}
