package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.AgentCostCalculator
import com.example.agent.AgentRuntime
import java.util.Locale

private val Bg = Color(0xFF0F172A)
private val Karte = Color(0xFF1E293B)
private val KarteHell = Color(0xFF334155)
private val ThemeAccent = Color(0xFF38BDF8)
private val Gruen = Color(0xFF22C55E)
private val Gelb = Color(0xFFEAB308)
private val Lila = Color(0xFFA855F7)

@Composable
fun AgentKostenScreen(
    modifier: Modifier = Modifier
) {
    val cfg by AgentRuntime.config.collectAsState()

    // Simulation states
    var anrufeProTag by remember { mutableFloatStateOf(25f) }
    var dauerMinuten by remember { mutableFloatStateOf(3f) }
    var simuliertesModell by remember { mutableStateOf(cfg.llmModel.ifBlank { "gemini-3.5-flash" }) }
    var leitungTyp by remember { mutableStateOf("eingehend") } // eingehend | festnetz | mobilfunk
    var showFaqDetails by remember { mutableStateOf(true) }

    // Live Breakdown based on current runtime config
    val currentBreakdown = remember(cfg) {
        AgentCostCalculator.calculateCost(cfg, direction = "eingehend")
    }

    // Simulated Breakdown
    val simConfig = remember(cfg, simuliertesModell) {
        val provider = when {
            simuliertesModell.startsWith("gemini") -> "gemini"
            simuliertesModell.startsWith("claude") -> "anthropic"
            else -> "openai"
        }
        cfg.copy(llmProvider = provider, llmModel = simuliertesModell)
    }

    val simBreakdown = remember(simConfig, leitungTyp) {
        AgentCostCalculator.calculateCost(
            simConfig,
            direction = if (leitungTyp == "eingehend") "eingehend" else "ausgehend",
            isMobile = leitungTyp == "mobilfunk"
        )
    }

    // Monthly volume calculations (22 working days)
    val gespraechsMinutenMonat = (anrufeProTag * dauerMinuten * 22).toDouble()
    val anrufeMonat = (anrufeProTag * 22).toInt()
    val kostenMonatKi = gespraechsMinutenMonat * simBreakdown.totalPerMin
    val kostenMonatMensch = gespraechsMinutenMonat * 0.50 // 30 €/h
    val ersparnisMonat = (kostenMonatMensch - kostenMonatKi).coerceAtLeast(0.0)
    val ersparnisProzent = if (kostenMonatMensch > 0) (ersparnisMonat / kostenMonatMensch) * 100.0 else 0.0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. Header: Aktuelle Live-Kostenrate ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(ThemeAccent.copy(alpha = 0.6f), Lila.copy(alpha = 0.6f))),
                        RoundedCornerShape(16.dp)
                    )
                    .background(
                        Brush.verticalGradient(listOf(Karte, Color(0xFF131D31))),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(ThemeAccent.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = ThemeAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("DEINE AKTUELLEN AGENTEN-KOSTEN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ThemeAccent, letterSpacing = 1.sp)
                            Text(
                                "${currentBreakdown.providerName} (${currentBreakdown.modelName})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        if (currentBreakdown.isFreeTier) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Gruen.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Gruen.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "FREE TIER AKTIV",
                                    color = Gruen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiBox(
                            label = "Kosten pro Minute",
                            wert = String.format(Locale.GERMANY, "%.3f €", currentBreakdown.totalPerMin),
                            unterzeile = if (currentBreakdown.isFreeTier) "0 € bis 1.500 Calls/Tag" else "Minutengenaue Abrechnung",
                            accentColor = ThemeAccent,
                            modifier = Modifier.weight(1f)
                        )
                        KpiBox(
                            label = "Ø Anruf (3 Min)",
                            wert = String.format(Locale.GERMANY, "%.3f €", currentBreakdown.totalPerMin * 3.0),
                            unterzeile = "ca. ${(currentBreakdown.totalPerMin * 300).toInt().coerceAtLeast(1)} Cent pro Call",
                            accentColor = Gruen,
                            modifier = Modifier.weight(1f)
                        )
                        KpiBox(
                            label = "Ersparnis",
                            wert = "ca. 96%",
                            unterzeile = "vs. Mitarbeiter (0,50 €/m)",
                            accentColor = Lila,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // --- 2. Live Kostenaufschlüsselung der Komponenten ---
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Karte)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "AUFSCHLÜSSELUNG PRO GESPRÄCHSMINUTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    KostenPosition(
                        icon = Icons.Default.Psychology,
                        titel = "KI-Sprachmodell & Intelligenz",
                        modell = "${currentBreakdown.providerName} · ${currentBreakdown.modelName}",
                        betrag = currentBreakdown.llmCostPerMin,
                        hinweis = if (currentBreakdown.isFreeTier) "Im Free-Tier 0,00 € (bis 1 Mio. Tokens/Tag)" else "Pay-as-you-go je nach Tokenverbrauch"
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.06f))

                    KostenPosition(
                        icon = Icons.Default.RecordVoiceOver,
                        titel = "Spracherkennung (STT - Whisper)",
                        modell = "OpenAI Whisper-1 (Echtzeit-Transkript)",
                        betrag = currentBreakdown.sttCostPerMin,
                        hinweis = "0,006 $ (~0,0055 €) pro Minute Audioeingang"
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.06f))

                    KostenPosition(
                        icon = Icons.Default.RecordVoiceOver,
                        titel = "Sprachausgabe (TTS - Natürliche Stimme)",
                        modell = "OpenAI TTS-1 / Nova / Onyx / Echo",
                        betrag = currentBreakdown.ttsCostPerMin,
                        hinweis = "0,015 $ je 1.000 Zeichen (oder kostenlos via Android Systemstimme)"
                    )

                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.06f))

                    KostenPosition(
                        icon = Icons.Default.PhoneInTalk,
                        titel = "SIP-Telefonie & Trunk",
                        modell = "Eingehende Anrufe (Inbound SIP)",
                        betrag = currentBreakdown.sipCostPerMin,
                        hinweis = "0,00 € bei eingehender Flatrate (Ausgehend: ~0,01 € Festnetz / ~0,059 € Mobilfunk)"
                    )

                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(KarteHell.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gesamtkosten pro Minute:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Text(
                                String.format(Locale.GERMANY, "%.3f € / Minute", currentBreakdown.totalPerMin),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ThemeAccent
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Interaktiver Kosten- & ROI-Rechner (Simulator) ---
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Karte)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Calculate, null, tint = ThemeAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("INTERAKTIVER KOSTEN- & ROI-SIMULATOR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("Passe dein Anrufvolumen an und sieh sofort die monatlichen Kosten und Ersparnisse.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))

                    Spacer(Modifier.height(14.dp))

                    // Modell-Auswahl
                    Text("KI-Modell für Simulation:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = simuliertesModell == "gemini-3.5-flash",
                            onClick = { simuliertesModell = "gemini-3.5-flash" },
                            label = { Text("Gemini 3.5 Flash", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeAccent, selectedLabelColor = Color.Black)
                        )
                        FilterChip(
                            selected = simuliertesModell == "gpt-4o-mini",
                            onClick = { simuliertesModell = "gpt-4o-mini" },
                            label = { Text("GPT-4o Mini", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeAccent, selectedLabelColor = Color.Black)
                        )
                        FilterChip(
                            selected = simuliertesModell == "gpt-realtime",
                            onClick = { simuliertesModell = "gpt-realtime" },
                            label = { Text("Realtime Audio", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemeAccent, selectedLabelColor = Color.Black)
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Leitungsart
                    Text("Telefonie-Verbindung:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = leitungTyp == "eingehend",
                            onClick = { leitungTyp = "eingehend" },
                            label = { Text("Eingehend (0 €)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = leitungTyp == "festnetz",
                            onClick = { leitungTyp = "festnetz" },
                            label = { Text("Ausgehend Festnetz (~1 ct)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = leitungTyp == "mobilfunk",
                            onClick = { leitungTyp = "mobilfunk" },
                            label = { Text("Ausgehend Mobilfunk (~5,9 ct)", fontSize = 11.sp) }
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Slider 1: Anrufe pro Tag
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Anrufe pro Tag:", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(Modifier.weight(1f))
                        Text("${anrufeProTag.toInt()} Anrufe / Tag (~$anrufeMonat / Monat)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeAccent)
                    }
                    Slider(
                        value = anrufeProTag,
                        onValueChange = { anrufeProTag = it },
                        valueRange = 5f..300f,
                        steps = 58,
                        colors = SliderDefaults.colors(thumbColor = ThemeAccent, activeTrackColor = ThemeAccent)
                    )

                    // Slider 2: Dauer pro Anruf
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ø Gesprächsdauer:", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(Modifier.weight(1f))
                        Text(String.format(Locale.GERMANY, "%.1f Minuten", dauerMinuten), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeAccent)
                    }
                    Slider(
                        value = dauerMinuten,
                        onValueChange = { dauerMinuten = it },
                        valueRange = 1f..10f,
                        steps = 17,
                        colors = SliderDefaults.colors(thumbColor = ThemeAccent, activeTrackColor = ThemeAccent)
                    )

                    Spacer(Modifier.height(10.dp))

                    // Ergebnis-Karten
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth().border(1.dp, ThemeAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Monatliches Telefonievolumen:", fontSize = 12.sp, color = Color.Gray)
                                Spacer(Modifier.weight(1f))
                                Text("${gespraechsMinutenMonat.toInt()} Minuten", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Monatskosten KI-Agent:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    String.format(Locale.GERMANY, "%.2f € / Monat", kostenMonatKi),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ThemeAccent
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Vergleich Callcenter / Personal:", fontSize = 12.sp, color = Color.Gray)
                                Spacer(Modifier.weight(1f))
                                Text(String.format(Locale.GERMANY, "%.2f €", kostenMonatMensch), fontSize = 12.sp, color = Color.Gray)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.08f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Savings, null, tint = Gruen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Monatliche Ersparnis:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gruen)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    String.format(Locale.GERMANY, "+%.2f € (%.1f%%)", ersparnisMonat, ersparnisProzent),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Gruen
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Modellvergleich: Tabelle der KI-Modelle ---
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Karte)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("VERGLEICH ALLER SPRACHMODELLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))

                    AgentCostCalculator.PRESET_MODELS.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(KarteHell.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(model.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    model.recommendationTag?.let { tag ->
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = ThemeAccent.copy(alpha = 0.15f)
                                        ) {
                                            Text(tag, fontSize = 9.sp, color = ThemeAccent, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text(model.description, fontSize = 11.sp, color = Color.LightGray, modifier = Modifier.padding(top = 2.dp))
                                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("⚡ ${model.latencyMs} ms", fontSize = 10.sp, color = Color.Gray)
                                    if (model.freeTierAvailable) {
                                        Text("✓ 1.500 Calls/Tag Free", fontSize = 10.sp, color = Gruen)
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    String.format(Locale.GERMANY, "%.3f €", model.costPerMinApprox),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (model.costPerMinApprox < 0.01) Gruen else ThemeAccent
                                )
                                Text("pro Minute", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        // --- 5. "Ab wann muss man zahlen?" & FAQ Transparenz ---
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Karte),
                modifier = Modifier.clickable { showFaqDetails = !showFaqDetails }
            ) {
                Column(Modifier.padding(14.dp).animateContentSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, null, tint = Gelb, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AB WANN MUSS MAN ZAHLEN? (FAQ)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                        Icon(if (showFaqDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
                    }

                    if (showFaqDetails) {
                        Spacer(Modifier.height(10.dp))

                        FaqItem(
                            frage = "Gibt es eine kostenlose Testphase?",
                            antwort = "Ja! Mit Google Gemini (Standard) sind bis zu 1.500 Aufrufe pro Tag und 1.000.000 Tokens pro Minute im Free Tier vollkommen 0,00 € kostenlos. Du kannst den Agenten ausführlich testen, ohne einen einzigen Cent zu bezahlen."
                        )

                        FaqItem(
                            frage = "Ab wann entstehen echte Kosten?",
                            antwort = "Kosten entstehen erst, wenn du:\n1. Das kostenlose Google Gemini Kontingent überschreitest und aktiv Pay-as-you-go in Google Cloud aktivierst.\n2. OpenAI (ChatGPT / Whisper) nutzt und Guthaben auflädst.\n3. Eigene ausgehende Telefonate über deinen SIP-Trunk ins Festnetz (~1 ct/Min) oder Mobilfunk (~6 ct/Min) führst."
                        )

                        FaqItem(
                            frage = "Gibt es ein monatliches Abo oder versteckte Gebühren?",
                            antwort = "Nein. Es gibt kein Zwangs-Abo und keine versteckten Gebühren in STROMRUF. Alle API-Kosten laufen transparent und direkt über deinen eigenen API-Schlüssel (Google Cloud / OpenAI) bzw. SIP-Provider."
                        )

                        FaqItem(
                            frage = "Wie zahle ich bei Google oder OpenAI?",
                            antwort = "Du hinterlegst bei Google Cloud oder OpenAI eine Zahlungsmethode (Kreditkarte oder Lastschrift/Prepaid). Bei OpenAI lädst du z.B. 5 $ oder 10 $ Startguthaben auf. Bei Google Gemini startest du komplett ohne Zahlungspflicht."
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun KpiBox(
    label: String,
    wert: String,
    unterzeile: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = KarteHell.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Spacer(Modifier.height(2.dp))
            Text(wert, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Spacer(Modifier.height(2.dp))
            Text(unterzeile, fontSize = 9.sp, color = Color.LightGray, maxLines = 1)
        }
    }
}

@Composable
private fun KostenPosition(
    icon: ImageVector,
    titel: String,
    modell: String,
    betrag: Double,
    hinweis: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).background(KarteHell, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = ThemeAccent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(titel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(modell, fontSize = 11.sp, color = Color.LightGray)
            Text(hinweis, fontSize = 10.sp, color = Color.Gray)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (betrag <= 0.0001) "0,00 €" else String.format(Locale.GERMANY, "%.3f €", betrag),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (betrag <= 0.0001) Gruen else Color.White
            )
            Text("pro Min", fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun FaqItem(
    frage: String,
    antwort: String
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text("• $frage", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ThemeAccent)
        Spacer(Modifier.height(2.dp))
        Text(antwort, fontSize = 11.sp, color = Color.LightGray, lineHeight = 16.sp)
    }
}
