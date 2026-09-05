package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.example.transcription.offline.LocalGemma
import com.example.transcription.offline.LocalTranscripts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

@Composable
fun OfflineTranscriptionSetup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Modellstatus wird geladen …") }
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
            Text("Kostenlos transkribieren · Deutsch", style = MaterialTheme.typography.titleMedium)
            Text(if (ready) "Bereit. Neue Gespräche über eine Minute werden nach dem Auflegen auf dem Handy transkribiert."
                else "Einmal etwa 190 MB herunterladen. Danach funktioniert die Transkription ohne Internet und ohne API-Gebühren.")
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (!ready) Button(onClick = {
                scope.launch(Dispatchers.IO) { LocalTranscripts.download(context) }
            }) { Text("Deutsch-Modell laden / fortsetzen") }
            Text("Längere Aufnahmen brauchen Zeit und Akku. Während eines Smart Calls pausiert die Verarbeitung. Die Zusammenfassung wird danach automatisch als Smart-Call-Notiz in Stromruf gespeichert; Audio und vollständiges Transkript bleiben auf dem Handy.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
fun LocalGemmaSetup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(LocalGemma.ready(context)) }
    var message by remember { mutableStateOf("") }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = "Gemma-Modell wird installiert …"
            val result = LocalGemma.install(context, uri)
            installed = result.isSuccess
            message = result.exceptionOrNull()?.message ?: "Gemma 3n E2B ist lokal bereit"
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lokale KI für Notiz und nächsten Schritt · optional", style = MaterialTheme.typography.titleMedium)
            Text(
                if (installed) "Gemma 3n E2B ist installiert. Nach der Transkription erstellt sie die Gesprächsnotiz direkt auf dem Handy."
                else "Gemma 3n E2B kann Zusammenfassung und nächsten Schritt zusätzlich lokal formulieren. Ohne sie nutzt die App weiterhin die sichere Regel-Logik für Termine.",
                style = MaterialTheme.typography.bodySmall
            )
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
            if (!installed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LocalGemma.MODEL_PAGE)))
                    }) { Text("Modellseite öffnen") }
                    Button(onClick = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                        Text("Heruntergeladenes Modell installieren")
                    }
                }
                Text(
                    "Einmal auf der Modellseite die Gemma-Lizenz akzeptieren, die .litertlm-Datei herunterladen und hier auswählen. Das Modell ist zu groß für die APK; Audio und Gesprächsinhalt bleiben dabei auf deinem Handy.",
                    style = MaterialTheme.typography.bodySmall
                )
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
    var localError by remember(file.name) { mutableStateOf<String?>(null) }
    LaunchedEffect(file.name) {
        while (true) {
            snapshot = withContext(Dispatchers.IO) { LocalTranscripts.read(context, file.name) }
            delay(1500)
        }
    }
    val state = snapshot.optString("state")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(localError ?: snapshot.optString("message", "Lokales Deutsch-Transkript"), style = MaterialTheme.typography.bodySmall)
        snapshot.optString("syncMessage").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        snapshot.optString("followUpMessage").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        snapshot.optString("summary").takeIf { it.isNotBlank() }?.let { Text("Notiz: $it", style = MaterialTheme.typography.bodySmall, maxLines = 6) }
        snapshot.optString("nextAction").takeIf { it.isNotBlank() }?.let { Text("Nächster Schritt: $it", style = MaterialTheme.typography.bodySmall) }
        if (state in setOf("", "error")) OutlinedButton(onClick = {
            scope.launch {
                localError = withContext(Dispatchers.IO) {
                    runCatching { LocalTranscripts.request(context, file) }.exceptionOrNull()?.message
                }
            }
        }) { Text(if (state == "error") "Lokal erneut versuchen" else "Kostenlos auf Deutsch transkribieren") }
        if (snapshot.optString("text").isNotBlank()) TextButton(onClick = { showText = true }) {
            Text(if (state == "done") "Deutsch-Transkript öffnen" else "Bisherigen Text öffnen")
        }
    }
    if (showText) AlertDialog(
        onDismissRequest = { showText = false },
        title = { Text("Gesprächstext · Deutsch") },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                    Text(snapshot.optString("text"))
                }
            }
        },
        confirmButton = { TextButton(onClick = { showText = false }) { Text("Schließen") } }
    )
}
