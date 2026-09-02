package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.util.FileTextExtractor
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agent.*
import com.example.ui.theme.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF0F172A)
private val Karte = Color(0xFF1E293B)
private val Gruen = Color(0xFF7CE58A)
private val Gelb = Color(0xFFFFC864)
private val Rot = Color(0xFFEF4444)

// ============================================================================
// Hauptscreen: Agenten | Live | KI-Assistent | Anrufe | Kampagnen | Wissen | Setup
// ============================================================================
@Composable
fun AgentCallScreen(
    modifier: Modifier = Modifier,
    viewModel: com.example.viewmodel.StromrufViewModel? = null
) {
    val tabs = listOf("Agenten", "Smart Calls", "Live", "KI-Assistent", "Anrufe", "Kampagnen", "Wissen", "Setup")
    var tab by remember { mutableStateOf(0) }
    val sessions by AgentRuntime.sessions.collectAsState()
    val aktive = sessions.count { it.status.collectAsState().value.aktiv }

    Column(modifier.fillMaxSize().background(Bg)) {
        ScrollableTabRow(
            selectedTabIndex = tab, containerColor = Karte,
            contentColor = ThemeAccent, edgePadding = 8.dp
        ) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t, fontSize = 13.sp)
                        if (i == 2 && aktive > 0) {
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.size(16.dp).background(Gruen, CircleShape),
                                contentAlignment = Alignment.Center) {
                                Text("$aktive", fontSize = 10.sp, color = Color.Black)
                            }
                        }
                    }
                })
            }
        }
        when (tab) {
            0 -> AgentenTab()
            1 -> SmartCallsTab()
            2 -> LiveTab()
            3 -> {
                if (viewModel != null) {
                    com.example.ui.AiAgentScreen(viewModel = viewModel)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("KI-Assistent wird initialisiert...", color = Color.White)
                    }
                }
            }
            4 -> AnrufeTab()
            5 -> KampagnenTab()
            6 -> WissenTab()
            7 -> EinrichtungTab()
        }
    }
}

// ============================================================================
// TAB 1: LIVE
// ============================================================================
@Composable
private fun LiveTab() {
    val sessions by AgentRuntime.sessions.collectAsState()
    val sipStatus by SipEngine.status.collectAsState()
    val aktive = sessions.filter { it.status.collectAsState().value.aktiv }
    val fertige = sessions.filter { !it.status.collectAsState().value.aktiv }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(
                    if (sipStatus == "Registriert") Gruen else Gelb, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("SIP-Trunk: $sipStatus", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { AgentRuntime.raeumeAuf() }) {
                    Icon(Icons.Default.ClearAll, null, tint = Color.Gray)
                }
            }
        }
        if (aktive.isEmpty() && fertige.isEmpty()) item {
            Text("Keine laufenden Gespräche.\nStarte im Tab \"Agenten\" einen " +
                 "Gerätetest oder Anruf – oder lass eine Kampagne laufen.",
                fontSize = 13.sp, color = Color.Gray,
                modifier = Modifier.padding(top = 24.dp))
        }
        items(aktive, key = { it.sessionId }) { LiveKarte(it) }
        if (fertige.isNotEmpty()) item {
            Text("GERADE BEENDET", fontSize = 10.sp, color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp))
        }
        items(fertige, key = { it.sessionId }) { LiveKarte(it) }
    }
}

@Composable
private fun LiveKarte(s: AgentSession) {
    val cfg by AgentRuntime.config.collectAsState()
    val status by s.status.collectAsState()
    val transcript by s.transcript.collectAsState()
    val fehler by s.fehler.collectAsState()
    val nachbearbeitet by s.nachbearbeitung.collectAsState()
    var dauer by remember { mutableStateOf(0L) }
    LaunchedEffect(s.sessionId, status.aktiv) {
        while (status.aktiv) {
            dauer = (System.currentTimeMillis() - s.startedAt) / 1000; delay(1000)
        }
    }
    val callCost = remember(dauer, cfg) {
        AgentCostCalculator.calculateCallCost(dauer.toInt(), cfg, s.direction)
    }
    val callCostFormatted = remember(callCost) {
        if (callCost < 0.001) "< 0,01 €" else String.format(Locale.GERMANY, "%.3f €", callCost)
    }

    Card(colors = CardDefaults.cardColors(containerColor = Karte),
        shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(when (status) {
                    SessionStatus.SPRICHT -> ThemeAccent
                    SessionStatus.HOERT_ZU -> Gruen
                    SessionStatus.DENKT -> Gelb
                    SessionStatus.FEHLER -> Rot
                    else -> Color.Gray
                }, CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${s.agent.name} · ${s.contactName ?: s.remoteNumber}",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    Text("${status.label} · ${dauer / 60}:${"%02d".format(dauer % 60)} · ~$callCostFormatted" +
                         if (s.campaignId != null) " · Kampagne" else "",
                        fontSize = 11.sp, color = Color.Gray)
                }
                if (status.aktiv) {
                    FilledIconButton(onClick = { AgentRuntime.beende(s) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rot)) {
                        Icon(Icons.Default.CallEnd, "Auflegen", tint = Color.White)
                    }
                }
            }
            fehler?.let { Text(it, fontSize = 11.sp, color = Rot,
                modifier = Modifier.padding(top = 4.dp)) }
            if (nachbearbeitet) Text("Agent pflegt gerade das CRM…",
                fontSize = 11.sp, color = ThemeAccent, modifier = Modifier.padding(top = 4.dp))
            transcript.takeLast(4).forEach { z ->
                Text((if (z.vomAgent) "🤖 " else "👤 ") + z.text,
                    fontSize = 12.sp, color = if (z.vomAgent) Color.LightGray else Color.White,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// ============================================================================
// TAB 2: AGENTEN (Verwaltung + Vorschau + Gerätetest + Anrufen)
// ============================================================================
@Composable
private fun AgentenTab() {
    var unterReiter by remember { mutableStateOf("profile") }

    Column(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            FilterChip(
                selected = unterReiter == "profile",
                onClick = { unterReiter = "profile" },
                label = { Text("Agenten-Profile") }
            )
            FilterChip(
                selected = unterReiter == "kosten",
                onClick = { unterReiter = "kosten" },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kosten & Tarife")
                    }
                }
            )
            FilterChip(
                selected = unterReiter == "training",
                onClick = { unterReiter = "training" },
                label = { Text("Trainingslabor") }
            )
            FilterChip(
                selected = unterReiter == "einstellungen",
                onClick = { unterReiter = "einstellungen" },
                label = { Text("So arbeitet der Agent") }
            )
        }

        when (unterReiter) {
            "kosten" -> AgentKostenScreen()
            "training" -> AgentTrainingScreen()
            "einstellungen" -> AgentEinstellungenScreen()
            else -> {
                AgentenProfilListe(onKostenKlick = { unterReiter = "kosten" })
            }
        }
    }
}

@Composable
private fun AgentenProfilListe(
    onKostenKlick: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val agents by AgentRuntime.agents.collectAsState()
    val geladen by AgentRuntime.geladen.collectAsState()
    val cfg by AgentRuntime.config.collectAsState()
    var editor by remember { mutableStateOf<AgentProfile?>(null) }
    var anrufDialog by remember { mutableStateOf<AgentProfile?>(null) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var knowledgeList by remember { mutableStateOf<List<KnowledgeEntry>>(emptyList()) }
    var statusText by remember { mutableStateOf<String?>(null) }

    fun refreshKnowledge() {
        scope.launch { knowledgeList = AgentBackend.fetchKnowledge(ctx) }
    }
    LaunchedEffect(Unit) { refreshKnowledge() }

    var targetAgentForFile by remember { mutableStateOf<String?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { u ->
            val agId = targetAgentForFile
            scope.launch(Dispatchers.IO) {
                val (fileName, textContent) = FileTextExtractor.extractText(ctx, u)
                if (textContent.isNotBlank()) {
                    val entry = KnowledgeEntry(
                        agentId = agId,
                        title = fileName,
                        sourceType = "datei",
                        content = textContent,
                        isActive = true
                    )
                    val ok = AgentBackend.upsertKnowledge(ctx, entry)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            statusText = "✓ '$fileName' (${textContent.length} Zeichen) als Quelle hinzugefügt"
                            refreshKnowledge()
                        } else {
                            statusText = "Fehler beim Speichern der Datei."
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText = "Konnte keinen Text aus '$fileName' lesen."
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!geladen) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = ThemeAccent) }
            statusText?.let { st ->
                item {
                    Text(st, fontSize = 12.sp, color = if (st.startsWith("✓")) Gruen else Rot,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            if (geladen && agents.isEmpty()) item {
                Text("Noch keine Agenten. Lege mit + deinen ersten Agenten an – " +
                     "z.B. \"Mia, Empfang\" oder \"Leo, Vertrieb\".",
                    fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 24.dp))
            }
            items(agents, key = { it.id }) { a ->
                val agentSources = knowledgeList.filter { it.agentId == a.id }
                val agentCost = remember(cfg, a.direction) {
                    AgentCostCalculator.calculateCost(cfg, a.direction)
                }
                Card(colors = CardDefaults.cardColors(containerColor = Karte),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.clickable { editor = a }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp, color = Color.White)
                                Text("${a.role} · ${richtungLabel(a.direction)} · " +
                                     "${Stimmen.liste.find { it.first == a.voiceId }?.second?.substringBefore(" –") ?: a.voiceId}" +
                                     " (${Stimmen.tempoLabel(a.voiceSpeed)}) · max. ${a.maxParallel} parallel",
                                    fontSize = 11.sp, color = Color.Gray)
                            }
                            Switch(checked = a.isActive, onCheckedChange = { an ->
                                AgentRuntime.speichereAgent(a.copy(isActive = an))
                            }, colors = SwitchDefaults.colors(checkedTrackColor = ThemeAccent))
                        }

                        // Kosten-Badge für diesen Agenten
                        Row(
                            Modifier
                                .padding(top = 4.dp)
                                .clickable { onKostenKlick() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                null,
                                tint = if (agentCost.isFreeTier) Gruen else ThemeAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (agentCost.isFreeTier) "Kosten: 0,00 € (Free Tier) · ~${String.format(Locale.GERMANY, "%.3f", agentCost.totalPerMin)} € / Min"
                                else "Kosten: ~${String.format(Locale.GERMANY, "%.3f", agentCost.totalPerMin)} € / Min (${agentCost.providerName})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (agentCost.isFreeTier) Gruen else ThemeAccent
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }

                        // Wissensquellen-Badge & Datei-Upload Button
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, tint = ThemeAccent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (agentSources.isNotEmpty()) "${agentSources.size} Wissensquelle(n)"
                                else "Keine eigenen Dateien als Quellen",
                                fontSize = 11.sp, color = if (agentSources.isNotEmpty()) ThemeAccent else Color.Gray
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(
                                onClick = {
                                    targetAgentForFile = a.id
                                    fileLauncher.launch(arrayOf("*/*"))
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, null, Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Datei als Quelle", fontSize = 11.sp)
                            }
                        }

                        Row(Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { AgentRuntime.starteGeraetetest(a) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(Icons.Default.Mic, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
                                Text("Gerätetest", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { anrufDialog = a },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(Icons.Default.Call, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
                                Text("Anrufen", fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { AgentRuntime.loescheAgent(a.id) }) {
                                Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
        FloatingActionButton(
            onClick = { editor = AgentProfile(sortOrder = agents.size) },
            containerColor = ThemeAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, "Neuer Agent", tint = Color.Black) }
    }

    editor?.let { a ->
        AgentEditorDialog(a,
            onSchliessen = { editor = null; refreshKnowledge() },
            onSpeichern = { neu ->
                AgentRuntime.speichereAgent(neu) { ok ->
                    meldung = if (ok) null else "Speichern fehlgeschlagen"
                }
                editor = null
                refreshKnowledge()
            }
        )
    }
    anrufDialog?.let { a ->
        var nummer by remember { mutableStateOf("") }
        var f by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { anrufDialog = null }, containerColor = Karte,
            title = { Text("${a.name} ruft an", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(nummer, { nummer = it },
                        label = { Text("Rufnummer") }, singleLine = true)
                    f?.let { Text(it, color = Rot, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AgentRuntime.rufeAn(a, nummer.trim()) { fehler ->
                        if (fehler == null) anrufDialog = null else f = fehler
                    }
                }, enabled = nummer.isNotBlank()) { Text("Anrufen", color = ThemeAccent) }
            },
            dismissButton = { TextButton({ anrufDialog = null }) { Text("Abbrechen", color = Color.Gray) } })
    }
}

private fun richtungLabel(d: String) = when (d) {
    "eingehend" -> "nur eingehend"; "ausgehend" -> "nur ausgehend"; else -> "ein- & ausgehend"
}

// ---------------- Agenten-Editor mit Stimmen-Vorschau ----------------
@Composable
private fun AgentEditorDialog(
    start: AgentProfile, onSchliessen: () -> Unit, onSpeichern: (AgentProfile) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by AgentRuntime.config.collectAsState()
    var a by remember { mutableStateOf(start) }
    var vorschauLaeuft by remember { mutableStateOf(false) }
    var agentSources by remember { mutableStateOf<List<KnowledgeEntry>>(emptyList()) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    fun ladeQuellen() {
        scope.launch {
            val alle = AgentBackend.fetchKnowledge(ctx)
            agentSources = alle.filter { it.agentId == a.id }
        }
    }
    LaunchedEffect(a.id) { ladeQuellen() }

    val editorFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { u ->
            scope.launch(Dispatchers.IO) {
                val (fileName, textContent) = FileTextExtractor.extractText(ctx, u)
                if (textContent.isNotBlank()) {
                    val entry = KnowledgeEntry(
                        agentId = a.id,
                        title = fileName,
                        sourceType = "datei",
                        content = textContent,
                        isActive = true
                    )
                    val ok = AgentBackend.upsertKnowledge(ctx, entry)
                    withContext(Dispatchers.Main) {
                        if (ok) {
                            statusMsg = "✓ Datei '$fileName' (${textContent.length} Zeichen) hinzugefügt"
                            ladeQuellen()
                        } else statusMsg = "Fehler beim Speichern der Datei."
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusMsg = "Kein Text in '$fileName' gefunden."
                    }
                }
            }
        }
    }

    // Offline-Vorschau: Android-Systemstimme (kostenlos, ohne API)
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) t?.language = Locale.GERMANY
        }
        tts = t
        onDispose { t?.stop(); t?.shutdown() }
    }

    Dialog(onDismissRequest = onSchliessen,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().background(Karte).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSchliessen) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                    Text(if (start.name.isBlank()) "Neuer Agent" else start.name,
                        fontWeight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.weight(1f))
                    Button(onClick = { onSpeichern(a) },
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeAccent),
                        shape = RoundedCornerShape(10.dp)) {
                        Text("Speichern", color = Color.Black)
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {

                    Abschnitt("Identität") {
                        Feld("Name", a.name) { a = a.copy(name = it) }
                        Feld("Rolle (z.B. Empfang, Vertrieb, Rückruf)", a.role) { a = a.copy(role = it) }
                        Text("Richtung", fontSize = 12.sp, color = Color.Gray)
                        ChipReihe(listOf("beide" to "Ein- & ausgehend",
                            "eingehend" to "Nur eingehend", "ausgehend" to "Nur ausgehend"),
                            a.direction) { a = a.copy(direction = it) }
                        ChipReihe(listOf("sie" to "Sie-Form", "du" to "Du-Form"),
                            a.formOfAddress) { a = a.copy(formOfAddress = it) }
                    }

                    Abschnitt("Stimme (Deutsch)") {
                        Stimmen.liste.forEach { (id, label) ->
                            Row(Modifier.fillMaxWidth().clickable { a = a.copy(voiceId = id) }
                                .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = a.voiceId == id,
                                    onClick = { a = a.copy(voiceId = id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = ThemeAccent))
                                Text(label, fontSize = 13.sp, color = Color.White)
                            }
                        }
                        Text("Tempo: ${Stimmen.tempoLabel(a.voiceSpeed)} " +
                             "(${"%.2f".format(a.voiceSpeed)}×)",
                            fontSize = 12.sp, color = Color.White)
                        Slider(a.voiceSpeed, { a = a.copy(voiceSpeed = it) },
                            valueRange = 0.5f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = ThemeAccent,
                                activeTrackColor = ThemeAccent))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                tts?.setSpeechRate(a.voiceSpeed)
                                tts?.speak(a.greeting, TextToSpeech.QUEUE_FLUSH, null, "vorschau")
                            }, shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.VolumeUp, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Vorschau (offline)", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = {
                                if (cfg.speechKey.isBlank() || vorschauLaeuft) return@OutlinedButton
                                vorschauLaeuft = true
                                scope.launch(Dispatchers.IO) {
                                    val f = File(ctx.cacheDir, "vorschau.wav")
                                    val ok = Tts.speak(cfg, a.voiceId, a.voiceSpeed, a.greeting, f)
                                    withContext(Dispatchers.Main) {
                                        if (ok != null) {
                                            val mp = MediaPlayer()
                                            mp.setDataSource(f.absolutePath)
                                            mp.setOnCompletionListener { it.release(); vorschauLaeuft = false }
                                            mp.prepare(); mp.start()
                                        } else vorschauLaeuft = false
                                    }
                                }
                            }, enabled = cfg.speechKey.isNotBlank() && !vorschauLaeuft,
                                shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.GraphicEq, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (vorschauLaeuft) "spielt…" else "Vorschau (ChatGPT)", fontSize = 12.sp)
                            }
                        }
                        Text("Offline = Android-Systemstimme (kostenlos, nur zum Testen von " +
                             "Text & Tempo). ChatGPT = echte Stimme, braucht den OpenAI-Schlüssel.",
                            fontSize = 10.sp, color = Color.Gray)
                    }

                    Abschnitt("Gespräch") {
                        Feld("Begrüßung", a.greeting, minLines = 2) { a = a.copy(greeting = it) }
                        Feld("Anweisungen (Prompt)", a.systemPrompt, minLines = 4) {
                            a = a.copy(systemPrompt = it)
                        }
                        Schalter("KI-Hinweis am Gesprächsanfang", a.aiDisclosure) {
                            a = a.copy(aiDisclosure = it)
                        }
                        Schalter("Kurze Antworten (1–3 Sätze)", a.shortAnswers) {
                            a = a.copy(shortAnswers = it)
                        }
                        Feld("Weiterleitung an Mitarbeiter (Rufnummer, optional)",
                            a.transferNumber) { a = a.copy(transferNumber = it) }
                    }

                    Abschnitt("Wissensquellen & Dateien (PDF, TXT, CSV...)") {
                        Schalter("Wissensdatenbank verwenden", a.useKnowledge) {
                            a = a.copy(useKnowledge = it)
                        }
                        if (a.useKnowledge) {
                            OutlinedButton(
                                onClick = { editorFileLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Datei als Quelle hochladen (PDF, TXT, CSV, DOCX, JSON...)", fontSize = 12.sp)
                            }

                            statusMsg?.let {
                                Text(it, fontSize = 11.sp, color = if (it.startsWith("✓")) Gruen else Rot, modifier = Modifier.padding(top = 4.dp))
                            }

                            if (agentSources.isNotEmpty()) {
                                Text("Angehängte Dateien & Quellen (${agentSources.size}):", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                                agentSources.forEach { k ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Karte, RoundedCornerShape(8.dp)).padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            when (k.sourceType) {
                                                "datei" -> Icons.Default.AttachFile
                                                "url" -> Icons.Default.Link
                                                else -> Icons.Default.Description
                                            }, null, tint = ThemeAccent, modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(k.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            Text("${k.content.length} Zeichen · ${if (k.sourceType == "datei") "Datei" else if (k.sourceType == "url") "Webseite" else "Text"}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                AgentBackend.deleteKnowledge(ctx, k.id)
                                                ladeQuellen()
                                            }
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            } else {
                                Text("Noch keine eigenen Dateien für diesen Agenten hochgeladen.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    Abschnitt("Kapazität & Timing") {
                        Text("Parallele Gespräche: ${a.maxParallel}", fontSize = 12.sp, color = Color.White)
                        Slider(a.maxParallel.toFloat(),
                            { a = a.copy(maxParallel = it.toInt().coerceIn(1, 10)) },
                            valueRange = 1f..10f, steps = 8,
                            colors = SliderDefaults.colors(thumbColor = ThemeAccent,
                                activeTrackColor = ThemeAccent))
                        Text("Hörfenster: ${a.listenWindowSec} s", fontSize = 12.sp, color = Color.White)
                        Slider(a.listenWindowSec.toFloat(),
                            { a = a.copy(listenWindowSec = it.toInt().coerceIn(3, 15)) },
                            valueRange = 3f..15f, steps = 11,
                            colors = SliderDefaults.colors(thumbColor = ThemeAccent,
                                activeTrackColor = ThemeAccent))
                        Text("Max. Gesprächsdauer: ${a.maxDurationMin} min",
                            fontSize = 12.sp, color = Color.White)
                        Slider(a.maxDurationMin.toFloat(),
                            { a = a.copy(maxDurationMin = it.toInt().coerceIn(2, 60)) },
                            valueRange = 2f..60f,
                            colors = SliderDefaults.colors(thumbColor = ThemeAccent,
                                activeTrackColor = ThemeAccent))
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

// ============================================================================
// TAB 3: ANRUFE (Verlauf, Aufnahme, Transkript, CRM-Aktionen, Löschung)
// ============================================================================
@Composable
private fun AnrufeTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<CallSessionRow>?>(null) }
    fun laden() { scope.launch { sessions = AgentBackend.fetchSessions(ctx) } }
    LaunchedEffect(Unit) { laden() }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anrufe werden nach ${AgentRuntime.config.value.retentionDays} Tagen " +
                     "automatisch gelöscht.", fontSize = 11.sp, color = Color.Gray,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { laden() }) {
                    Icon(Icons.Default.Refresh, null, tint = ThemeAccent)
                }
            }
        }
        val liste = sessions
        when {
            liste == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = ThemeAccent) }
            liste.isEmpty() -> item { Text("Noch keine Gespräche.", fontSize = 13.sp, color = Color.Gray) }
            else -> items(liste, key = { it.id }) { s ->
                VerlaufKarte(s, onGeloescht = { laden() })
            }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun VerlaufKarte(s: CallSessionRow, onGeloescht: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by AgentRuntime.config.collectAsState()
    var offen by remember { mutableStateOf(false) }
    var spielt by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    val df = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY) }
    val restTage = s.recordingExpiresAt?.let {
        ((it - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
    }
    val callCost = remember(s.durationSec, cfg) {
        AgentCostCalculator.calculateCallCost(s.durationSec, cfg, s.direction)
    }
    val costStr = remember(callCost) {
        if (callCost < 0.001) "< 0,01 €" else String.format(Locale.GERMANY, "%.3f €", callCost)
    }

    Card(colors = CardDefaults.cardColors(containerColor = Karte),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.clickable { offen = !offen }) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(when (s.direction) {
                    "ausgehend" -> Icons.Default.CallMade
                    "geraetetest" -> Icons.Default.Mic
                    else -> Icons.Default.CallReceived
                }, null, tint = ThemeAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("${s.agentName} · ${s.contactName ?: s.remoteNumber ?: "?"}",
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    Text("${df.format(Date(s.startedAt))} · ${s.durationSec / 60}:" +
                         "%02d".format(s.durationSec % 60) +
                         " · ~$costStr" +
                         (s.outcome?.let { " · $it" } ?: "") +
                         (s.sentiment?.let { " · Stimmung: $it" } ?: ""),
                        fontSize = 11.sp, color = Color.Gray)
                }
                Icon(if (offen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = Color.Gray)
            }
            if (offen) {
                s.summary?.let {
                    Text(it, fontSize = 12.sp, color = Color.LightGray,
                        modifier = Modifier.padding(top = 6.dp))
                }
                if (s.recordingPath != null) {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            if (spielt) { player?.release(); player = null; spielt = false }
                            else scope.launch {
                                val url = AgentBackend.signedRecordingUrl(ctx, s.recordingPath!!)
                                    ?: return@launch
                                val mp = MediaPlayer()
                                mp.setDataSource(url)
                                mp.setOnPreparedListener { it.start(); spielt = true }
                                mp.setOnCompletionListener { it.release(); player = null; spielt = false }
                                mp.prepareAsync(); player = mp
                            }
                        }, shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Icon(if (spielt) Icons.Default.Stop else Icons.Default.PlayArrow,
                                null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (spielt) "Stopp" else "Aufnahme", fontSize = 12.sp)
                        }
                        restTage?.let {
                            Spacer(Modifier.width(8.dp))
                            Text("wird in $it Tagen gelöscht", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
                if (s.transcript.isNotEmpty()) {
                    Text("TRANSKRIPT", fontSize = 10.sp, color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp))
                    s.transcript.forEach { (vomAgent, text) ->
                        Text((if (vomAgent) "🤖 " else "👤 ") + text,
                            fontSize = 12.sp,
                            color = if (vomAgent) Color.LightGray else Color.White,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp),
                    color = Color.White.copy(alpha = 0.08f))
                AktionenBlock(s.id)
                TextButton(onClick = {
                    scope.launch { AgentBackend.deleteSession(ctx, s); onGeloescht() }
                }) { Text("Jetzt löschen", fontSize = 12.sp, color = Rot) }
            }
        }
    }
}

@Composable
private fun AktionenBlock(sessionId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var aktionen by remember(sessionId) { mutableStateOf<List<AgentAction>?>(null) }
    var arbeitet by remember { mutableStateOf(false) }
    fun laden() { scope.launch { aktionen = AgentBackend.fetchActions(ctx, sessionId) } }
    LaunchedEffect(sessionId) { laden() }

    val liste = aktionen
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CRM-AKTIONEN DES AGENTEN", fontSize = 10.sp, color = Color.Gray,
                modifier = Modifier.weight(1f))
            if (liste != null && liste.isEmpty()) {
                TextButton(onClick = {
                    arbeitet = true
                    scope.launch {
                        AgentBackend.runPostCall(ctx, sessionId); laden(); arbeitet = false
                    }
                }, enabled = !arbeitet) {
                    Text(if (arbeitet) "läuft…" else "Nachbearbeiten",
                        fontSize = 11.sp, color = ThemeAccent)
                }
            }
        }
        when {
            liste == null -> Text("…", fontSize = 11.sp, color = Color.Gray)
            liste.isEmpty() -> Text("Keine Aktionen.", fontSize = 11.sp, color = Color.Gray)
            else -> {
                liste.forEach { a ->
                    AktionsZeile(a) { was ->
                        scope.launch {
                            when (was) {
                                "uebernehmen" -> AgentBackend.applyActions(ctx, listOf(a.id))
                                "verwerfen" -> AgentBackend.discardAction(ctx, a.id)
                            }
                            laden()
                        }
                    }
                }
                val offene = liste.filter { it.status == "vorgeschlagen" }
                if (offene.size > 1) {
                    TextButton(onClick = {
                        scope.launch {
                            AgentBackend.applyActions(ctx, offene.map { it.id }); laden()
                        }
                    }) { Text("Alle ${offene.size} übernehmen", fontSize = 12.sp, color = ThemeAccent) }
                }
            }
        }
    }
}

@Composable
private fun AktionsZeile(a: AgentAction, onAktion: (String) -> Unit) {
    val farbe = when (a.status) {
        "ausgefuehrt" -> Gruen; "vorgeschlagen" -> Gelb; "fehler" -> Rot; else -> Color.Gray
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(farbe, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(a.titel, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = Color.White, modifier = Modifier.weight(1f))
            if (a.status == "vorgeschlagen") {
                TextButton(onClick = { onAktion("uebernehmen") },
                    contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Übernehmen", fontSize = 11.sp, color = ThemeAccent)
                }
                TextButton(onClick = { onAktion("verwerfen") },
                    contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Nein", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
        if (a.arguments.isNotBlank())
            Text(a.arguments, fontSize = 11.sp, color = Color.Gray,
                modifier = Modifier.padding(start = 13.dp))
        a.error?.let { Text("Fehler: $it", fontSize = 11.sp, color = Rot,
            modifier = Modifier.padding(start = 13.dp)) }
    }
}

// ============================================================================
// TAB 4: KAMPAGNEN (Hotbox-Listen durch Agenten anrufen lassen)
// ============================================================================
@Composable
private fun KampagnenTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val campaigns by AgentRuntime.campaigns.collectAsState()
    val agents by AgentRuntime.agents.collectAsState()
    var neu by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AgentRuntime.ladeKampagnen() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Eine Kampagne lässt einen Agenten selbstständig eine Hotbox-Liste " +
                     "abtelefonieren – im Zeitfenster, mit Parallelität und max. Versuchen.",
                    fontSize = 12.sp, color = Color.Gray)
            }
            if (campaigns.isEmpty()) item {
                Text("Noch keine Kampagnen.", fontSize = 13.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 12.dp))
            }
            items(campaigns, key = { it.id }) { c ->
                val agent = agents.find { it.id == c.agentId }
                Card(colors = CardDefaults.cardColors(containerColor = Karte),
                    shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp, color = Color.White)
                                Text("${agent?.name ?: "?"} · " +
                                     (c.hotboxListName ?: "Alle Hotbox-Kontakte") +
                                     " · ${c.startHour}–${c.endHour} Uhr · " +
                                     "max. ${c.maxParallel} parallel",
                                    fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(c.status.uppercase(), fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (c.status) {
                                    "aktiv" -> Gruen; "fertig" -> Color.Gray; else -> Gelb
                                })
                        }
                        if (c.gesamt > 0) {
                            LinearProgressIndicator(
                                progress = { c.erledigt.toFloat() / c.gesamt },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = ThemeAccent, trackColor = Bg)
                            Text("${c.erledigt} erledigt · ${c.offen} offen · ${c.gesamt} gesamt",
                                fontSize = 11.sp, color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (c.status != "fertig") OutlinedButton(onClick = {
                                scope.launch {
                                    AgentBackend.setCampaignStatus(ctx, c.id,
                                        if (c.status == "aktiv") "pausiert" else "aktiv")
                                    AgentRuntime.ladeKampagnen()
                                }
                            }, shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Icon(if (c.status == "aktiv") Icons.Default.Pause
                                     else Icons.Default.PlayArrow, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (c.status == "aktiv") "Pausieren" else "Starten",
                                    fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                scope.launch {
                                    AgentBackend.deleteCampaign(ctx, c.id)
                                    AgentRuntime.ladeKampagnen()
                                }
                            }) { Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
        FloatingActionButton(onClick = { neu = true }, containerColor = ThemeAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Neue Kampagne", tint = Color.Black)
        }
    }
    if (neu) KampagneDialog(agents) { neu = false }
}

@Composable
private fun KampagneDialog(agents: List<AgentProfile>, onSchliessen: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var agentId by remember { mutableStateOf(agents.firstOrNull()?.id ?: "") }
    var liste by remember { mutableStateOf<String?>(null) }
    var listen by remember { mutableStateOf<List<String>>(emptyList()) }
    var stunden by remember { mutableStateOf(9f..18f) }
    var parallel by remember { mutableStateOf(1) }
    var versuche by remember { mutableStateOf(2) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var speichert by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { listen = AgentBackend.hotboxListNames(ctx) }

    AlertDialog(onDismissRequest = onSchliessen, containerColor = Karte,
        title = { Text("Neue Kampagne", color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Agent", fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp))
                ChipReihe(agents.map { it.id to it.name }, agentId) { agentId = it }
                Text("Hotbox-Liste", fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp))
                ChipReihe(listOf<Pair<String?, String>>(null to "Alle Hotbox-Kontakte") +
                        listen.map { it as String? to it }, liste) { liste = it }
                Text("Zeitfenster: ${stunden.start.toInt()}–${stunden.endInclusive.toInt()} Uhr",
                    fontSize = 12.sp, color = Color.White,
                    modifier = Modifier.padding(top = 8.dp))
                RangeSlider(stunden, { stunden = it }, valueRange = 7f..21f, steps = 13,
                    colors = SliderDefaults.colors(thumbColor = ThemeAccent,
                        activeTrackColor = ThemeAccent))
                Text("Parallel: $parallel · Versuche: $versuche",
                    fontSize = 12.sp, color = Color.White)
                Slider(parallel.toFloat(), { parallel = it.toInt().coerceIn(1, 5) },
                    valueRange = 1f..5f, steps = 3,
                    colors = SliderDefaults.colors(thumbColor = ThemeAccent,
                        activeTrackColor = ThemeAccent))
                Slider(versuche.toFloat(), { versuche = it.toInt().coerceIn(1, 5) },
                    valueRange = 1f..5f, steps = 3,
                    colors = SliderDefaults.colors(thumbColor = ThemeAccent,
                        activeTrackColor = ThemeAccent))
                fehler?.let { Text(it, color = Rot, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                speichert = true
                scope.launch {
                    val f = AgentBackend.createCampaign(ctx, Campaign(
                        name = name.trim(), agentId = agentId, hotboxListName = liste,
                        startHour = stunden.start.toInt(), endHour = stunden.endInclusive.toInt(),
                        maxParallel = parallel, maxAttempts = versuche))
                    speichert = false
                    if (f == null) { AgentRuntime.ladeKampagnen(); onSchliessen() } else fehler = f
                }
            }, enabled = name.isNotBlank() && agentId.isNotBlank() && !speichert) {
                Text(if (speichert) "…" else "Anlegen", color = ThemeAccent)
            }
        },
        dismissButton = { TextButton(onSchliessen) { Text("Abbrechen", color = Color.Gray) } })
}

// ============================================================================
// TAB 5: WISSEN (Quellen: Text oder URL-Import, global oder pro Agent)
// ============================================================================
@Composable
private fun WissenTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val agents by AgentRuntime.agents.collectAsState()
    var eintraege by remember { mutableStateOf<List<KnowledgeEntry>?>(null) }
    var editor by remember { mutableStateOf<KnowledgeEntry?>(null) }
    fun laden() { scope.launch { eintraege = AgentBackend.fetchKnowledge(ctx) } }
    LaunchedEffect(Unit) { laden() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Wissen fließt in die Gespräche ein: Preise, Tarife, FAQ, " +
                     "Gesprächsleitfäden. Global oder einem Agenten zugeordnet.",
                    fontSize = 12.sp, color = Color.Gray)
            }
            val liste = eintraege
            when {
                liste == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = ThemeAccent) }
                liste.isEmpty() -> item { Text("Noch keine Quellen.", fontSize = 13.sp, color = Color.Gray) }
                else -> items(liste, key = { it.id }) { k ->
                    Card(colors = CardDefaults.cardColors(containerColor = Karte),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.clickable { editor = k }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (k.sourceType) {
                                "url" -> Icons.Default.Link
                                "datei" -> Icons.Default.AttachFile
                                else -> Icons.Default.Description
                            }, null, tint = ThemeAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(k.title, fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp, color = Color.White)
                                Text((agents.find { it.id == k.agentId }?.name ?: "Alle Agenten") +
                                     " · ${if (k.sourceType == "datei") "Datei" else if (k.sourceType == "url") "Webseite" else "Text"}" +
                                     " · ${k.content.length} Zeichen" +
                                     if (!k.isActive) " · inaktiv" else "",
                                    fontSize = 11.sp, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                scope.launch { AgentBackend.deleteKnowledge(ctx, k.id); laden() }
                            }) { Icon(Icons.Default.DeleteOutline, null, tint = Color.Gray) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(70.dp)) }
        }
        FloatingActionButton(onClick = { editor = KnowledgeEntry() },
            containerColor = ThemeAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Add, "Neue Quelle", tint = Color.Black)
        }
    }
    editor?.let { k ->
        WissenDialog(k, agents, onSchliessen = { editor = null }, onGespeichert = {
            editor = null; laden()
        })
    }
}

@Composable
private fun WissenDialog(
    start: KnowledgeEntry, agents: List<AgentProfile>,
    onSchliessen: () -> Unit, onGespeichert: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var k by remember { mutableStateOf(start) }
    var laedt by remember { mutableStateOf(false) }
    var fehler by remember { mutableStateOf<String?>(null) }

    val wissenFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { u ->
            scope.launch(Dispatchers.IO) {
                val (fileName, textContent) = FileTextExtractor.extractText(ctx, u)
                withContext(Dispatchers.Main) {
                    if (textContent.isNotBlank()) {
                        k = k.copy(
                            title = if (k.title.isBlank()) fileName else k.title,
                            sourceType = "datei",
                            content = textContent
                        )
                    } else {
                        fehler = "Kein Text in '$fileName' gefunden."
                    }
                }
            }
        }
    }

    AlertDialog(onDismissRequest = onSchliessen, containerColor = Karte,
        title = { Text(if (start.title.isBlank()) "Neue Quelle" else "Quelle bearbeiten",
            color = Color.White) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(k.title, { k = k.copy(title = it) },
                    label = { Text("Titel (z.B. Preisliste 2026)") }, singleLine = true)
                ChipReihe(listOf("text" to "Text", "url" to "Webseite (URL)", "datei" to "Datei (PDF, TXT...)"),
                    k.sourceType) { k = k.copy(sourceType = it) }
                if (k.sourceType == "url") {
                    OutlinedTextField(k.sourceUrl, { k = k.copy(sourceUrl = it) },
                        label = { Text("https://…") }, singleLine = true)
                    OutlinedButton(onClick = {
                        laedt = true; fehler = null
                        scope.launch(Dispatchers.IO) {
                            val text = runCatching { holeUrlText(k.sourceUrl) }.getOrNull()
                            withContext(Dispatchers.Main) {
                                laedt = false
                                if (text.isNullOrBlank()) fehler = "Seite konnte nicht geladen werden."
                                else k = k.copy(content = text.take(8000))
                            }
                        }
                    }, enabled = k.sourceUrl.startsWith("http") && !laedt,
                        shape = RoundedCornerShape(10.dp)) {
                        Text(if (laedt) "lädt…" else "Inhalt von der Seite laden", fontSize = 12.sp)
                    }
                }
                if (k.sourceType == "datei") {
                    OutlinedButton(
                        onClick = { wissenFileLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Datei auswählen (PDF, TXT, CSV, DOCX, JSON...)", fontSize = 12.sp)
                    }
                }
                OutlinedTextField(k.content, { k = k.copy(content = it) },
                    label = { Text("Inhalt") }, minLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Text("Gilt für", fontSize = 12.sp, color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp))
                ChipReihe(listOf<Pair<String?, String>>(null to "Alle Agenten") +
                        agents.map { it.id as String? to it.name },
                    k.agentId) { k = k.copy(agentId = it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(k.isActive, { k = k.copy(isActive = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = ThemeAccent))
                    Text("Aktiv", fontSize = 13.sp, color = Color.White)
                }
                fehler?.let { Text(it, color = Rot, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (AgentBackend.upsertKnowledge(ctx, k)) onGespeichert()
                    else fehler = "Speichern fehlgeschlagen."
                }
            }, enabled = k.title.isNotBlank() && k.content.isNotBlank()) {
                Text("Speichern", color = ThemeAccent)
            }
        },
        dismissButton = { TextButton(onSchliessen) { Text("Abbrechen", color = Color.Gray) } })
}

/** Webseite laden und grob in Text verwandeln (HTML-Tags entfernen). */
private fun holeUrlText(url: String): String {
    val client = OkHttpClient()
    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) return ""
        val html = r.body?.string() ?: return ""
        return html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&auml;", "ä").replace("&ouml;", "ö").replace("&uuml;", "ü")
            .replace("&Auml;", "Ä").replace("&Ouml;", "Ö").replace("&Uuml;", "Ü")
            .replace("&szlig;", "ß").replace("&euro;", "€")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n").trim()
    }
}

// ============================================================================
// TAB 6: EINRICHTUNG (SIP-Trunk, ChatGPT, Routing, Aufbewahrung, Rechte)
// ============================================================================
@Composable
private fun EinrichtungTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val agents by AgentRuntime.agents.collectAsState()
    val sipStatus by SipEngine.status.collectAsState()
    var c by remember { mutableStateOf(AgentRuntime.config.value) }
    var policy by remember { mutableStateOf(ToolPolicy()) }
    var meldung by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        AgentRuntime.aktualisieren { c = AgentRuntime.config.value }
        policy = AgentBackend.fetchPolicy(ctx)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {

        Abschnitt("Dein SIP-Trunk (eigener Anbieter = eigene Kosten)") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(
                    if (sipStatus == "Registriert") Gruen else Gelb, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(sipStatus, fontSize = 12.sp, color = Color.Gray)
            }
            Feld("Anzeigename", c.sipDisplayName) { c = c.copy(sipDisplayName = it) }
            Feld("SIP-Benutzer", c.sipUser) { c = c.copy(sipUser = it) }
            Feld("SIP-Passwort", c.sipPassword, passwort = true) { c = c.copy(sipPassword = it) }
            Feld("Domain (z.B. sip.anbieter.de)", c.sipDomain) { c = c.copy(sipDomain = it) }
            Feld("Proxy (optional)", c.sipProxy) { c = c.copy(sipProxy = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(c.sipPort.toString(),
                    { c = c.copy(sipPort = it.toIntOrNull() ?: 5060) },
                    label = { Text("Port", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f), singleLine = true)
                Box(Modifier.weight(2f)) {
                    Column {
                        Text("Transport", fontSize = 11.sp, color = Color.Gray)
                        ChipReihe(listOf("UDP" to "UDP", "TCP" to "TCP", "TLS" to "TLS"),
                            c.sipTransport) { c = c.copy(sipTransport = it) }
                    }
                }
            }
            Text("Gleichzeitige Leitungen: ${c.sipMaxLines}", fontSize = 12.sp, color = Color.White)
            Slider(c.sipMaxLines.toFloat(), { c = c.copy(sipMaxLines = it.toInt().coerceIn(1, 10)) },
                valueRange = 1f..10f, steps = 8,
                colors = SliderDefaults.colors(thumbColor = ThemeAccent, activeTrackColor = ThemeAccent))
        }

        Abschnitt("KI-Engine & Sprachmodelle (Verstehen, Denken, Antworten)") {
            Text("KI-Anbieter", fontSize = 12.sp, color = Color.Gray)
            ChipReihe(listOf("gemini" to "Google Gemini (Empfohlen)", "openai" to "ChatGPT / OpenAI", "anthropic" to "Claude"),
                c.llmProvider) { p ->
                c = when (p) {
                    "gemini" -> c.copy(llmProvider = "gemini", llmModel = "gemini-3.5-flash")
                    "openai" -> c.copy(llmProvider = "openai", llmBaseUrl = "https://api.openai.com/v1", llmModel = "gpt-4o-mini")
                    else -> c.copy(llmProvider = "anthropic", llmBaseUrl = "https://api.anthropic.com", llmModel = "claude-sonnet-4-6")
                }
            }

            if (c.llmProvider == "gemini") {
                Feld("Google Gemini API-Schlüssel (AIzaSy...)", c.geminiApiKey, passwort = true) {
                    c = c.copy(geminiApiKey = it)
                }
                Text("Gemini Sprachmodell", fontSize = 12.sp, color = Color.Gray)
                ChipReihe(listOf(
                    "gemini-3.5-flash" to "3.5 Flash (Empfohlen)",
                    "gemini-3.1-flash-lite-preview" to "3.1 Flash-Lite",
                    "gemini-3.1-pro-preview" to "3.1 Pro (Denken)"
                ), c.llmModel) { c = c.copy(llmModel = it) }
            } else if (c.llmProvider == "openai") {
                Feld("OpenAI API-Schlüssel (Whisper + Chat + Stimmen)", c.openaiApiKey, passwort = true) {
                    c = c.copy(openaiApiKey = it)
                }
                Feld("Modell", c.llmModel) { c = c.copy(llmModel = it) }
            } else {
                Feld("Anthropic API-Schlüssel", c.llmApiKey, passwort = true) {
                    c = c.copy(llmApiKey = it)
                }
                Feld("Modell", c.llmModel) { c = c.copy(llmModel = it) }
            }

            if (c.llmProvider != "openai") {
                Feld("Optional: OpenAI API-Schlüssel für TTS / Whisper Stimmen", c.openaiApiKey, passwort = true) {
                    c = c.copy(openaiApiKey = it)
                }
            }
        }

        Abschnitt("Eingehende Anrufe (eine Nummer, mehrere Agenten)") {
            ChipReihe(listOf("round_robin" to "Abwechselnd",
                "geringste_auslastung" to "Freiester Agent",
                "fester_agent" to "Fester Agent"),
                c.routingStrategy) { c = c.copy(routingStrategy = it) }
            if (c.routingStrategy == "fester_agent")
                ChipReihe(agents.map { it.id as String? to it.name }, c.fixedAgentId) {
                    c = c.copy(fixedAgentId = it)
                }
            Feld("KI-Hinweis (wird am Gesprächsanfang gesagt)", c.aiDisclosureText,
                minLines = 2) { c = c.copy(aiDisclosureText = it) }
        }

        Abschnitt("Aufzeichnung & Löschung") {
            Schalter("Gespräche aufzeichnen", c.recordingEnabled) {
                c = c.copy(recordingEnabled = it)
            }
            Text("Automatisch löschen nach: ${c.retentionDays} Tagen " +
                 if (c.retentionDays == 7) "(Standard)" else "",
                fontSize = 12.sp, color = Color.White)
            Slider(c.retentionDays.toFloat(),
                { c = c.copy(retentionDays = it.toInt().coerceIn(1, 30)) },
                valueRange = 1f..30f, steps = 28,
                colors = SliderDefaults.colors(thumbColor = ThemeAccent, activeTrackColor = ThemeAccent))
            Text("Aufnahme UND Transkript werden nachts serverseitig gelöscht. " +
                 "Der Anrufeintrag in der Aktivität bleibt.", fontSize = 10.sp, color = Color.Gray)
        }

        Abschnitt("Was der Agent nach dem Gespräch im CRM tun darf") {
            Schalter("Aktionen sofort ausführen", policy.autoApply) {
                policy = policy.copy(autoApply = it)
            }
            Text(if (policy.autoApply) "Der Agent ändert die Daten direkt."
                 else "Der Agent schlägt vor, du gibst im Tab Anrufe frei.",
                fontSize = 11.sp, color = Color.Gray)
            AlleWerkzeuge.liste.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = policy.allowedTools.contains(key),
                        onCheckedChange = { an ->
                            policy = policy.copy(allowedTools =
                                if (an) policy.allowedTools + key else policy.allowedTools - key)
                        },
                        colors = CheckboxDefaults.colors(checkedColor = ThemeAccent))
                    Text(label, fontSize = 13.sp, color = Color.White)
                }
            }
            Text("Max. Aktionen pro Gespräch: ${policy.maxActions}",
                fontSize = 12.sp, color = Color.White)
            Slider(policy.maxActions.toFloat(),
                { policy = policy.copy(maxActions = it.toInt().coerceIn(1, 20)) },
                valueRange = 1f..20f, steps = 18,
                colors = SliderDefaults.colors(thumbColor = ThemeAccent, activeTrackColor = ThemeAccent))
            Feld("Eigene Regeln für die Nachbearbeitung", policy.extraPrompt, minLines = 2) {
                policy = policy.copy(extraPrompt = it)
            }
        }

        Button(onClick = {
            AgentRuntime.speichereKonfiguration(c) { ok1 ->
                scope.launch {
                    val ok2 = AgentBackend.savePolicy(ctx, policy)
                    meldung = if (ok1 && ok2) "Gespeichert. SIP-Trunk registriert sich neu…"
                              else "Fehler beim Speichern."
                }
            }
        }, colors = ButtonDefaults.buttonColors(containerColor = ThemeAccent),
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Alles speichern", color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
        meldung?.let {
            Text(it, fontSize = 12.sp,
                color = if (it.startsWith("Fehler")) Rot else Gruen,
                modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ============================================================================
// Kleine Bausteine
// ============================================================================
@Composable
private fun Abschnitt(titel: String, inhalt: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Karte),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titel.uppercase(), fontSize = 10.sp, color = ThemeAccent,
                fontWeight = FontWeight.Bold)
            inhalt()
        }
    }
}

@Composable
private fun Feld(label: String, wert: String, minLines: Int = 1,
                 passwort: Boolean = false, onWert: (String) -> Unit) {
    OutlinedTextField(wert, onWert, label = { Text(label, fontSize = 12.sp) },
        minLines = minLines, singleLine = minLines == 1 && !passwort,
        visualTransformation = if (passwort)
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Schalter(label: String, wert: Boolean, onWert: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
        Switch(wert, onWert, colors = SwitchDefaults.colors(checkedTrackColor = ThemeAccent))
    }
}

@Composable
private fun <T> ChipReihe(optionen: List<Pair<T, String>>, wert: T, onWahl: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        optionen.forEach { (key, label) ->
            FilterChip(selected = wert == key, onClick = { onWahl(key) },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ThemeAccent,
                    selectedLabelColor = Color.Black))
        }
    }
}
