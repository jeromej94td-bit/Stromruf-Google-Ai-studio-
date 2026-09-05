package com.example.transcription.offline

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Optional local post-processing for Smart Calls. The model is deliberately not
 * bundled with the APK: Gemma's license must be accepted by the user and the
 * file is several GB. Audio and transcript never leave the device.
 */
object LocalGemma {
    const val MODEL_PAGE = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm"
    private const val MODEL_NAME = "gemma-3n-e2b-it.litertlm"
    private const val MIN_MODEL_BYTES = 500L * 1024L * 1024L

    data class Analysis(val summary: String, val nextAction: String)

    private fun directory(context: Context) = File(context.noBackupFilesDir, "local_gemma").apply { mkdirs() }
    fun model(context: Context) = File(directory(context), MODEL_NAME)
    fun ready(context: Context) = model(context).let { it.isFile && it.length() >= MIN_MODEL_BYTES }

    /** Copies a user-selected, license-approved .litertlm model into app-private storage. */
    suspend fun install(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val target = model(context)
            val partial = File(target.parentFile, "$MODEL_NAME.partial")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(partial).use { output -> input.copyTo(output, 256 * 1024) }
            } ?: error("Modell kann nicht gelesen werden")
            require(partial.length() >= MIN_MODEL_BYTES) { "Keine vollständige Gemma-3n-E2B-Modelldatei ausgewählt" }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Modell konnte nicht installiert werden" }
        }
    }

    /** Returns a concise note and next action. Failure is safe: caller uses deterministic fallback. */
    suspend fun analyze(context: Context, transcript: String): Result<Analysis> = withContext(Dispatchers.IO) {
        runCatching {
            require(ready(context)) { "Gemma-Modell ist nicht installiert" }
            val prompt = """
                Fasse dieses deutsche Kundengespräch kurz und sachlich zusammen.
                Nenne als nächsten Schritt nur das, was tatsächlich vereinbart wurde.
                Antworte ausschließlich als JSON ohne Markdown:
                {"summary":"maximal 600 Zeichen","next_action":"maximal 220 Zeichen"}

                Gespräch:
                ${transcript.takeLast(24_000)}
            """.trimIndent()
            val config = EngineConfig(
                modelPath = model(context).absolutePath,
                backend = Backend.GPU(),
                cacheDir = File(context.cacheDir, "gemma_cache").apply { mkdirs() }.absolutePath
            )
            Engine(config).use { engine ->
                engine.initialize()
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of("Du bearbeitest deutsche Gesprächsnotizen. Erfinde keine Termine oder Zusagen."),
                        samplerConfig = SamplerConfig(topK = 8, topP = 0.8, temperature = 0.1),
                        maxOutputToken = 260
                    )
                ).use { conversation ->
                    val raw = conversation.sendMessage(prompt).text
                    val jsonText = raw.substringAfter('{', "").substringBeforeLast('}', "")
                    require(jsonText.isNotBlank()) { "Gemma hat kein lesbares Ergebnis geliefert" }
                    val json = JSONObject("{$jsonText}")
                    Analysis(
                        summary = json.optString("summary").replace(Regex("\\s+"), " ").trim().take(700),
                        nextAction = json.optString("next_action").replace(Regex("\\s+"), " ").trim().take(260)
                    )
                }
            }
        }
    }
}
