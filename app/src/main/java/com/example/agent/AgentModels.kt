package com.example.agent

import java.util.UUID

/** Ein KI-Agent (agent_profiles). */
data class AgentProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Mia",
    val role: String = "Empfang",
    val direction: String = "beide",               // eingehend | ausgehend | beide
    val greeting: String = "Guten Tag, mein Name ist Mia. Wie kann ich Ihnen weiterhelfen?",
    val systemPrompt: String = "Du bist eine freundliche Mitarbeiterin eines deutschen Energieunternehmens.",
    val language: String = "de",
    val voiceId: String = "nova",
    val voiceSpeed: Float = 1.0f,                  // 0.5 langsam … 2.0 schnell
    val formOfAddress: String = "sie",
    val maxParallel: Int = 2,
    val listenWindowSec: Int = 6,
    val maxDurationMin: Int = 15,
    val aiDisclosure: Boolean = true,
    val shortAnswers: Boolean = true,
    val transferNumber: String = "",
    val useKnowledge: Boolean = true,
    val isActive: Boolean = false,
    val sortOrder: Int = 0
)

/** SIP + Schlüssel (agent_runtime_config). Google Gemini & ChatGPT unterstützt. */
data class RuntimeConfig(
    val sipDisplayName: String = "Mein SIP-Trunk",
    val sipUser: String = "", val sipPassword: String = "",
    val sipDomain: String = "", val sipProxy: String = "",
    val sipPort: Int = 5060, val sipTransport: String = "UDP",
    val sipCallerId: String = "", val sipMaxLines: Int = 4,
    val routingStrategy: String = "round_robin",
    val fixedAgentId: String? = null,
    val llmProvider: String = "gemini",            // gemini | openai | anthropic
    val llmBaseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    val llmModel: String = "gemini-3.5-flash",
    val llmApiKey: String = "",                    // optional (z.B. Anthropic / Custom)
    val geminiApiKey: String = "",                 // Google Gemini API Key
    val sttBaseUrl: String = "https://api.openai.com/v1",
    val sttModel: String = "whisper-1",
    val ttsBaseUrl: String = "https://api.openai.com/v1",
    val ttsModel: String = "tts-1",
    val openaiApiKey: String = "",                 // Schlüssel für STT+TTS+OpenAI Chat
    val aiDisclosureText: String = "Kurzer Hinweis: Sie sprechen mit einem digitalen Assistenten.",
    val recordingEnabled: Boolean = true,
    val retentionDays: Int = 7
) {
    val chatKey: String get() = when (llmProvider.lowercase()) {
        "gemini" -> geminiApiKey.ifBlank { llmApiKey }.ifBlank { openaiApiKey }
        "openai" -> llmApiKey.ifBlank { openaiApiKey }
        else -> llmApiKey.ifBlank { geminiApiKey }.ifBlank { openaiApiKey }
    }
    val speechKey: String get() = openaiApiKey
}

/** Gespeicherte Session (agent_call_sessions). */
data class CallSessionRow(
    val id: String,
    val agentName: String, val agentRole: String?,
    val direction: String, val remoteNumber: String?, val contactName: String?,
    val startedAt: Long, val durationSec: Int,
    val status: String, val outcome: String?, val summary: String?, val sentiment: String?,
    val transcript: List<Pair<Boolean, String>>,
    val recordingPath: String?, val recordingExpiresAt: Long?
)

/** Wissensquelle (agent_knowledge). */
data class KnowledgeEntry(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String? = null,                   // null = für alle Agenten
    val title: String = "",
    val sourceType: String = "text",               // text | url
    val sourceUrl: String = "",
    val content: String = "",
    val isActive: Boolean = true
)

/** Kampagne (agent_campaigns) – z.B. Hotbox-Liste anrufen. */
data class Campaign(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val agentId: String = "",
    val hotboxListName: String? = null,            // null = alle Hotbox-Kontakte
    val status: String = "pausiert",               // aktiv | pausiert | fertig
    val startHour: Int = 9, val endHour: Int = 18,
    val maxParallel: Int = 1, val maxAttempts: Int = 2,
    val offen: Int = 0, val erledigt: Int = 0, val gesamt: Int = 0
)

data class CampaignCall(
    val id: String, val contactId: String, val contactName: String?,
    val phone: String, val attempts: Int, val status: String
)

/** Aktion des Agenten (agent_actions). */
data class AgentAction(
    val id: String, val sessionId: String?, val toolName: String,
    val arguments: String, val reason: String?, val status: String,
    val error: String?, val createdAt: Long
) {
    val titel: String get() = when (toolName) {
        "kontakt_suchen" -> "Kontakt gesucht"
        "kontakt_anlegen" -> "Kontakt angelegt"
        "kontakt_aktualisieren" -> "Kontakt aktualisiert"
        "wiedervorlage_anlegen" -> "Wiedervorlage angelegt"
        "gespraechsergebnis_setzen" -> "Ergebnis gesetzt"
        "notiz_an_anruf" -> "Notiz gespeichert"
        "hotbox_setzen" -> "Hotbox geändert"
        "kontakt_sperren" -> "Kontakt gesperrt"
        else -> toolName
    }
}

data class ToolPolicy(
    val autoApply: Boolean = false,
    val allowedTools: List<String> = AlleWerkzeuge.liste.map { it.first },
    val maxActions: Int = 8,
    val extraPrompt: String = ""
)

object AlleWerkzeuge {
    val liste = listOf(
        "kontakt_suchen" to "Kontakte suchen",
        "kontakt_anlegen" to "Neue Kontakte anlegen",
        "kontakt_aktualisieren" to "Kontaktdaten ergänzen",
        "wiedervorlage_anlegen" to "Wiedervorlagen & Termine setzen",
        "gespraechsergebnis_setzen" to "Gesprächsergebnis setzen",
        "notiz_an_anruf" to "Notiz an den Anruf",
        "hotbox_setzen" to "Hotbox pflegen",
        "kontakt_sperren" to "Nicht-mehr-anrufen setzen"
    )
}

/** Deutsche Auswahl der ChatGPT-Stimmen. */
object Stimmen {
    val liste = listOf(
        "nova" to "Nova – weiblich, warm, serviceorientiert",
        "shimmer" to "Shimmer – weiblich, hell, freundlich",
        "alloy" to "Alloy – neutral, sachlich",
        "onyx" to "Onyx – männlich, tief, seriös",
        "echo" to "Echo – männlich, ruhig",
        "fable" to "Fable – warm, erzählend"
    )
    fun tempoLabel(s: Float) = when {
        s < 0.85f -> "langsam"
        s > 1.2f -> "schnell"
        else -> "normal"
    }
}

// ---------- Laufzeit ----------
enum class SessionStatus(val label: String) {
    KLINGELT("Klingelt"), VERBINDET("Verbindet"), SPRICHT("Spricht"),
    HOERT_ZU("Hört zu"), DENKT("Denkt"), BEENDET("Beendet"), FEHLER("Fehler");
    val aktiv get() = this != BEENDET && this != FEHLER
}
enum class SessionMode { SIP, GERAETETEST }
data class TranscriptLine(val vomAgent: Boolean, val text: String,
                          val ts: Long = System.currentTimeMillis())
data class Latenzen(var sttMs: Long = 0, var llmMs: Long = 0, var ttsMs: Long = 0)
