package com.example.transcription.offline

import java.util.Calendar
import java.util.Locale

/** Finds an explicit German callback date/time without inventing a default appointment. */
object GermanFollowUpPlanner {
    data class Plan(val dueAt: Long, val description: String)

    fun plan(transcript: String, now: Long = System.currentTimeMillis()): Plan? {
        val text = transcript.lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ")
        val callback = Regex("sprechen|telefonier|anruf|rückruf|zurückruf|zurückrufen|rufen.*an|hören uns|melden|wiederhören|kontakt|termin")
        if (!callback.containsMatchIn(text)) return null

        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var explicitDate = false

        val date = Regex("\\b(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2,4}))?\\b").find(text)
        when {
            date != null -> {
                explicitDate = true
                calendar.set(Calendar.DAY_OF_MONTH, date.groupValues[1].toInt())
                calendar.set(Calendar.MONTH, date.groupValues[2].toInt() - 1)
                val year = date.groupValues[3]
                if (year.isNotBlank()) {
                    calendar.set(Calendar.YEAR, if (year.length == 2) 2000 + year.toInt() else year.toInt())
                } else if (calendar.timeInMillis < now - 60_000L) {
                    calendar.add(Calendar.YEAR, 1)
                }
            }
            Regex("\\bmorgen\\b").containsMatchIn(text) -> {
                explicitDate = true
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            Regex("nächste woche|kommende woche").containsMatchIn(text) -> {
                explicitDate = true
                val delta = (Calendar.MONDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
                calendar.add(Calendar.DAY_OF_YEAR, if (delta == 0) 7 else delta)
            }
            else -> {
                val weekday = mapOf(
                    "montag" to Calendar.MONDAY,
                    "dienstag" to Calendar.TUESDAY,
                    "mittwoch" to Calendar.WEDNESDAY,
                    "donnerstag" to Calendar.THURSDAY,
                    "freitag" to Calendar.FRIDAY,
                    "samstag" to Calendar.SATURDAY,
                    "sonntag" to Calendar.SUNDAY
                ).entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(text) }?.value
                if (weekday != null) {
                    explicitDate = true
                    val delta = (weekday - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    calendar.add(Calendar.DAY_OF_YEAR, if (delta == 0) 7 else delta)
                }
            }
        }
        if (!explicitDate) return null

        val time = Regex("\\b([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s*uhr(?:\\s*([0-5]\\d))?").find(text)
            ?: return null
        val hour = time.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minute = time.groupValues.getOrNull(2)?.toIntOrNull()
            ?: time.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        if (calendar.timeInMillis <= now + 60_000L) return null

        return Plan(
            dueAt = calendar.timeInMillis,
            description = "Termin automatisch aus Gespräch bzw. gespeicherter Gemma-Regel erkannt"
        )
    }
}
