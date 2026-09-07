package com.example.transcription.offline

import java.util.Calendar
import java.util.Locale

/** Finds explicit German callback dates/times and callback windows without inventing defaults. */
object GermanFollowUpPlanner {
    data class Plan(
        val dueAt: Long,
        val description: String,
        val windowEndAt: Long? = null,
        val originalAppointmentAt: Long = dueAt
    )

    fun plan(transcript: String, now: Long = System.currentTimeMillis()): Plan? {
        val text = transcript.lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ")
        val numberWords = mapOf("eins" to 1, "eine" to 1, "einer" to 1, "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "elf" to 11, "zwölf" to 12)
        val callback = Regex("sprechen|telefonier|anruf|rückruf|zurückruf|zurückrufen|rufen.*an|hören uns|melden|wiederhören|kontakt|termin")
        if (!callback.containsMatchIn(text)) return null

        val relativeHours = Regex("(?:in|nach)\\s+(\\d{1,2}|eins|eine|einer|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf)\\s+stunden?").find(text)
        if (relativeHours != null) {
            val token = relativeHours.groupValues[1]
            val hours = token.toIntOrNull() ?: numberWords[token] ?: return null
            val due = now + hours * 60L * 60L * 1000L
            return Plan(due, "Rückrufzeit relativ aus Gespräch erkannt", originalAppointmentAt = due)
        }

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
                if (year.isNotBlank()) calendar.set(Calendar.YEAR, if (year.length == 2) 2000 + year.toInt() else year.toInt())
                else if (calendar.timeInMillis < now - 60_000L) calendar.add(Calendar.YEAR, 1)
            }
            Regex("\\bmorgen\\b").containsMatchIn(text) -> { explicitDate = true; calendar.add(Calendar.DAY_OF_YEAR, 1) }
            Regex("nächste woche|kommende woche").containsMatchIn(text) -> {
                explicitDate = true
                val delta = (Calendar.MONDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
                calendar.add(Calendar.DAY_OF_YEAR, if (delta == 0) 7 else delta)
            }
            else -> {
                val weekday = mapOf(
                    "montag" to Calendar.MONDAY, "dienstag" to Calendar.TUESDAY,
                    "mittwoch" to Calendar.WEDNESDAY, "donnerstag" to Calendar.THURSDAY,
                    "freitag" to Calendar.FRIDAY, "samstag" to Calendar.SATURDAY, "sonntag" to Calendar.SUNDAY
                ).entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(text) }?.value
                if (weekday != null) {
                    explicitDate = true
                    val delta = (weekday - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    calendar.add(Calendar.DAY_OF_YEAR, if (delta == 0) 7 else delta)
                }
            }
        }
        val range = Regex("(?:zwischen|von)\\s*([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(?:uhr)?\\s*(?:und|bis|-)\\s*([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*uhr").find(text)
        if (range != null) {
            if (!explicitDate) {
                explicitDate = true
                // Ohne genannten Tag gilt ein noch kommendes Zeitfenster für heute, sonst morgen.
            }
            val startHour = range.groupValues[1].toInt()
            val startMinute = range.groupValues[2].toIntOrNull() ?: 0
            val endHour = range.groupValues[3].toInt()
            val endMinute = range.groupValues[4].toIntOrNull() ?: 0
            var start = (calendar.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, startHour); set(Calendar.MINUTE, startMinute) }
            var end = (calendar.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, endHour); set(Calendar.MINUTE, endMinute) }
            if (!Regex("\\bmorgen\\b|nächste woche|kommende woche|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|\\b\\d{1,2}\\.\\d{1,2}").containsMatchIn(text) && end.timeInMillis <= now) {
                start.add(Calendar.DAY_OF_YEAR, 1); end.add(Calendar.DAY_OF_YEAR, 1)
            }
            if (end.timeInMillis <= start.timeInMillis) end.add(Calendar.DAY_OF_YEAR, 1)
            val queueAt = (start.timeInMillis + 5L * 60L * 1000L).coerceAtMost(end.timeInMillis)
            if (queueAt <= now + 60_000L) return null
            return Plan(queueAt, "Rückruf-Zeitfenster aus Gespräch erkannt", end.timeInMillis, start.timeInMillis)
        }

        if (!explicitDate) return null

        val time = Regex("\\b([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s*uhr(?:\\s*([0-5]\\d))?").find(text) ?: return null
        val hour = time.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minute = time.groupValues.getOrNull(2)?.toIntOrNull() ?: time.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        if (calendar.timeInMillis <= now + 60_000L) return null
        return Plan(calendar.timeInMillis, "Termin automatisch aus Gespräch bzw. gespeicherter Gemma-Regel erkannt")
    }
}
