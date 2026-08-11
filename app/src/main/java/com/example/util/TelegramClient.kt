package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Telegram-Bot-Anbindung (robuste Version).
 *
 * - Token und Chat-ID werden vor jeder Nutzung bereinigt (Leerzeichen,
 *   Zeilenumbrüche, versehentliches "bot"-Präfix oder eingefügte URL-Teile).
 * - Requests laufen über FormBody (kein JSON-Encoding nötig).
 * - Jeder Fehler liefert eine konkrete Ursache (Telegram-description,
 *   HTTP-Code oder Netzwerkfehler) statt eines stummen false.
 * - Ein automatischer Retry bei Netzwerk-Aussetzern.
 */
class TelegramClient(context: Context) {

    private val settings = SecureIntegrationSettings(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    data class TgResult(
        val ok: Boolean,
        val detail: String = ""   // Bot-Name, Chat-ID oder Fehlerursache
    )

    // ---------- Bereinigung ----------

    /** Entfernt Whitespace, Zeilenumbrüche, "bot"-Präfix und URL-Reste aus dem Token. */
    private fun cleanToken(raw: String?): String? {
        if (raw == null) return null
        var t = raw.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        // Falls eine komplette URL eingefügt wurde: Token herausziehen
        val urlMatch = Regex("bot(\\d+:[A-Za-z0-9_-]+)").find(t)
        if (urlMatch != null) t = urlMatch.groupValues[1]
        // Falls "bot" vorangestellt wurde
        if (t.startsWith("bot") && t.length > 3 && t[3].isDigit()) t = t.substring(3)
        return t.takeIf { it.isNotBlank() }
    }

    /** Chat-ID bereinigen (nur Ziffern und optionales Minus für Gruppen). */
    private fun cleanChatId(raw: String?): String? {
        if (raw == null) return null
        val t = raw.trim().replace(" ", "")
        return t.takeIf { it.isNotBlank() && Regex("^-?\\d+$").matches(t) } ?: t.takeIf { it.isNotBlank() }
    }

    private fun token(): String? = cleanToken(settings.getTelegramBotToken())
    private fun chatId(): String? = cleanChatId(settings.getTelegramChatId())

    fun isConfigured(): Boolean = token() != null && chatId() != null
    fun hasToken(): Boolean = token() != null

    // ---------- HTTP-Kern mit Retry und klaren Fehlern ----------

    private fun executeWithRetry(request: Request): Pair<Int, String>? {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    return Pair(resp.code, body)
                }
            } catch (e: IOException) {
                lastError = e
                Log.w("TelegramClient", "Netzwerkfehler (Versuch ${attempt + 1}): ${e.message}")
            } catch (e: Exception) {
                lastError = e
                Log.e("TelegramClient", "Unerwarteter Fehler: ${e.message}", e)
                return null
            }
        }
        Log.e("TelegramClient", "Alle Versuche fehlgeschlagen: ${lastError?.message}")
        return null
    }

    /** Wertet eine Telegram-Antwort aus und extrahiert ok/description. */
    private fun parseTelegram(codeAndBody: Pair<Int, String>?): Pair<Boolean, String> {
        if (codeAndBody == null) return Pair(false, "Netzwerkfehler – keine Verbindung zu api.telegram.org")
        val (code, body) = codeAndBody
        return try {
            val json = JSONObject(body)
            if (json.optBoolean("ok")) Pair(true, body)
            else Pair(false, "Telegram: ${json.optString("description", "Fehler $code")}")
        } catch (e: Exception) {
            Pair(false, "HTTP $code – unerwartete Antwort")
        }
    }

    // ---------- API ----------

    /** Prüft den Bot-Token über getMe. */
    suspend fun testConnectionDetailed(): TgResult = withContext(Dispatchers.IO) {
        val tok = token() ?: return@withContext TgResult(false, "Kein Bot-Token gespeichert")
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$tok/getMe")
            .get()
            .build()
        val (ok, detail) = parseTelegram(executeWithRetry(req))
        if (!ok) return@withContext TgResult(false, detail)
        val result = JSONObject(detail).optJSONObject("result")
        val name = result?.optString("username").orEmpty().ifBlank {
            result?.optString("first_name").orEmpty()
        }
        TgResult(true, name)
    }

    /** Kompatibilität: liefert Bot-Namen oder null. */
    suspend fun testConnection(): String? = testConnectionDetailed().let { if (it.ok) it.detail else null }

    /** Liefert den Bot-Usernamen (für den t.me-Link / QR-Code). */
    suspend fun getBotUsernameDetailed(): TgResult = testConnectionDetailed()

    /** t.me-Deeplink zum Bot – als QR-Code anzeigen oder direkt öffnen. */
    fun buildBotLink(botUsername: String): String =
        "https://t.me/${botUsername.removePrefix("@")}?start=stromruf"

    /**
     * Wartet darauf, dass der Nutzer dem Bot von SEINEM Account eine Nachricht
     * schickt (z. B. über den QR-Code / "Start"-Button in Telegram).
     * Pollt getUpdates, bis ein privater Chat auftaucht, und speichert die Chat-ID.
     */
    suspend fun waitForPrivateChatDetailed(timeoutMs: Long = 45_000): TgResult {
        val startAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startAt < timeoutMs) {
            val r = autoDetectChatIdDetailed()
            if (r.ok) return r
            kotlinx.coroutines.delay(2_000)
        }
        return TgResult(
            false,
            "Keine Verbindung erkannt. Öffne den Bot-Chat in Telegram, tippe auf " +
                "\"Start\" und versuche es erneut."
        )
    }

    /** Ermittelt die Chat-ID automatisch über getUpdates und speichert sie. */
    suspend fun autoDetectChatIdDetailed(): TgResult = withContext(Dispatchers.IO) {
        val tok = token() ?: return@withContext TgResult(false, "Kein Bot-Token gespeichert")
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$tok/getUpdates?limit=100")
            .get()
            .build()
        val (ok, detail) = parseTelegram(executeWithRetry(req))
        if (!ok) return@withContext TgResult(false, detail)
        val updates = JSONObject(detail).optJSONArray("result")
        if (updates == null || updates.length() == 0) {
            return@withContext TgResult(
                false,
                "Keine Nachrichten gefunden. Schicke dem Bot in Telegram zuerst " +
                    "eine Nachricht (z. B. \"Start\") und versuche es erneut."
            )
        }
        for (i in updates.length() - 1 downTo 0) {
            val upd = updates.optJSONObject(i) ?: continue
            val msg = upd.optJSONObject("message")
                ?: upd.optJSONObject("edited_message")
                ?: continue
            // Nachrichten von Bots ignorieren – wir suchen DEINEN privaten Chat
            val from = msg.optJSONObject("from")
            if (from?.optBoolean("is_bot", false) == true) continue
            val chat = msg.optJSONObject("chat") ?: continue
            // Nur private Chats (dein persönlicher Chat mit dem Bot)
            if (chat.optString("type") != "private") continue
            val id = chat.optLong("id", 0L)
            if (id != 0L) {
                val idStr = id.toString()
                settings.saveTelegramChatId(idStr)
                return@withContext TgResult(true, idStr)
            }
        }
        TgResult(
            false,
            "Kein privater Chat gefunden. Öffne in Telegram den Chat mit deinem Bot, " +
                "schicke ihm eine Nachricht (z. B. \"Start\") und tippe erneut auf " +
                "\"Chat-ID ermitteln\"."
        )
    }

    suspend fun autoDetectChatId(): String? = autoDetectChatIdDetailed().let { if (it.ok) it.detail else null }

    /** Sendet eine Textnachricht. Liefert bei Fehler die konkrete Ursache. */
    suspend fun sendMessageDetailed(text: String): TgResult = withContext(Dispatchers.IO) {
        val tok = token() ?: return@withContext TgResult(false, "Kein Bot-Token gespeichert")
        val cid = chatId() ?: return@withContext TgResult(false, "Keine Chat-ID gespeichert")
        if (text.isBlank()) return@withContext TgResult(false, "Leere Nachricht")

        // Schutz: Die Bot-ID (Zahl vor dem ":" im Token) ist KEINE gültige Ziel-Chat-ID.
        val botOwnId = tok.substringBefore(":")
        if (cid == botOwnId || cid == "-$botOwnId") {
            return@withContext TgResult(
                false,
                "Die gespeicherte Chat-ID ist die ID des Bots selbst. Es muss DEINE " +
                    "persönliche Chat-ID sein: Schicke dem Bot in Telegram eine Nachricht " +
                    "und tippe in den Einstellungen auf \"Chat-ID ermitteln\"."
            )
        }

        val form = FormBody.Builder()
            .add("chat_id", cid)
            .add("text", text.take(4000))   // Telegram-Limit: 4096 Zeichen
            .build()
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$tok/sendMessage")
            .post(form)
            .build()
        val (ok, detail) = parseTelegram(executeWithRetry(req))
        if (ok) return@withContext TgResult(true, "")

        // Häufige Fehler in verständliche Hinweise übersetzen
        val friendly = when {
            detail.contains("bots can't send messages to bots", ignoreCase = true) ||
            detail.contains("can't send messages to the bot", ignoreCase = true) ->
                "Die Chat-ID zeigt auf einen Bot statt auf deinen persönlichen Chat. " +
                    "Schicke deinem Bot in Telegram eine Nachricht und tippe dann in den " +
                    "Einstellungen auf \"Chat-ID ermitteln\" – dann wird deine eigene " +
                    "Chat-ID gespeichert und die Notiz landet in deinem Chat mit dem Bot."
            detail.contains("chat not found", ignoreCase = true) ->
                "Chat nicht gefunden. Schicke dem Bot zuerst eine Nachricht in Telegram " +
                    "und ermittle die Chat-ID erneut."
            else -> detail
        }
        TgResult(false, friendly)
    }

    suspend fun sendMessage(text: String): Boolean = sendMessageDetailed(text).ok

    /**
     * Sendet eine Audio-Datei als echte Telegram-Voice-Message (sendVoice).
     * Fällt bei nicht unterstütztem Format automatisch auf sendAudio zurück.
     */
    suspend fun sendVoiceDetailed(audioFile: File, caption: String? = null): TgResult =
        withContext(Dispatchers.IO) {
            val tok = token() ?: return@withContext TgResult(false, "Kein Bot-Token gespeichert")
            val cid = chatId() ?: return@withContext TgResult(false, "Keine Chat-ID gespeichert")
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return@withContext TgResult(false, "Audio-Datei nicht gefunden")
            }

            fun buildRequest(method: String, field: String): Request {
                val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", cid)
                    .addFormDataPart(
                        field, audioFile.name,
                        audioFile.asRequestBody("audio/mp4".toMediaType())
                    )
                if (!caption.isNullOrBlank()) {
                    bodyBuilder.addFormDataPart("caption", caption.take(1000))
                }
                return Request.Builder()
                    .url("https://api.telegram.org/bot$tok/$method")
                    .post(bodyBuilder.build())
                    .build()
            }

            // Erst als Voice-Message versuchen …
            val (okVoice, detailVoice) = parseTelegram(executeWithRetry(buildRequest("sendVoice", "voice")))
            if (okVoice) return@withContext TgResult(true, "")

            // … sonst als normale Audio-Datei senden.
            val (okAudio, detailAudio) = parseTelegram(executeWithRetry(buildRequest("sendAudio", "audio")))
            if (okAudio) TgResult(true, "") else TgResult(false, detailAudio.ifBlank { detailVoice })
        }

    /** Formatiert und sendet eine Kunden-Notiz. Liefert Ergebnis mit Fehlerdetail. */
    suspend fun sendCustomerNoteDetailed(
        contactName: String,
        company: String?,
        phone: String?,
        note: String
    ): TgResult {
        val sb = StringBuilder()
        sb.append("📝 Notiz aus STROMRUF\n")
        sb.append("━━━━━━━━━━━━━━━\n")
        sb.append("👤 ").append(contactName)
        if (!company.isNullOrBlank()) sb.append(" · ").append(company)
        sb.append("\n")
        if (!phone.isNullOrBlank()) sb.append("📞 ").append(phone).append("\n")
        sb.append("━━━━━━━━━━━━━━━\n")
        sb.append(note.trim())
        return sendMessageDetailed(sb.toString())
    }

    suspend fun sendCustomerNote(
        contactName: String,
        company: String?,
        phone: String?,
        note: String
    ): Boolean = sendCustomerNoteDetailed(contactName, company, phone, note).ok
}
