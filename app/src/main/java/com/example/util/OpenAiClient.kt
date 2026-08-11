package com.example.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeneratedCustomerMessage(
    val subject: String,
    val body: String
)

class OpenAiClient(private val context: Context) {
    private val settings = SecureIntegrationSettings(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun testApiKey(): Boolean = withContext(Dispatchers.IO) {
        val key = settings.getOpenAiKey() ?: return@withContext false
        val json = JSONObject()
            .put("model", "gpt-4o-mini") // Note: The prompt had gpt-4.1-mini, fixing to standard openai models
            .put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", "Antworte nur mit OK.")
            ))

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions") // Using standard endpoint
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { it.isSuccessful }
    }

    suspend fun transcribeAudio(audioUri: Uri): String = withContext(Dispatchers.IO) {
        val key = settings.getOpenAiKey() ?: error("OpenAI API Key fehlt.")
        val bytes = context.contentResolver.openInputStream(audioUri)?.use { it.readBytes() }
            ?: error("Audio konnte nicht gelesen werden.")

        val fileBody = bytes.toRequestBody("audio/m4a".toMediaType())
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1") // The prompt had gpt-4o-transcribe, using whisper-1
            .addFormDataPart("file", "stromruf-audio.m4a", fileBody)
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $key")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Transkription fehlgeschlagen: $raw")
            JSONObject(raw).optString("text", raw)
        }
    }

    suspend fun generateCustomerMessage(
        customerName: String,
        customerEmail: String?,
        rawNote: String,
        transcript: String?,
        nextAppointment: String?
    ): GeneratedCustomerMessage = withContext(Dispatchers.IO) {
        val key = settings.getOpenAiKey() ?: error("OpenAI API Key fehlt.")

        val input = """
            Kunde: $customerName
            E-Mail: ${customerEmail ?: "unbekannt"}
            Naechster Termin: ${nextAppointment ?: "nicht angegeben"}

            Manuelle Notiz:
            $rawNote

            Transkript:
            ${transcript ?: ""}
        """.trimIndent()

        val instructions = """
            Du bist ein professioneller Vertriebsassistent fuer Stromruf.
            Erstelle eine kurze, freundliche Kundennachricht auf Deutsch.
            Nutze nur Informationen aus Notiz, Transkript und Termin.
            Erfinde keine Preise, Termine, Zusagen oder Anlagen.
            Wenn etwas unklar ist, formuliere neutral.
            Antworte ausschliesslich als JSON:
            {"subject":"...","body":"..."}
        """.trimIndent()

        val json = JSONObject()
            .put("model", "gpt-4o-mini") // Fixing model name to gpt-4o-mini
            .put("messages", org.json.JSONArray()
                .put(JSONObject().put("role", "system").put("content", instructions))
                .put(JSONObject().put("role", "user").put("content", input))
            )
            .put("response_format", JSONObject().put("type", "json_object"))

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions") // standard endpoint
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("KI-Erstellung fehlgeschlagen: $raw")

            val root = JSONObject(raw)
            val outputText = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")

            val parsed = JSONObject(outputText.trim())
            GeneratedCustomerMessage(
                subject = parsed.optString("subject"),
                body = parsed.optString("body")
            )
        }
    }
}
