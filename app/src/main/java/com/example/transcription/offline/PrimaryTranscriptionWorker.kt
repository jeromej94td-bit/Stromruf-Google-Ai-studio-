package com.example.transcription.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.util.SecureIntegrationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Groq Whisper Large v3 is primary; fast local Whisper is fallback only. */
class PrimaryTranscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(8, TimeUnit.MINUTES)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val name = inputData.getString("file") ?: return@withContext Result.success()
        val job = LocalTranscripts.read(context, name)
        if (job.optString("state") == "done") return@withContext Result.success()

        val file = runCatching { LocalTranscripts.recording(context, name) }.getOrNull()
            ?: return@withContext Result.success()
        if (job.optLong("size") != file.length() || job.optLong("modified") != file.lastModified()) {
            job.put("state", "error").put("message", "Aufnahme wurde geändert – bitte erneut starten")
            LocalTranscripts.write(context, name, job)
            return@withContext Result.success()
        }

        val key = SecureIntegrationSettings(context).getGroqKey()
        if (!key.isNullOrBlank()) {
            job.put("state", "running")
                .put("message", "Groq Whisper Large v3 transkribiert …")
                .put("transcriptionSource", "groq-whisper-large-v3")
            LocalTranscripts.write(context, name, job)

            val groq = runCatching { transcribeWithGroq(file, key) }
            if (groq.isSuccess) {
                val transcript = groq.getOrThrow().trim()
                if (transcript.isNotBlank()) {
                    val wavDurationMs = runCatching { PcmWave(file).use { it.durationMs } }.getOrDefault(0L)
                    val callDurationMs = job.optLong("callDurationSeconds").coerceAtLeast(0L) * 1000L
                    val durationMs = maxOf(wavDurationMs, callDurationMs)
                    val fallbackSummary = GermanCallSummary.create(transcript)
                    val gemma = LocalGemma.analyze(context, transcript).getOrNull()
                    val nextAction = gemma?.nextAction.orEmpty()
                    val baseSummary = gemma?.summary?.ifBlank { fallbackSummary } ?: fallbackSummary
                    val summary = if (nextAction.isBlank()) baseSummary else "$baseSummary\nNächster Schritt: $nextAction"

                    job.put("state", "done")
                        .put("durationMs", durationMs)
                        .put("text", transcript)
                        .put("summary", summary)
                        .put("analysisSource", if (gemma != null) "gemma-3n-e2b" else "regelbasiert")
                        .put("nextAction", nextAction)
                        .put("syncState", "pending")
                        .put("message", if (gemma != null)
                            "Groq Whisper Large v3 + lokale Gemma-Notiz fertig"
                        else
                            "Groq Whisper Large v3 Transkript fertig")
                        .put("syncMessage", "Zusammenfassung und Termin werden automatisch verarbeitet")
                    LocalTranscripts.write(context, name, job)
                    LocalTranscripts.enqueueNoteSync(context, name)
                    return@withContext Result.success()
                }
            }

            val reason = groq.exceptionOrNull()?.message.orEmpty().take(160)
            job.put("groqFallbackReason", reason)
                .put("message", "Groq nicht verfügbar – lokaler Whisper-Fallback wird gestartet")
            LocalTranscripts.write(context, name, job)
        } else {
            job.put("message", "Kein Groq-Schlüssel – lokaler Whisper-Fallback wird gestartet")
            LocalTranscripts.write(context, name, job)
        }

        if (LocalTranscripts.ready(context)) {
            job.put("state", "pending")
                .put("transcriptionSource", "local-whisper-base-q5_1")
                .put("message", "Lokales Whisper-Fallback wartet auf Verarbeitung")
            LocalTranscripts.write(context, name, job)
            LocalTranscripts.enqueue(context, name)
        } else {
            job.put("state", "pending")
                .put("transcriptionSource", "local-whisper-base-q5_1")
                .put("message", "Lokales Whisper-Fallback wird vorbereitet")
            LocalTranscripts.write(context, name, job)
            LocalTranscripts.download(context)
        }
        Result.success()
    }

    private fun transcribeWithGroq(file: java.io.File, apiKey: String): String {
        require(file.length() > 0L) { "Audiodatei ist leer" }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("language", "de")
            .addFormDataPart("temperature", "0")
            .addFormDataPart(
                "prompt",
                "Deutsches geschäftliches Telefonat aus der Energieberatung. Begriffe können sein: Strom, Gas, Arbeitspreis, Grundpreis, Marktlokationsnummer, Zählernummer, Abschlag, Lieferbeginn, Preisgarantie, Börse, Smart Calls, sense electra. Zahlen, Namen und Firmennamen exakt wiedergeben."
            )
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IOException(if (detail.isBlank()) "Groq HTTP ${response.code}" else detail)
            }
            val text = runCatching { JSONObject(body).optString("text") }.getOrDefault("")
            if (text.isBlank()) throw IOException("Groq hat kein Transkript geliefert")
            return text
        }
    }
}
