package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.AgentBackendClient
import com.example.agent.RealtimeClient
import com.example.util.SupabaseAuthClient
import com.example.util.SupabaseDbClient
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class TestSzenario(
    val id: String,
    val name: String,
    val persona: String,
    val schwierigkeit: String,
    val kundenName: String?,
    val firma: String?,
    val zielErgebnis: String?
)

private data class AgentKurz(val id: String, val name: String, val rolle: String, val trainiert: Boolean)

/**
 * Trainingslabor fuer den KI-Agenten.
 *
 * Hier wird der Agent geuebt und geprueft, BEVOR er echte Kunden anruft:
 *  - Testgespraech ueber Mikrofon und Lautsprecher (kein SIP-Trunk noetig)
 *  - Uebungskunden mit verschiedenen Persoenlichkeiten
 *  - Live-Mitschrift und sichtbare Werkzeugaufrufe
 *  - Bewertung nach dem Gespraech
 */
@Composable
fun AgentTrainingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val phase by RealtimeClient.phase.collectAsState()
    val verlauf by RealtimeClient.verlauf.collectAsState()
    val werkzeuge by RealtimeClient.werkzeuge.collectAsState()
    val fehler by RealtimeClient.fehler.collectAsState()
    val pegel by RealtimeClient.pegel.collectAsState()
    val sessionId by RealtimeClient.sessionId.collectAsState()

    var agenten by remember { mutableStateOf<List<AgentKurz>>(emptyList()) }
    var szenarien by remember { mutableStateOf<List<TestSzenario>>(emptyList()) }
    var gewaehlterAgent by remember { mutableStateOf<AgentKurz?>(null) }
    var gewaehltesSzenario by remember { mutableStateOf<TestSzenario?>(null) }
    var textEingabe by remember { mutableStateOf("") }
    var pruefErgebnis by remember { mutableStateOf<String?>(null) }
    var pruefeGerade by remember { mutableStateOf(false) }
    var zeigeBewertung by remember { mutableStateOf(false) }
    var laden by remember { mutableStateOf(true) }

    // Stammdaten laden
    LaunchedEffect(Unit) {
        runCatching {
            val a = SupabaseDbClient.fetchTableRows(context, "agent_profiles")
            val liste = mutableListOf<AgentKurz>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                liste.add(
                    AgentKurz(
                        id = o.getString("id"),
                        name = o.optString("name", "Agent"),
                        rolle = o.optString("role", ""),
                        trainiert = o.optBoolean("is_trained", false)
                    )
                )
            }
            agenten = liste
            gewaehlterAgent = liste.firstOrNull()

            val s = SupabaseDbClient.fetchTableRows(context, "agent_test_scenarios")
            val sl = mutableListOf<TestSzenario>()
            for (i in 0 until s.length()) {
                val o = s.getJSONObject(i)
                sl.add(
                    TestSzenario(
                        id = o.getString("id"),
                        name = o.optString("name", "Testkunde"),
                        persona = o.optString("persona", ""),
                        schwierigkeit = o.optString("difficulty", "normal"),
                        kundenName = o.optString("customer_name").takeIf { it.isNotBlank() && it != "null" },
                        firma = o.optString("customer_company").takeIf { it.isNotBlank() && it != "null" },
                        zielErgebnis = o.optString("expected_outcome").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
            szenarien = sl
        }
        laden = false
    }

    val laeuftGespraech = phase != RealtimeClient.Phase.AUS && phase != RealtimeClient.Phase.FEHLER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Trainingslabor",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Sprich hier selbst mit deinem Agenten, bevor er echte Kunden anruft. " +
                    "Es wird kein Telefonanschluss gebraucht, alles laeuft ueber Mikrofon und Lautsprecher.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---------- Verbindung pruefen ----------
        if (!laeuftGespraech) {
            OutlinedButton(
                onClick = {
                    pruefeGerade = true
                    pruefErgebnis = null
                    scope.launch {
                        val ergebnis = pruefeVerbindung(context)
                        pruefErgebnis = ergebnis
                        pruefeGerade = false
                    }
                },
                enabled = !pruefeGerade,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (pruefeGerade) "Pruefe ..." else "Verbindung zu ChatGPT pruefen")
            }
            pruefErgebnis?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("OK")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        // ---------- Auswahl ----------
        if (!laeuftGespraech) {
            if (laden) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Text("Welcher Agent?", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (agenten.isEmpty() && !laden) {
                Text(
                    "Noch kein Agent angelegt. Lege zuerst unter Agenten einen an.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                agenten.chunked(2).forEach { reihe ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reihe.forEach { a ->
                            FilterChip(
                                selected = gewaehlterAgent?.id == a.id,
                                onClick = { gewaehlterAgent = a },
                                label = {
                                    Text(
                                        (if (a.trainiert) "OK " else "") + a.name,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Text(
                "Uebungskunde (optional)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Waehle eine Rolle, die du selbst spielst. Ohne Auswahl sprichst du einfach frei.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = gewaehltesSzenario == null,
                    onClick = { gewaehltesSzenario = null },
                    label = { Text("Freies Gespraech") }
                )
                szenarien.forEach { s ->
                    val farbe = when (s.schwierigkeit) {
                        "leicht" -> "leicht"
                        "schwer" -> "schwer"
                        else -> "normal"
                    }
                    FilterChip(
                        selected = gewaehltesSzenario?.id == s.id,
                        onClick = { gewaehltesSzenario = s },
                        label = { Text("${s.name} (${farbe})", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            gewaehltesSzenario?.let { s ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Deine Rolle:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(s.persona, style = MaterialTheme.typography.bodySmall)
                        s.zielErgebnis?.let {
                            Text(
                                "Der Agent besteht, wenn: $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // ---------- Statusanzeige ----------
        if (laeuftGespraech) {
            val statusText = when (phase) {
                RealtimeClient.Phase.VERBINDET -> "Verbinde mit ChatGPT ..."
                RealtimeClient.Phase.BEREIT -> "Sprich einfach los"
                RealtimeClient.Phase.AGENT_SPRICHT -> "Agent spricht"
                RealtimeClient.Phase.KUNDE_SPRICHT -> "Ich hoere dich"
                else -> ""
            }
            val pegelAnim by animateFloatAsState(targetValue = pegel, label = "pegel")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (phase) {
                        RealtimeClient.Phase.AGENT_SPRICHT -> MaterialTheme.colorScheme.primaryContainer
                        RealtimeClient.Phase.KUNDE_SPRICHT -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(statusText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier
                            .size((44 + pegelAnim * 26).dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + pegelAnim * 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    gewaehlterAgent?.let {
                        Text(
                            "${it.name}${if (it.rolle.isNotBlank()) " (" + it.rolle + ")" else ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        fehler?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // ---------- Start / Stopp ----------
        if (!laeuftGespraech) {
            Button(
                onClick = {
                    val token = SupabaseAuthClient.getSessionToken(context)
                    if (token.isNullOrBlank()) {
                        pruefErgebnis = "Bitte zuerst in der App anmelden."
                        return@Button
                    }
                    RealtimeClient.starten(
                        context = context,
                        loginToken = token,
                        agentId = gewaehlterAgent?.id,
                        szenarioId = gewaehltesSzenario?.id,
                        test = true,
                        onBeendet = { zeigeBewertung = true }
                    )
                },
                enabled = gewaehlterAgent != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Testgespraech starten", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { RealtimeClient.beenden() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Gespraech beenden", fontWeight = FontWeight.Bold)
            }

            // Tippen statt sprechen
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textEingabe,
                    onValueChange = { textEingabe = it },
                    placeholder = { Text("oder tippen statt sprechen") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        RealtimeClient.textSenden(textEingabe.trim())
                        textEingabe = ""
                    },
                    enabled = textEingabe.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Senden")
                }
            }
        }

        // ---------- Mitschrift ----------
        if (verlauf.isNotEmpty()) {
            HorizontalDivider()
            Text("Mitschrift", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            verlauf.forEach { z ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (z.vomAgent) Arrangement.Start else Arrangement.End
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (z.vomAgent)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                if (z.vomAgent) (gewaehlterAgent?.name ?: "Agent") else "Du",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(z.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // ---------- Werkzeuge, die der Agent benutzt hat ----------
        if (werkzeuge.isNotEmpty()) {
            HorizontalDivider()
            Text(
                "Das hat der Agent erledigt",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            werkzeuge.forEach { w ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            werkzeugName(w.name),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val text = w.argumente.keys().asSequence()
                            .joinToString("  ") { k -> "$k: ${w.argumente.opt(k)}" }
                        if (text.isNotBlank()) {
                            Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            w.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (w.status == "Fehler") MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        w.ergebnis?.optString("hinweis")?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    // ---------- Bewertung nach dem Gespraech ----------
    if (zeigeBewertung) {
        BewertungsDialog(
            anzahlZeilen = verlauf.size,
            anzahlWerkzeuge = werkzeuge.size,
            zielErgebnis = gewaehltesSzenario?.zielErgebnis,
            onSchliessen = { zeigeBewertung = false },
            onSpeichern = { punkte, notiz, freigeben ->
                scope.launch {
                    speichereBewertung(
                        context = context,
                        sessionId = sessionId,
                        agentId = gewaehlterAgent?.id,
                        punkte = punkte,
                        notiz = notiz,
                        freigeben = freigeben,
                        verlauf = verlauf,
                        werkzeuge = werkzeuge
                    )
                    if (freigeben && gewaehlterAgent != null) {
                        agenten = agenten.map {
                            if (it.id == gewaehlterAgent!!.id) it.copy(trainiert = true) else it
                        }
                        gewaehlterAgent = gewaehlterAgent!!.copy(trainiert = true)
                    }
                }
                zeigeBewertung = false
            }
        )
    }
}

@Composable
private fun BewertungsDialog(
    anzahlZeilen: Int,
    anzahlWerkzeuge: Int,
    zielErgebnis: String?,
    onSchliessen: () -> Unit,
    onSpeichern: (Int, String, Boolean) -> Unit
) {
    var punkte by remember { mutableStateOf(70f) }
    var notiz by remember { mutableStateOf("") }
    var freigeben by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text("Wie war das Gespraech?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "$anzahlZeilen Gespraechsbeitraege, $anzahlWerkzeuge Aktionen des Agenten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                zielErgebnis?.let {
                    Text("Ziel war: $it", style = MaterialTheme.typography.bodySmall)
                }
                Text("Bewertung: ${punkte.toInt()} von 100", style = MaterialTheme.typography.labelLarge)
                Slider(value = punkte, onValueChange = { punkte = it }, valueRange = 0f..100f)
                OutlinedTextField(
                    value = notiz,
                    onValueChange = { notiz = it },
                    label = { Text("Was soll er besser machen?") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = freigeben, onCheckedChange = { freigeben = it })
                    Text(
                        "Agent ist bereit fuer echte Anrufe",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSpeichern(punkte.toInt(), notiz, freigeben) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onSchliessen) { Text("Verwerfen") }
        }
    )
}

private fun werkzeugName(name: String): String = when (name) {
    "kontakt_notiz" -> "Notiz festgehalten"
    "ergebnis_setzen" -> "Ergebnis gesetzt"
    "wiedervorlage_anlegen" -> "Wiedervorlage angelegt"
    "interesse_markieren" -> "Kundendaten erfasst"
    "weiterleiten" -> "Weiterleitung angefordert"
    "gespraech_beenden" -> "Gespraech beendet"
    else -> name
}

/** Prueft den verschluesselt in Supabase Vault gespeicherten Schluessel. */
private suspend fun pruefeVerbindung(context: android.content.Context): String {
    val token = SupabaseAuthClient.getSessionToken(context)
        ?: return "Bitte zuerst in der App anmelden."
    return try {
        val antwort = AgentBackendClient.checkConnection(token)
        val modelle = antwort.optJSONArray("verfuegbare_realtime_modelle")
        "OK - Schluessel gueltig, ${modelle?.length() ?: 0} Realtime-Modelle verfuegbar."
    } catch (e: Exception) {
        "Fehler: ${e.message}"
    }
}

/** Speichert Mitschrift, Aktionen und Bewertung in Supabase (Aufbewahrung 7 Tage). */
private suspend fun speichereBewertung(
    context: android.content.Context,
    sessionId: String?,
    agentId: String?,
    punkte: Int,
    notiz: String,
    freigeben: Boolean,
    verlauf: List<RealtimeClient.Zeile>,
    werkzeuge: List<RealtimeClient.Werkzeugaufruf>
) {
    runCatching {
        if (sessionId != null) {
            val transkript = org.json.JSONArray()
            verlauf.forEach { z ->
                transkript.put(
                    JSONObject()
                        .put("rolle", if (z.vomAgent) "agent" else "kunde")
                        .put("text", z.text)
                        .put("zeit", z.zeit)
                )
            }
            val payload = JSONObject()
                .put("id", sessionId)
                .put("status", "beendet")
                .put("ended_at", java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.GERMANY
                ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
                .put("transcript", transkript)
                .put("score", punkte)
                .put("score_notes", notiz)
            SupabaseDbClient.upsertTableRow(context, "agent_call_sessions", payload)

            // Werkzeugaufrufe sind bereits transaktional ueber die Edge Function gespeichert.
        }
        if (freigeben && agentId != null) {
            SupabaseDbClient.upsertTableRow(
                context, "agent_profiles",
                JSONObject().put("id", agentId).put("is_trained", true)
            )
        }
    }
}
