package com.example.transcription.offline

import android.content.Context

/** User-editable rules for local Gemma post-processing. */
object GemmaPromptSettings {
    private const val PREFS = "smart_call_gemma_prompts"
    private const val KEY_DOCUMENTATION = "documentation"
    private const val KEY_CUSTOMER = "customer"
    private const val KEY_FOLLOW_UP = "follow_up"

    const val DEFAULT_DOCUMENTATION = """Fasse das Kundengespräch kurz, sachlich und vertrieblich brauchbar zusammen. Nenne nur Informationen, die wirklich im Gespräch vorkamen. Bevorzuge: Anliegen, Energieart, Verbrauch, aktueller Versorger, Preise, Laufzeit, Entscheidung, Einwände, zugesagte Unterlagen und nächster Schritt. Keine erfundenen Angaben."""

    const val DEFAULT_CUSTOMER = """Formuliere zusätzlich eine kurze kundenfreundliche Gesprächszusammenfassung in höflichem Deutsch, die direkt per E-Mail, Messenger oder Zwischenablage an den Kunden weitergegeben werden kann. Keine internen Bewertungen, keine Vermutungen und keine erfundenen Zusagen."""

    const val DEFAULT_FOLLOW_UP = """Erkenne nur tatsächlich vereinbarte Rückrufe oder Termine. Datum und Uhrzeit müssen aus dem Gespräch stammen. Wenn kein eindeutiger Termin vereinbart wurde, formuliere lediglich den nächsten Schritt und erfinde kein konkretes Datum."""

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun documentation(context: Context): String = prefs(context).getString(KEY_DOCUMENTATION, DEFAULT_DOCUMENTATION)
        ?.takeIf { it.isNotBlank() } ?: DEFAULT_DOCUMENTATION

    fun customer(context: Context): String = prefs(context).getString(KEY_CUSTOMER, DEFAULT_CUSTOMER)
        ?.takeIf { it.isNotBlank() } ?: DEFAULT_CUSTOMER

    fun followUp(context: Context): String = prefs(context).getString(KEY_FOLLOW_UP, DEFAULT_FOLLOW_UP)
        ?.takeIf { it.isNotBlank() } ?: DEFAULT_FOLLOW_UP

    fun save(context: Context, documentation: String, customer: String, followUp: String) {
        prefs(context).edit()
            .putString(KEY_DOCUMENTATION, documentation.trim().ifBlank { DEFAULT_DOCUMENTATION })
            .putString(KEY_CUSTOMER, customer.trim().ifBlank { DEFAULT_CUSTOMER })
            .putString(KEY_FOLLOW_UP, followUp.trim().ifBlank { DEFAULT_FOLLOW_UP })
            .apply()
    }

    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
