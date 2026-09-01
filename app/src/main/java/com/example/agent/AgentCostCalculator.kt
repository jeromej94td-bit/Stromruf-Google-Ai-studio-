package com.example.agent

import java.util.Locale

/**
 * Kostenrechner & Transparenz-Engine für KI-Telefonie.
 * Berechnet minutengenaue Kosten für Sprachmodelle, STT, TTS und SIP-Telefonie.
 */
object AgentCostCalculator {

    data class CostBreakdown(
        val llmCostPerMin: Double,
        val sttCostPerMin: Double,
        val ttsCostPerMin: Double,
        val sipCostPerMin: Double,
        val totalPerMin: Double,
        val isFreeTier: Boolean,
        val providerName: String,
        val modelName: String,
        val details: List<Pair<String, Double>>
    )

    data class PresetModel(
        val id: String,
        val name: String,
        val provider: String,
        val model: String,
        val description: String,
        val costPerMinApprox: Double,
        val freeTierAvailable: Boolean,
        val latencyMs: Int,
        val recommendationTag: String? = null
    )

    val PRESET_MODELS = listOf(
        PresetModel(
            id = "gemini-3.5-flash",
            name = "Google Gemini 3.5 Flash",
            provider = "gemini",
            model = "gemini-3.5-flash",
            description = "Ultraschnell, intelligent & extrem günstig. 1.500 Aufrufe/Tag komplett kostenlos.",
            costPerMinApprox = 0.0012,
            freeTierAvailable = true,
            latencyMs = 350,
            recommendationTag = "Bester Allrounder (Empfohlen)"
        ),
        PresetModel(
            id = "gemini-3.1-flash-lite",
            name = "Google Gemini 3.1 Flash-Lite",
            provider = "gemini",
            model = "gemini-3.1-flash-lite-preview",
            description = "Maximale Geschwindigkeit und minimaler Ressourcenverbrauch. Kostenlos im Free-Tier.",
            costPerMinApprox = 0.0008,
            freeTierAvailable = true,
            latencyMs = 280,
            recommendationTag = "Geringste Latenz"
        ),
        PresetModel(
            id = "gemini-3.1-pro",
            name = "Google Gemini 3.1 Pro",
            provider = "gemini",
            model = "gemini-3.1-pro-preview",
            description = "Höchste Denkfähigkeit & komplexe Tarife/Regeln. Kostenlos im Free-Tier.",
            costPerMinApprox = 0.0085,
            freeTierAvailable = true,
            latencyMs = 750,
            recommendationTag = "Komplexe Beratung"
        ),
        PresetModel(
            id = "gpt-4o-mini",
            name = "OpenAI GPT-4o Mini",
            provider = "openai",
            model = "gpt-4o-mini",
            description = "Solides OpenAI-Modell für Standard-Anrufe via Whisper + Chat + TTS.",
            costPerMinApprox = 0.0220,
            freeTierAvailable = false,
            latencyMs = 600
        ),
        PresetModel(
            id = "gpt-realtime",
            name = "OpenAI Realtime Voice",
            provider = "openai",
            model = "gpt-realtime",
            description = "Direkte Audio-zu-Audio WebSocket-Verbindung. Höchste Natürlichkeit, höherer Preis.",
            costPerMinApprox = 0.1850,
            freeTierAvailable = false,
            latencyMs = 320,
            recommendationTag = "Native Realtime Audio"
        ),
        PresetModel(
            id = "claude-sonnet",
            name = "Anthropic Claude 3.5 Sonnet",
            provider = "anthropic",
            model = "claude-sonnet-4-6",
            description = "Sehr präzise deutsche Sprache und Formulierung für anspruchsvolle B2B-Telefonie.",
            costPerMinApprox = 0.0350,
            freeTierAvailable = false,
            latencyMs = 800
        )
    )

    /**
     * Berechnet die detaillierten Kosten pro Minute basierend auf der Konfiguration.
     */
    fun calculateCost(
        config: RuntimeConfig,
        direction: String = "eingehend",
        isMobile: Boolean = false,
        useOfflineTts: Boolean = false
    ): CostBreakdown {
        val provider = config.llmProvider.lowercase()
        val model = config.llmModel.lowercase()

        // 1. LLM Kosten pro Minute (Annahme: Ø 4 Dialogrunden / ~150 Input + 80 Output Tokens pro Runde = 600 In / 320 Out / Min)
        val (llmCost, isFreeTier) = when {
            provider == "gemini" -> {
                // Gemini hat 1.500 Aufrufe / Tag kostenlos
                val rate = if (model.contains("pro")) 0.0085 else 0.0012
                Pair(rate, true)
            }
            provider == "openai" && model.contains("realtime") -> {
                // $0.06/min in audio + $0.24/min out audio -> ~0.18 € / min
                Pair(0.1850, false)
            }
            provider == "openai" -> {
                // gpt-4o-mini
                Pair(0.0040, false)
            }
            provider == "anthropic" -> {
                Pair(0.0150, false)
            }
            else -> Pair(0.0020, false)
        }

        // 2. STT (Whisper / Audio-Erkennung)
        // Whisper-1 kostet $0.006 / Minute (~0.0055 €)
        val sttCost = if (model.contains("realtime")) {
            0.0 // bereits in Realtime inkludiert
        } else {
            0.0055
        }

        // 3. TTS (Sprachsynthese)
        // OpenAI TTS kostet $0.015 / 1000 Zeichen. Bei ca. 800 Zeichen Agenten-Sprechzeit / Min = ~0.011 €
        val ttsCost = when {
            useOfflineTts -> 0.0 // Kostenlos via Android Systemstimme
            model.contains("realtime") -> 0.0 // in Realtime Audio inklusive
            config.ttsModel.contains("hd") -> 0.0220
            config.speechKey.isNotBlank() -> 0.0110
            else -> 0.0 // Fallback Android TTS
        }

        // 4. SIP-Telefonie
        val sipCost = when {
            direction.lowercase() == "eingehend" -> 0.0000 // Inbound kostenlos in der Regel
            direction.lowercase() == "geraetetest" -> 0.0000 // Mikrofontest ohne Telefongebühr
            isMobile -> 0.0590 // dt. Mobilfunknetz
            else -> 0.0099 // dt. Festnetz
        }

        val total = llmCost + sttCost + ttsCost + sipCost

        val details = listOf(
            "KI-Sprachmodell (${config.llmModel})" to llmCost,
            "Spracherkennung (Whisper STT)" to sttCost,
            "Sprachausgabe (Stimme TTS)" to ttsCost,
            "SIP-Telefonie (${if (direction == "eingehend") "Eingehend Flat" else if (isMobile) "Ausgehend Mobilfunk" else "Ausgehend Festnetz"})" to sipCost
        )

        return CostBreakdown(
            llmCostPerMin = llmCost,
            sttCostPerMin = sttCost,
            ttsCostPerMin = ttsCost,
            sipCostPerMin = sipCost,
            totalPerMin = total,
            isFreeTier = isFreeTier,
            providerName = when (provider) {
                "gemini" -> "Google Gemini"
                "openai" -> "OpenAI ChatGPT"
                "anthropic" -> "Anthropic Claude"
                else -> provider.replaceFirstChar { it.uppercase() }
            },
            modelName = config.llmModel,
            details = details
        )
    }

    /**
     * Berechnet die Kosten für eine exakte Gesprächsdauer in Sekunden.
     */
    fun calculateCallCost(
        durationSec: Int,
        config: RuntimeConfig,
        direction: String = "eingehend",
        isMobile: Boolean = false
    ): Double {
        if (durationSec <= 0) return 0.0
        val breakdown = calculateCost(config, direction, isMobile)
        return (durationSec / 60.0) * breakdown.totalPerMin
    }

    /**
     * Formatiert Cent- oder Eurobeträge ansprechend (z.B. "0,018 €" oder "< 0,01 €" oder "1,24 €").
     */
    fun formatEuro(amount: Double): String {
        return when {
            amount <= 0.0001 -> "0,00 € (Kostenlos)"
            amount < 0.01 -> String.format(Locale.GERMANY, "%.3f €", amount)
            amount < 1.0 -> String.format(Locale.GERMANY, "%.2f € (%d Cent)", amount, (amount * 100).toInt())
            else -> String.format(Locale.GERMANY, "%.2f €", amount)
        }
    }

    fun formatCompactPerMin(amount: Double, isFreeTier: Boolean): String {
        return if (isFreeTier && amount < 0.02) {
            String.format(Locale.GERMANY, "~%.3f € / Min (Free Tier: 0 €)", amount)
        } else {
            String.format(Locale.GERMANY, "~%.3f € / Min", amount)
        }
    }

    /**
     * Vergleichsrechnung gegen menschliche Telefonisten / Callcenter.
     * Menschliche Telefonisten kosten ca. 30 € / Stunde = 0,50 € / Minute.
     */
    fun calculateSavings(
        totalCallMinutes: Double,
        aiCostPerMin: Double,
        humanCostPerMin: Double = 0.50
    ): Pair<Double, Double> {
        val totalAi = totalCallMinutes * aiCostPerMin
        val totalHuman = totalCallMinutes * humanCostPerMin
        val savedEuro = (totalHuman - totalAi).coerceAtLeast(0.0)
        val percent = if (totalHuman > 0) ((totalHuman - totalAi) / totalHuman) * 100.0 else 0.0
        return Pair(savedEuro, percent)
    }
}
