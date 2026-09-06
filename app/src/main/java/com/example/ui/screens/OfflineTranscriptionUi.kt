package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.transcription.offline.GemmaPromptSettings
import com.example.transcription.offline.LocalGemma
import com.example.transcription.offline.LocalTranscripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private fun copySmartCallText(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label kopiert", Toast.LENGTH_SHORT).show()
}

@Composable
fun OfflineTranscriptionSetup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Fallback-Modellstatus wird geladen …") }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { LocalTranscripts.resume(context) }
        while (true) {
            val snapshot = withContext(Dispatchers.IO) {
                LocalTranscripts.ready(context) to LocalTranscripts.modelStatus(context)
            }
            ready = snapshot.first
            status = snapshot.second
            delay(1500)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lokales Whisper · Fallback", style = MaterialTheme.typography.titleMedium)
            Text(
                "Haupttranskriptor ist Groq Whisper Large v3. Vorhandene und neue Smart-Call-WAVs werden automatisch zur Verarbeitung aufgenommen. Dieses lokale Modell übernimmt nur, wenn Groq oder Internet nicht verfügbar ist.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (ready) "Fallback ist bereit und kann offline übernehmen."
                else "Einmal etwa 60 MB herunterladen. Danach steht der Offline-Fallback ohne API-Kosten bereit."
            )
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (!ready) Button(onClick = {
                scope.launch(Dispatchers.IO) { LocalTranscripts.download(context) }
            }) { Text("60-MB-Fallback laden / fortsetzen") }
        }
    }
}

@Composable
fun LocalGemmaSetup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(LocalGemma.ready(context)) }
    var message by remember { mutableStateOf("") }
    var editRules by remember { mutableStateOf(false) }
    var documentationRules by remember { mutableStateOf(GemmaPromptSettings.documentation(context)) }
    var customerRules by remember { mutableStateOf(GemmaPromptSettings.customer(context)) }
    var followUpRules by remember { mutableStateOf(GemmaPromptSettings.followUp(context)) }

    fun reloadRules() {
        documentationRules = GemmaPromptSettings.documentation(context)
        customerRules = GemmaPromptSettings.customer(context)
        followUpRules = GemmaPromptSettings.followUp(context)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = "Gemma-Modell wird installiert …"
            val result = LocalGemma.install(context, uri)
            installed = result.isSuccess
            message = result.exceptionOrNull()?.message ?: "Gemma 3n E2B ist lokal bereit"
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Gemma 3n E2B · Dokumentation & nächster Schritt", style = MaterialTheme.typography.titleMedium)
            Text(
                if (installed) "Gemma ist installiert. Du kannst unten selbst festlegen, wie interne Notizen, Kundenfassung und Termine behandelt werden."
                else "Gemma kann Zusammenfassung, Kundenfassung und nächsten Schritt lokal formulieren. Ohne Gemma nutzt Stromruf die regelbasierte Auswertung.",
                style = MaterialTheme.typography.bodySmall
            )
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)

            if (!installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LocalGemma.MODEL_PAGE)))
                    }) { Text("Modellseite") }
                    Button(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                        Text("Gemma installieren")
                    }
                }
            }

            OutlinedButton(onClick = { editRules = !editRules }, modifier = Modifier.fillMaxWidth()) {
                Text(if (editRules) "Gemma-Regeln schließen" else "Gemma-Regeln bearbeiten")
            }

            if (editRules) {
                Text(
                    "Diese Regeln gelten für neue Gesprächsauswertungen. Damit bestimmst du selbst, welche Informationen Gemma dokumentiert und wann Termine entstehen sollen.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = documentationRules,
                    onValueChange = { documentationRules = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Interne Dokumentation") },
                    minLines = 4,
                    maxLines = 9
                )
                OutlinedTextField(
                    value = customerRules,
                    onValueChange = { customerRules = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Kundenfassung") },
                    minLines = 4,
                    maxLines = 9
                )
                OutlinedTextField(
                    value = followUpRules,
                    onValueChange = { followUpRules = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Termin- & Nachfassregeln") },
                    minLines = 4,
                    maxLines = 9
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        GemmaPromptSettings.save(context, documentationRules, customerRules, followUpRules)
                        reloadRules()
                        message = "Gemma-Regeln gespeichert – sie gelten ab der nächsten Auswertung"
                    }) { Text("Regeln speichern") }
                    OutlinedButton(onClick = {
                        GemmaPromptSettings.reset(context)
                        reloadRules()
                        message = "Gemma-Regeln auf Standard zurückgesetzt"
                    }) { Text("Standard") }
                }
            }
        }
    }
}

@Composable
fun OfflineRecordingTranscript(file: File) {
    if (!file.extension.equals("wav", true)) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember(file.name) { mutableStateOf(JSONObject()) }
    var showText by remember(file.name) { mutableStateOf(false) }
    var showCustomerText by remember(file.name) { mutableStateOf(false) }
    var localError by remember(file.name) { mutableStateOf<String?>(null) }

    LaunchedEffect(file.name) {
        while (true) {
            snapshot = withContext(Dispatchers.IO) { LocalTranscripts.read(context, file.name) }
            delay(1500)
        }
    }

    val state = snapshot.optString("state")
    val source = snapshot.optString("transcriptionSource")
    val analysis = snapshot.optString("analysisSource")
    val transcript = snapshot.optString("text")
    val customerText = snapshot.optString("customerText")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Telefon: " + com.example.recording.SmartRecordingMetadata.phone(snapshot, file.name), style = MaterialTheme.typography.bodySmall)
        Text("Kundennummer: " + snapshot.optString("customerNumber").ifBlank { "nicht eindeutig zugeordnet" }, style = MaterialTheme.typography.bodySmall)
        Text(localError ?: snapshot.optString("message", "Smart-Call-Verarbeitung"), style = MaterialTheme.typography.bodySmall)
        if (source.isNotBlank() && source != "pending") {
            Text(
                "Transkription: " + when (source) {
                    "groq-whisper-large-v3" -> "Groq Whisper Large v3"
                    "local-whisper-base-q5_1" -> "Lokales Whisper (Fallback)"
                    else -> source
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (analysis.isNotBlank()) {
            Text(
                "Analyse: " + when (analysis) {
                    "gemma-3n-e2b" -> "Gemma 3n E2B"
                    "regelbasiert" -> "Regelbasierter Fallback"
                    else -> analysis
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
        snapshot.optString("syncMessage").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        snapshot.optString("followUpMessage").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        snapshot.optString("summary").takeIf { it.isNotBlank() }?.let {
            Text("Zusammenfassung: $it", style = MaterialTheme.typography.bodySmall, maxLines = 8)
        }
        snapshot.optString("nextAction").takeIf { it.isNotBlank() }?.let {
            Text("Nächster Schritt: $it", style = MaterialTheme.typography.bodySmall)
        }

        if (state in setOf("", "error")) OutlinedButton(onClick = {
            scope.launch {
                localError = withContext(Dispatchers.IO) {
                    runCatching { LocalTranscripts.request(context, file) }.exceptionOrNull()?.message
                }
            }
        }) { Text(if (state == "error") "Erneut verarbeiten" else "Jetzt verarbeiten") }

        if (transcript.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { showText = true }) {
                    Text(if (state == "done") "Transkript öffnen" else "Bisherigen Text öffnen")
                }
                TextButton(onClick = { copySmartCallText(context, "Transkript", transcript) }) {
                    Text("Transkript kopieren")
                }
            }
        }

        if (customerText.isNotBlank()) {
            Text("Kundenfassung", style = MaterialTheme.typography.labelLarge)
            Text(customerText, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { showCustomerText = true }) { Text("Vollständig öffnen") }
                Button(onClick = { copySmartCallText(context, "Kundenfassung", customerText) }) {
                    Text("Für Kunden kopieren")
                }
            }
        }
    }

    if (showText) AlertDialog(
        onDismissRequest = { showText = false },
        title = { Text("Gesprächstranskript") },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                    Text(transcript)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { copySmartCallText(context, "Transkript", transcript) }) { Text("Kopieren") }
        },
        confirmButton = { TextButton(onClick = { showText = false }) { Text("Schließen") } }
    )

    if (showCustomerText) AlertDialog(
        onDismissRequest = { showCustomerText = false },
        title = { Text("Kundenfassung") },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                    Text(customerText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { copySmartCallText(context, "Kundenfassung", customerText) }) { Text("Kopieren") }
        },
        confirmButton = { TextButton(onClick = { showCustomerText = false }) { Text("Schließen") } }
    )
}
