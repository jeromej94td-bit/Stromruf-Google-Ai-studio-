package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Zentrale Hintergrund-Weiterleitung von Notizen an Telegram.
 *
 * Jede Notiz, die irgendwo in der App entsteht (Gesprächsnotiz, Sprachnotiz,
 * Wrap-Up nach einem Anruf), wird hier fire-and-forget an den verknüpften
 * Telegram-Bot gesendet und gleichzeitig als Zeile in der Supabase-Tabelle
 * `customer_messages` festgehalten – inklusive Zustellstatus. Damit ist das
 * Backend jederzeit synchron zur App.
 *
 * Die Weiterleitung blockiert nie die UI und wirft nie Exceptions nach außen.
 */
object TelegramForwarder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Auto-generierte Notizen (z. B. "Automatischer Anruf (01:23)") nicht weiterleiten. */
    fun isUserNote(note: String?): Boolean {
        if (note.isNullOrBlank()) return false
        val t = note.trim()
        return !(t.startsWith("Automatisch", ignoreCase = true) ||
            t.startsWith("Automatischer", ignoreCase = true) ||
            t.startsWith("Systemanruf", ignoreCase = true) ||
            t.startsWith("[Simulierter Anruf", ignoreCase = true))
    }

    /**
     * Leitet eine Notiz im Hintergrund an Telegram weiter und synchronisiert
     * sie nach Supabase (`customer_messages`).
     *
     * @param audioFile  optionale Sprachaufnahme – wird als Voice-Message gesendet
     * @param transcript optionale Transkription der Sprachaufnahme
     * @param messageId  vorhandene customer_messages-ID; wenn null, wird eine neue Zeile angelegt
     */
    fun forwardNote(
        context: Context,
        contactName: String,
        company: String? = null,
        phone: String? = null,
        note: String,
        transcript: String? = null,
        audioFile: File? = null,
        messageId: String? = null,
        source: String = "notiz"
    ) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val settings = SecureIntegrationSettings(appContext)
                val telegram = TelegramClient(appContext)
                val id = messageId ?: UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                val autoEnabled = settings.isTelegramAutoForward() && telegram.isConfigured()

                var tgStatus = "disabled"
                var tgError: String? = null
                var tgSentAt: Long? = null

                if (autoEnabled) {
                    val textToSend = buildString {
                        append(note.trim())
                        if (!transcript.isNullOrBlank() && transcript.trim() != note.trim()) {
                            append("\n\n🎤 Transkript:\n").append(transcript.trim())
                        }
                    }
                    var textOk = true
                    if (textToSend.isNotBlank()) {
                        val textResult = telegram.sendCustomerNoteDetailed(
                            contactName = contactName,
                            company = company,
                            phone = phone,
                            note = textToSend
                        )
                        textOk = textResult.ok
                        if (!textOk) tgError = textResult.detail
                    }

                    var voiceOk = true
                    if (audioFile != null && audioFile.exists()) {
                        val voiceResult = telegram.sendVoiceDetailed(
                            audioFile,
                            caption = "🎤 Sprachnotiz · $contactName"
                        )
                        voiceOk = voiceResult.ok
                        if (!voiceOk) tgError = voiceResult.detail
                    }

                    if (textOk && voiceOk) {
                        tgStatus = "sent"
                        tgSentAt = System.currentTimeMillis()
                        Log.d("TelegramForwarder", "Notiz an Telegram weitergeleitet ($source)")
                    } else {
                        tgStatus = "failed"
                        if (tgError == null) tgError = "Telegram-Weiterleitung fehlgeschlagen"
                        Log.w("TelegramForwarder", "Telegram-Weiterleitung fehlgeschlagen: $tgError")
                    }
                }

                // Supabase-Sync: Notiz + Zustellstatus in customer_messages festhalten.
                val payload = JSONObject().apply {
                    put("id", id)
                    put("contact_name", contactName)
                    put("contact_phone", phone ?: JSONObject.NULL)
                    put("contact_email", JSONObject.NULL)
                    put("contact_id", JSONObject.NULL)
                    put("raw_note", note)
                    put("transcript", transcript ?: JSONObject.NULL)
                    put("subject", "")
                    put("body", "")
                    put("provider", "telegram")
                    put("status", source)
                    put("created_at_ms", now)
                    put("telegram_status", tgStatus)
                    put("telegram_sent_at_ms", tgSentAt ?: JSONObject.NULL)
                    put("error_message", tgError ?: JSONObject.NULL)
                }
                SupabaseDbClient.upsertTableRow(appContext, "customer_messages", payload)
            } catch (e: Exception) {
                Log.e("TelegramForwarder", "forwardNote error", e)
            }
        }
    }

    /** Aktualisiert nur den Telegram-Status einer bestehenden Nachricht in Supabase. */
    fun syncStatus(context: Context, messageId: String, status: String, error: String? = null) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("id", messageId)
                    put("telegram_status", status)
                    put("error_message", error ?: JSONObject.NULL)
                }
                SupabaseDbClient.upsertTableRow(appContext, "customer_messages", payload)
            } catch (e: Exception) {
                Log.e("TelegramForwarder", "syncStatus error", e)
            }
        }
    }
}
