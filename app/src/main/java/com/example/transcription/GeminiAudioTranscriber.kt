package com.example.transcription

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.util.SecureIntegrationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

data class TranscriptionResult(
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val fullTranscript: String,
    val rawText: String,
    val estimatedDurationSeconds: Long = 0L
)

class GeminiAudioTranscriber(private val context: Context) {

    private val secureSettings = SecureIntegrationSettings(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiAudioTranscriber"
        // Model priorities conforming to skill guidelines
        private const val PRIMARY_MODEL = "gemini-2.5-flash"
        private const val FALLBACK_MODEL = "gemini-flash-latest"
    }

    /**
     * Checks if a Gemini API key is configured either via BuildConfig or local secure settings.
     */
    fun hasApiKey(): Boolean {
        return !secureSettings.getGeminiKey().isNullOrBlank()
    }

    /**
     * Gets the effective API key.
     */
    fun getApiKey(): String? {
        return secureSettings.getGeminiKey()
    }

    /**
     * Transcribes an audio recording using Gemini Flash and generates both a structured sales note and a full transcript.
     */
    suspend fun transcribeAndSummarize(
        audioFile: File,
        customKey: String? = null
    ): Result<TranscriptionResult> = withContext(Dispatchers.IO) {
        val apiKey = customKey ?: getApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Kein Gemini API-Schlüssel gefunden. Bitte trage deinen Schlüssel in den Einstellungen ein."))
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Audiodatei existiert nicht oder ist leer."))
        }

        // Check file size (max ~25MB for inline base64)
        if (audioFile.length() > 25 * 1024 * 1024) {
            return@withContext Result.failure(IllegalArgumentException("Audiodatei ist zu groß (${audioFile.length() / (1024 * 1024)} MB). Maximale Dateigröße für Direkt-Upload ist 25 MB."))
        }

        try {
            // Determine MIME type
            val mimeType = when {
                audioFile.name.endsWith(".wav", true) -> "audio/wav"
                audioFile.name.endsWith(".m4a", true) -> "audio/mp4"
                audioFile.name.endsWith(".mp4", true) -> "audio/mp4"
                audioFile.name.endsWith(".mp3", true) -> "audio/mp3"
                audioFile.name.endsWith(".aac", true) -> "audio/aac"
                audioFile.name.endsWith(".ogg", true) -> "audio/ogg"
                else -> "audio/mp4"
            }

            Log.d(TAG, "Reading audio file: ${audioFile.name} (${audioFile.length()} bytes, $mimeType)")
            val audioBytes = FileInputStream(audioFile).use { it.readBytes() }
            val base64Data = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

            val promptText = """
                Du bist ein intelligenter KI-Vertriebs- und Telefonie-Assistent für die Energieberatungs- und Vertriebs-App Stromruf.
                Höre dir die beigefügte Audioaufnahme des Telefonats vollständig und sehr genau an.

                Bitte erstelle eine hochwertige, strukturierte Auswertung auf Deutsch mit folgenden zwei festen Abschnitten:

                ### 📋 ZUSAMMENFASSUNG & GESPRÄCHSNOTIZ
                - **📌 Gesprächszweck:** (Kurze Zusammenfassung des Kernanliegens)
                - **📝 Wichtige besprochene Punkte:** (Konkrete Angaben zu Strom/Gas, Zählernummern, Abschlag, Tarif, Ersparnis, Wünschen oder Fragen)
                - **🎯 Vereinbarungen & To-Dos:** (Verbindliche Absprachen, nächste Schritte, Rückruftermine oder Aufgaben)
                - **💡 Kundensignale & Einwände:** (Interesse, Zufriedenheit, Einwände oder Stimmung des Gesprächspartners)

                ---

                ### 🎙️ WORTGETREUES TRANSKRIPT
                (Transkribiere das gesamte Gespräch mit klarer Sprecherzuordnung in chronologischer Reihenfolge, z.B.:
                👤 Berater: ...
                👥 Kunde: ...)
            """.trimIndent()

            val contentsArray = JSONArray().apply {
                val partText = JSONObject().put("text", promptText)
                val partAudio = JSONObject().put("inlineData", JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64Data)
                })

                val contentObj = JSONObject().apply {
                    put("parts", JSONArray().put(partText).put(partAudio))
                }
                put(contentObj)
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 4096)
                })
            }

            // Try primary model first, fallback if necessary
            var responseText: String? = null
            var lastError: Exception? = null

            val modelsToTry = listOf(PRIMARY_MODEL, FALLBACK_MODEL)
            for (model in modelsToTry) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    Log.d(TAG, "Sending audio transcription request to model: $model")
                    val response = client.newCall(request).execute()
                    val bodyString = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Model $model returned error ${response.code}: $bodyString")
                        val errObj = runCatching { JSONObject(bodyString).optJSONObject("error") }.getOrNull()
                        val errMsg = errObj?.optString("message") ?: "HTTP ${response.code}"
                        lastError = RuntimeException("Gemini API Fehler ($model): $errMsg")
                        continue
                    }

                    val root = JSONObject(bodyString)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            responseText = parts.getJSONObject(0).optString("text")
                            if (!responseText.isNullOrBlank()) {
                                break // Success!
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Request exception with model $model", e)
                    lastError = e
                }
            }

            if (responseText.isNullOrBlank()) {
                return@withContext Result.failure(lastError ?: RuntimeException("Keine Antwort von Gemini erhalten."))
            }

            // Parse sections
            val (summary, transcript) = parseSummaryAndTranscript(responseText)
            val result = TranscriptionResult(
                fileName = audioFile.name,
                timestamp = System.currentTimeMillis(),
                summary = summary,
                fullTranscript = transcript,
                rawText = responseText,
                estimatedDurationSeconds = 0L
            )

            // Save to cache
            TranscriptionCache(context).save(result)
            Log.d(TAG, "Transcription and summary successfully completed for: ${audioFile.name}")

            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    private fun parseSummaryAndTranscript(raw: String): Pair<String, String> {
        val transcriptMarker = "### 🎙️ WORTGETREUES TRANSKRIPT"
        val altTranscriptMarker = "WORTGETREUES TRANSKRIPT"
        val dividerMarker = "---"

        return when {
            raw.contains(transcriptMarker) -> {
                val parts = raw.split(transcriptMarker, limit = 2)
                var summary = parts[0].trim()
                if (summary.endsWith(dividerMarker)) {
                    summary = summary.removeSuffix(dividerMarker).trim()
                }
                val transcript = parts[1].trim()
                Pair(summary, transcript)
            }
            raw.contains(altTranscriptMarker) -> {
                val parts = raw.split(altTranscriptMarker, limit = 2)
                var summary = parts[0].trim()
                if (summary.endsWith(dividerMarker)) {
                    summary = summary.removeSuffix(dividerMarker).trim()
                }
                val transcript = parts[1].trim().removePrefix(":").removePrefix("#").trim()
                Pair(summary, transcript)
            }
            else -> {
                // If not cleanly separated, provide full text in summary and transcript
                Pair(raw, raw)
            }
        }
    }
}
