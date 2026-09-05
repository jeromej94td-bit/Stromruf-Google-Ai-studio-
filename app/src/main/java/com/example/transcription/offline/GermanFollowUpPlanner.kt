package com.example.transcription.offline

import java.util.Calendar
import java.util.Locale

/** Finds an unambiguous German callback date without sending conversation data to an API. */
object GermanFollowUpPlanner {
    data class Plan(val dueAt: Long, val description: String)

    fun plan(transcript: String, now: Long = System.currentTimeMillis()): Plan? {
        val text = transcript.lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ")
        val callback = Regex("sprechen|telefonier|anruf|rückruf|zurückruf|zurückrufen|melden|wiederhören|kontakt")
        val offer = Regex("angebot.*(?:senden|schicken|erstellen)|(?:senden|schicken|erstellen).*angebot")
        if (!callback.containsMatchIn(text) && !offer.containsMatchIn(text)) return null

        val calendar = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val date = Regex("\\b(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2,4}))?\\b").find(text)
        when {
            date != null -> {
                calendar.set(Calendar.DAY_OF_MONTH, date.groupValues[1].toInt())
                calendar.set(Calendar.MONTH, date.groupValues[2].toInt() - 1)
                val year = date.groupValues[3]
                if (year.isNotBlank()) calendar.set(Calendar.YEAR, year.let { if (it.length == 2) 2000 + it.toInt() else it.toInt() })
                else if (calendar.timeInMillis < now - 60_000L) calendar.add(Calendar.YEAR, 1)
            }
            Regex("\\bmorgen\\b").containsMatchIn(text) -> calendar.add(Calendar.DAY_OF_YEAR, 1)
            Regex("nächste woche|kommende woche").containsMatchIn(text) -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            else -> {
                val weekday = mapOf(
                    "montag" to Calendar.MONDAY, "dienstag" to Calendar.TUESDAY,
                    "mittwoch" to Calendar.WEDNESDAY, "donnerstag" to Calendar.THURSDAY,
                    "freitag" to Calendar.FRIDAY, "samstag" to Calendar.SATURDAY,
                    "sonntag" to Calendar.SUNDAY
                ).entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(text) }?.value
                if (weekday != null) {
                    val delta = (weekday - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    calendar.add(Calendar.DAY_OF_YEAR, if (delta == 0) 7 else delta)
                } else when {
                    offer.containsMatchIn(text) -> addBusinessDays(calendar, 3)
                    callback.containsMatchIn(text) -> calendar.add(Calendar.DAY_OF_YEAR, 7)
                    else -> return null
                }
            }
        }

        val time = Regex("\\b([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s*uhr(?:\\s*([0-5]\\d))?").find(text)
        val hour = time?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 10
        val minute = time?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: time?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 0
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        if (calendar.timeInMillis <= now + 60_000L) calendar.add(Calendar.DAY_OF_YEAR, 1)
        val certainty = when {
            time != null -> "automatisch aus Gespräch erkannt"
            offer.containsMatchIn(text) -> "Angebots-Nachfassen automatisch vorgeschlagen (3 Werktage, 10:00 Uhr)"
            else -> "Rückruf automatisch vorgeschlagen (10:00 Uhr)"
        }
        return Plan(calendar.timeInMillis, certainty)
    }

    private fun addBusinessDays(calendar: Calendar, days: Int) {
        var remaining = days
        while (remaining > 0) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            if (calendar.get(Calendar.DAY_OF_WEEK) !in setOf(Calendar.SATURDAY, Calendar.SUNDAY)) remaining--
        }
    }
}
