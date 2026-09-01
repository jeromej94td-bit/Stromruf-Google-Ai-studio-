package com.example.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

internal object SpeechHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    val JSON = "application/json; charset=utf-8".toMediaType()
}

/** Whisper-Spracherkennung (Deutsch). */
object Stt {
    fun transcribe(cfg: RuntimeConfig, wav: File): String {
        if (cfg.speechKey.isBlank() || !wav.exists() || wav.length() < 8000) return ""
        return try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", wav.name, wav.asRequestBody("audio/wav".toMediaType()))
                .addFormDataPart("model", cfg.sttModel)
                .addFormDataPart("language", "de").build()
            val req = Request.Builder()
                .url("${cfg.sttBaseUrl.trimEnd('/')}/audio/transcriptions")
                .header("Authorization", "Bearer ${cfg.speechKey}").post(body).build()
            SpeechHttp.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) "" else
                    JSONObject(r.body?.string() ?: "{}").optString("text", "")
            }
        } catch (e: Exception) { "" }
    }
}

/** ChatGPT-Sprachausgabe mit Tempo -> WAV. */
object Tts {
    fun speak(cfg: RuntimeConfig, voiceId: String, speed: Float, text: String, out: File): File? = try {
        val payload = JSONObject().put("model", cfg.ttsModel).put("voice", voiceId)
            .put("input", text).put("speed", speed.toDouble().coerceIn(0.5, 2.0))
            .put("response_format", "wav").toString()
        val req = Request.Builder()
            .url("${cfg.ttsBaseUrl.trimEnd('/')}/audio/speech")
            .header("Authorization", "Bearer ${cfg.speechKey}")
            .post(payload.toRequestBody(SpeechHttp.JSON)).build()
        SpeechHttp.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) null else { out.writeBytes(r.body!!.bytes()); out }
        }
    } catch (e: Exception) { null }
}

/** Gesprächsantworten – Google Gemini Standard, ChatGPT & Anthropic optional. */
object Llm {
    private const val FALLBACK = "Entschuldigung, da ist gerade etwas dazwischengekommen. " +
            "Könnten Sie das bitte wiederholen?"

    fun antwort(cfg: RuntimeConfig, system: String, verlauf: List<Pair<Boolean, String>>): String =
        when (cfg.llmProvider.lowercase()) {
            "anthropic" -> anthropic(cfg, system, verlauf)
            "openai" -> openAi(cfg, system, verlauf)
            else -> gemini(cfg, system, verlauf)
        }

    private fun msgs(verlauf: List<Pair<Boolean, String>>) = JSONArray().apply {
        verlauf.forEach { (vomAgent, text) ->
            put(JSONObject().put("role", if (vomAgent) "assistant" else "user").put("content", text))
        }
    }

    private fun gemini(cfg: RuntimeConfig, system: String, v: List<Pair<Boolean, String>>) = try {
        val contents = JSONArray()
        v.forEach { (vomAgent, text) ->
            contents.put(JSONObject().apply {
                put("role", if (vomAgent) "model" else "user")
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            })
        }
        if (contents.length() == 0) {
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", "Hallo, ich rufe an.")))
            })
        }
        val model = if (cfg.llmModel.startsWith("gemini")) cfg.llmModel else "gemini-3.5-flash"
        val payload = JSONObject().apply {
            put("contents", contents)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", system)))
            })
        }.toString()
        val apiKey = cfg.chatKey
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val req = Request.Builder().url(url)
            .post(payload.toRequestBody(SpeechHttp.JSON)).build()
        SpeechHttp.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) FALLBACK
            else {
                val root = JSONObject(r.body?.string() ?: "{}")
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    var answerText: String? = null
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                answerText = part.getString("text")
                                break
                            }
                        }
                    }
                    answerText ?: FALLBACK
                } else FALLBACK
            }
        }
    } catch (e: Exception) { FALLBACK }

    private fun openAi(cfg: RuntimeConfig, system: String, v: List<Pair<Boolean, String>>) = try {
        val all = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        val m = msgs(v); for (i in 0 until m.length()) all.put(m.get(i))
        val payload = JSONObject().put("model", cfg.llmModel)
            .put("messages", all).put("max_tokens", 300).toString()
        val req = Request.Builder().url("${cfg.llmBaseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer ${cfg.chatKey}")
            .post(payload.toRequestBody(SpeechHttp.JSON)).build()
        SpeechHttp.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) FALLBACK
            else JSONObject(r.body?.string() ?: "{}").getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").optString("content", FALLBACK)
        }
    } catch (e: Exception) { FALLBACK }

    private fun anthropic(cfg: RuntimeConfig, system: String, v: List<Pair<Boolean, String>>) = try {
        val payload = JSONObject().put("model", cfg.llmModel).put("max_tokens", 300)
            .put("system", system).put("messages", msgs(v)).toString()
        val req = Request.Builder().url("${cfg.llmBaseUrl.trimEnd('/')}/v1/messages")
            .header("x-api-key", cfg.chatKey).header("anthropic-version", "2023-06-01")
            .post(payload.toRequestBody(SpeechHttp.JSON)).build()
        SpeechHttp.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) FALLBACK else {
                val arr = JSONObject(r.body?.string() ?: "{}").optJSONArray("content")
                var t = FALLBACK
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("type") == "text") { t = o.optString("text"); break }
                }
                t
            }
        }
    } catch (e: Exception) { FALLBACK }
}

/** Deutscher Gesprächsprompt inkl. Wissensdatenbank und Kampagnenkontext. */
object PromptBau {
    fun system(agent: AgentProfile, kunde: String?, wissen: String, kampagne: String?): String =
        buildString {
            appendLine(agent.systemPrompt.trim())
            kunde?.let { appendLine("Du sprichst mit: $it.") }
            kampagne?.let { appendLine("Anlass des Anrufs: $it.") }
            appendLine()
            appendLine("REGELN FÜR DAS TELEFONAT (gesprochenes Deutsch):")
            appendLine("- Natürlich sprechen, keine Listen, keine Formatierung, keine Emojis.")
            appendLine(if (agent.formOfAddress == "sie")
                "- Durchgehend die höfliche Sie-Form verwenden."
                else "- Die lockere Du-Form verwenden.")
            if (agent.shortAnswers)
                appendLine("- 1 bis 3 kurze Sätze, höchstens eine Frage pro Antwort.")
            appendLine("- Zahlen, Uhrzeiten, Telefonnummern, Beträge natürlich aussprechen")
            appendLine("  (z.B. 'neunzehn Euro vierundachtzig', Nummern in kleinen Gruppen).")
            appendLine("- Keine Preise, Kundendaten oder Zusagen erfinden.")
            appendLine("- Bei Unsicherheit ehrlich sein und einen Rückruf anbieten.")
            if (agent.transferNumber.isNotBlank())
                appendLine("- Wünscht der Kunde einen Menschen, biete die Weiterleitung an " +
                        "die Nummer ${agent.transferNumber} an und verabschiede dich.")
            if (wissen.isNotBlank()) {
                appendLine(); appendLine("WISSENSDATENBANK (nur hieraus zitieren):")
                appendLine(wissen)
            }
        }
}

object WavUtil {
    fun pcmToWav(pcm: ByteArray, sampleRate: Int, channels: Int = 1, bits: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val h = ByteArrayOutputStream()
        fun i32(v: Int) { h.write(v); h.write(v shr 8); h.write(v shr 16); h.write(v shr 24) }
        fun i16(v: Int) { h.write(v); h.write(v shr 8) }
        h.write("RIFF".toByteArray()); i32(36 + pcm.size); h.write("WAVE".toByteArray())
        h.write("fmt ".toByteArray()); i32(16); i16(1); i16(channels)
        i32(sampleRate); i32(byteRate); i16(channels * bits / 8); i16(bits)
        h.write("data".toByteArray()); i32(pcm.size); h.write(pcm)
        return h.toByteArray()
    }
    fun sampleRate(wav: File): Int = runCatching {
        val b = wav.inputStream().use { s -> ByteArray(28).also { s.read(it) } }
        (b[24].toInt() and 0xFF) or ((b[25].toInt() and 0xFF) shl 8) or
        ((b[26].toInt() and 0xFF) shl 16) or ((b[27].toInt() and 0xFF) shl 24)
    }.getOrDefault(24000).coerceIn(8000, 48000)
    fun durationMs(wav: File): Long {
        if (!wav.exists() || wav.length() < 44) return 0
        return (wav.length() - 44) * 1000 / (sampleRate(wav) * 2)
    }
}
