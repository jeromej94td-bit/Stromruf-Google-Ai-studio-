package com.example.transcription.offline

/**
 * Concise, deterministic on-device summary. The original transcript stays local.
 * No language model/API and no audio upload is used here.
 */
object GermanCallSummary {
    fun create(transcript: String): String {
        val clean = transcript
            .lineSequence()
            .map { it.replace(Regex("^\\s*\\[\\d{2}:\\d{2}]\\s*"), "").trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return "Gespräch geführt; die automatische Transkription hat keinen verständlichen Text erkannt."

        val sentences = clean.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }.filter { it.isNotBlank() }
        val keywords = Regex("(?i)angebot|preis|tarif|vertrag|strom|gas|verbrauch|zähler|kwh|termin|rückruf|zurückrufen|nächste woche|morgen|montag|dienstag|mittwoch|donnerstag|freitag|erreichen|interesse|kein interesse|absage")
        val relevant = sentences.filter { keywords.containsMatchIn(it) }
        val selected = (relevant + sentences).distinct().take(3)
        val body = selected.joinToString(" ").take(900)

        val tags = mutableListOf<String>().apply {
            if (Regex("(?i)angebot|preis|tarif").containsMatchIn(clean)) add("Angebot")
            if (Regex("(?i)termin|rückruf|zurückrufen|nächste woche|morgen|montag|dienstag|mittwoch|donnerstag|freitag").containsMatchIn(clean)) add("Wiedervorlage")
            if (Regex("(?i)kein interesse|absage").containsMatchIn(clean)) add("Kein Interesse")
            if (Regex("(?i)interesse").containsMatchIn(clean) && none { it == "Kein Interesse" }) add("Interesse")
        }
        return buildString {
            if (tags.isNotEmpty()) append(tags.joinToString(" · ")).append("\n")
            append(body)
        }
    }
}
