package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.agent.AgentBackendClient
import com.example.util.SupabaseAuthClient
import com.example.util.SupabaseDbClient
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hier wird festgelegt, WIE der Agent arbeitet.
 * Alles wird sofort in Supabase gespeichert und gilt beim naechsten Gespraech.
 */
@Composable
fun AgentEinstellungenScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var laden by remember { mutableStateOf(true) }
    var meldung by remember { mutableStateOf<String?>(null) }

    // Zugang
    var openAiKey by remember { mutableStateOf("") }
    var keyVorhanden by remember { mutableStateOf(false) }
    var modell by remember { mutableStateOf("gpt-realtime") }

    // Agent
    var agentId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var rolle by remember { mutableStateOf("") }
    var begruessung by remember { mutableStateOf("") }
    var auftrag by remember { mutableStateOf("") }
    var ziel by remember { mutableStateOf("") }
    var erfolg by remember { mutableStateOf("") }
    var einwaende by remember { mutableStateOf("") }
    var verboten by remember { mutableStateOf("") }
    var stimme by remember { mutableStateOf("marin") }
    var tempo by remember { mutableStateOf(1.0f) }
    var anrede by remember { mutableStateOf("sie") }
    var kurzeAntworten by remember { mutableStateOf(true) }
    var kiHinweis by remember { mutableStateOf(true) }
    var unterbrechbar by remember { mutableStateOf(true) }
    var pauseMs by remember { mutableStateOf(600f) }
    var empfindlichkeit by remember { mutableStateOf(0.5f) }
    var denkstufe by remember { mutableStateOf("low") }
    var maxMinuten by remember { mutableStateOf(8f) }
    var weiterleitung by remember { mutableStateOf("") }
    var werkzeuge by remember { mutableStateOf(setOf(
        "kontakt_notiz", "wiedervorlage_anlegen", "ergebnis_setzen",
        "interesse_markieren", "weiterleiten", "gespraech_beenden"
    )) }

    LaunchedEffect(Unit) {
        runCatching {
            val cfg = SupabaseDbClient.fetchTableRows(context, "agent_runtime_config")
            if (cfg.length() > 0) {
                val o = cfg.getJSONObject(0)
                keyVorhanden = o.optString("openai_key_last4").isNotBlank()
                modell = o.optString("realtime_model", "gpt-realtime")
            }
            val agents = SupabaseDbClient.fetchTableRows(context, "agent_profiles")
            if (agents.length() > 0) {
                val a = agents.getJSONObject(0)
                agentId = a.getString("id")
                name = a.optString("name", "")
                rolle = a.optString("role", "")
                begruessung = a.optString("greeting", "")
                auftrag = a.optString("system_prompt", "")
                ziel = a.optString("goal", "")
                erfolg = a.optString("success_criteria", "")
                einwaende = a.optString("objection_handling", "")
                verboten = a.optString("forbidden_topics", "")
                stimme = a.optString("voice_id", "marin")
                tempo = a.optDouble("voice_speed", 1.0).toFloat()
                anrede = a.optString("form_of_address", "sie")
                kurzeAntworten = a.optBoolean("short_answers", true)
                kiHinweis = a.optBoolean("ai_disclosure", true)
                unterbrechbar = a.optBoolean("allow_interruption", true)
                pauseMs = a.optInt("vad_silence_ms", 600).toFloat()
                empfindlichkeit = a.optDouble("vad_threshold", 0.5).toFloat()
                denkstufe = a.optString("reasoning_effort", "low")
                maxMinuten = a.optInt("max_duration_min", 8).toFloat()
                weiterleitung = a.optString("transfer_number", "")
                val t = a.optJSONArray("enabled_tools")
                if (t != null) {
                    val menge = mutableSetOf<String>()
                    for (i in 0 until t.length()) menge.add(t.getString(i))
                    if (menge.isNotEmpty()) werkzeuge = menge
                }
            }
        }
        laden = false
    }

    fun speichern() {
        scope.launch {
            runCatching {
                if (openAiKey.isNotBlank()) {
                    val token = SupabaseAuthClient.getSessionToken(context)
                        ?: throw IllegalStateException("Bitte zuerst in der App anmelden.")
                    AgentBackendClient.saveOpenAiKey(token, openAiKey)
                }
                SupabaseDbClient.upsertTableRow(
                    context,
                    "agent_runtime_config",
                    JSONObject().put("realtime_model", modell).put("realtime_enabled", true)
                )
                agentId?.let { id ->
                    val werkzeugArray = JSONArray()
                    werkzeuge.forEach { werkzeugArray.put(it) }
                    val a = JSONObject()
                        .put("id", id)
                        .put("name", name)
                        .put("role", rolle)
                        .put("greeting", begruessung)
                        .put("system_prompt", auftrag)
                        .put("goal", ziel)
                        .put("success_criteria", erfolg)
                        .put("objection_handling", einwaende)
                        .put("forbidden_topics", verboten)
                        .put("voice_id", stimme)
                        .put("voice_speed", tempo.toDouble())
                        .put("form_of_address", anrede)
                        .put("short_answers", kurzeAntworten)
                        .put("ai_disclosure", kiHinweis)
                        .put("allow_interruption", unterbrechbar)
                        .put("vad_silence_ms", pauseMs.toInt())
                        .put("vad_threshold", empfindlichkeit.toDouble())
                        .put("reasoning_effort", denkstufe)
                        .put("max_duration_min", maxMinuten.toInt())
                        .put("transfer_number", weiterleitung)
                        .put("realtime_model", modell)
                        .put("enabled_tools", werkzeugArray)
                    SupabaseDbClient.upsertTableRow(context, "agent_profiles", a)
                }
                if (openAiKey.isNotBlank()) {
                    keyVorhanden = true
                    openAiKey = ""
                }
                meldung = "Gespeichert. Gilt ab dem naechsten Gespraech."
            }.onFailure { meldung = "Konnte nicht speichern: ${it.message}" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "So arbeitet der Agent",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        if (laden) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        meldung?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        // ---------- Zugang ----------
        Abschnitt("Zugang zu ChatGPT") {
            OutlinedTextField(
                value = openAiKey,
                onValueChange = { openAiKey = it },
                label = {
                    Text(if (keyVorhanden) "OpenAI-Schluessel (hinterlegt, zum Aendern neu eingeben)"
                    else "OpenAI-Schluessel (sk-...)")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Der Schluessel wird verschluesselt in Supabase Vault gespeichert und nie an das Handy zurueckgegeben.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Modell", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = modell == "gpt-realtime",
                    onClick = { modell = "gpt-realtime" },
                    label = { Text("Beste Qualitaet") }
                )
                FilterChip(
                    selected = modell == "gpt-realtime-mini",
                    onClick = { modell = "gpt-realtime-mini" },
                    label = { Text("Guenstig und schnell") }
                )
            }
        }

        // ---------- Person ----------
        Abschnitt("Wer ist der Agent?") {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = rolle, onValueChange = { rolle = it },
                label = { Text("Rolle (z.B. Energieberatung)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = begruessung, onValueChange = { begruessung = it },
                label = { Text("Erster Satz im Gespraech") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = auftrag, onValueChange = { auftrag = it },
                label = { Text("Auftrag: wer er ist und was er tut") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
        }

        // ---------- Ziel ----------
        Abschnitt("Was soll er erreichen?") {
            OutlinedTextField(value = ziel, onValueChange = { ziel = it },
                label = { Text("Ziel des Gespraechs") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = erfolg, onValueChange = { erfolg = it },
                label = { Text("Erfolgreich ist das Gespraech, wenn ...") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = einwaende, onValueChange = { einwaende = it },
                label = { Text("Einwandbehandlung (eine Regel pro Zeile)") },
                minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = verboten, onValueChange = { verboten = it },
                label = { Text("Das darf er NIE sagen") },
                minLines = 3, modifier = Modifier.fillMaxWidth())
        }

        // ---------- Stimme ----------
        Abschnitt("Stimme und Sprechweise") {
            Text("Stimme", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            val stimmen = listOf(
                "marin" to "Marin (weiblich, warm)",
                "cedar" to "Cedar (maennlich, ruhig)",
                "alloy" to "Alloy (neutral)",
                "shimmer" to "Shimmer (weiblich, hell)",
                "echo" to "Echo (maennlich, sachlich)"
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stimmen.chunked(2).forEach { reihe ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        reihe.forEach { (id, label) ->
                            FilterChip(
                                selected = stimme == id,
                                onClick = { stimme = id },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
            Text("Sprechtempo: ${String.format("%.1f", tempo)}x",
                style = MaterialTheme.typography.labelMedium)
            Slider(value = tempo, onValueChange = { tempo = it }, valueRange = 0.7f..1.3f)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = anrede == "sie", onClick = { anrede = "sie" },
                    label = { Text("Sie-Form") })
                FilterChip(selected = anrede == "du", onClick = { anrede = "du" },
                    label = { Text("Du-Form") })
            }
            Schalter("Kurz antworten (1 bis 3 Saetze)", kurzeAntworten) { kurzeAntworten = it }
            Schalter("Sagt von sich aus, dass er eine KI ist", kiHinweis) { kiHinweis = it }
        }

        // ---------- Gespraechsfluss ----------
        Abschnitt("Gespraechsfluss") {
            Schalter("Kunde darf ihn unterbrechen", unterbrechbar) { unterbrechbar = it }
            Text(
                "Wartezeit bis er antwortet: ${pauseMs.toInt()} ms" +
                        if (pauseMs < 450) "  (sehr flott, faellt eher ins Wort)"
                        else if (pauseMs > 900) "  (laesst viel Zeit zum Ausreden)" else "",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(value = pauseMs, onValueChange = { pauseMs = it }, valueRange = 300f..1500f)

            Text(
                "Mikrofon-Empfindlichkeit: ${String.format("%.2f", empfindlichkeit)}" +
                        if (empfindlichkeit < 0.35f) "  (reagiert auch auf leise Stimmen und Nebengeraeusche)"
                        else if (empfindlichkeit > 0.7f) "  (nur deutliche Stimme)" else "",
                style = MaterialTheme.typography.labelMedium
            )
            Slider(value = empfindlichkeit, onValueChange = { empfindlichkeit = it }, valueRange = 0.2f..0.9f)

            Text(
                "Realtime antwortet direkt ohne zusaetzliche Denkstufe, damit das Gespraech fluessig bleibt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Gespraech spaetestens beenden nach ${maxMinuten.toInt()} Minuten",
                style = MaterialTheme.typography.labelMedium)
            Slider(value = maxMinuten, onValueChange = { maxMinuten = it }, valueRange = 2f..20f)

            OutlinedTextField(value = weiterleitung, onValueChange = { weiterleitung = it },
                label = { Text("Weiterleitung an (Telefonnummer)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        // ---------- Werkzeuge ----------
        Abschnitt("Was darf er selbst erledigen?") {
            val alle = listOf(
                "kontakt_notiz" to "Notizen festhalten",
                "interesse_markieren" to "Verbrauch und Vertragsende erfassen",
                "wiedervorlage_anlegen" to "Termine und Rueckrufe anlegen",
                "ergebnis_setzen" to "Gespraechsergebnis setzen",
                "weiterleiten" to "An einen Menschen weiterleiten",
                "gespraech_beenden" to "Gespraech selbst beenden"
            )
            alle.forEach { (id, label) ->
                Schalter(label, werkzeuge.contains(id)) { an ->
                    werkzeuge = if (an) werkzeuge + id else werkzeuge - id
                }
            }
        }

        Button(
            onClick = { speichern() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Speichern", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Abschnitt(titel: String, inhalt: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(titel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            inhalt()
        }
    }
}

@Composable
private fun Schalter(label: String, wert: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = wert, onCheckedChange = onChange)
    }
}
