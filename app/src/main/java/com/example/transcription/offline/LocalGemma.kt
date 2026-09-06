package com.example.transcription.offline

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local Gemma post-processing for Smart Calls. */
object LocalGemma {
    const val MODEL_PAGE = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm"
    private const val MODEL_NAME = "gemma-3n-e2b-it.litertlm"
    private const val MIN_MODEL_BYTES = 500L * 1024L * 1024L

    data class Analysis(
        val summary: String,
        val nextAction: String,
        val customerText: String
    )

    private fun directory(context: Context) = File(context.noBackupFilesDir, "local_gemma").apply { mkdirs() }
    fun model(context: Context) = File(directory(context), MODEL_NAME)
    fun ready(context: Context) = model(context).let { it.isFile && it.length() >= MIN_MODEL_BYTES }

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

    /** Uses the user-editable documentation/customer/follow-up rules saved in the app. */
    suspend fun analyze(context: Context, transcript: String): Result<Analysis> = withContext(Dispatchers.IO) {
        runCatching {
            require(ready(context)) { "Gemma-Modell ist nicht installiert" }
            val documentationRules = GemmaPromptSettings.documentation(context)
            val customerRules = GemmaPromptSettings.customer(context)
            val followUpRules = GemmaPromptSettings.followUp(context)
            val nowText = SimpleDateFormat("EEEE, dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
            val prompt = """
                Du wertest ein deutsches Kundengespräch für Stromruf aus.
                Aktuelles Datum und Uhrzeit: $nowText

                REGELN FÜR INTERNE DOKUMENTATION:
                $documentationRules

                REGELN FÜR KUNDENFASSUNG:
                $customerRules

                REGELN FÜR NÄCHSTEN SCHRITT / TERMIN:
                $followUpRules

                Wenn die Terminregeln einen automatisch berechneten Termin verlangen, schreibe in next_action
                das berechnete konkrete Datum UND die konkrete Uhrzeit, damit Stromruf es eindeutig übernehmen kann.

                Antworte ausschließlich als JSON ohne Markdown:
                {
                  "summary":"interne Gesprächsnotiz",
                  "next_action":"vereinbarter oder nach Nutzerregel berechneter nächster Schritt; bei Kalendertermin mit DD.MM.YYYY und HH:MM Uhr",
                  "customer_text":"kundenfreundliche Fassung zum direkten Kopieren"
                }

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
                        systemInstruction = Contents.of(
                            "Du bearbeitest deutsche Geschäftsgespräche. Halte dich an die gespeicherten Nutzerregeln. Erfinde keine Fakten, Termine, Preise oder Zusagen außerhalb dieser Regeln."
                        ),
                        samplerConfig = SamplerConfig(topK = 8, topP = 0.8, temperature = 0.1),
                        maxOutputToken = 700
                    )
                ).use { conversation ->
                    val raw = conversation.sendMessage(prompt).contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString(separator = "") { it.text }
                    val jsonText = raw.substringAfter('{', "").substringBeforeLast('}', "")
                    require(jsonText.isNotBlank()) { "Gemma hat kein lesbares Ergebnis geliefert" }
                    val json = JSONObject("{$jsonText}")
                    Analysis(
                        summary = json.optString("summary").trim().take(2400),
                        nextAction = json.optString("next_action").trim().take(600),
                        customerText = json.optString("customer_text").trim().take(3000)
                    )
                }
            }
        }
    }
}
