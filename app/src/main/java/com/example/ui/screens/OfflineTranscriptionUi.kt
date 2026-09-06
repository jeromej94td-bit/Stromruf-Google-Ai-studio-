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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.transcription.offline.LocalGemma
import com.example.transcription.offline.LocalTranscripts
import com.example.util.SecureIntegrationSettings
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
    val secure = remember { SecureIntegrationSettings(context) }
    var groqKey by remember { mutableStateOf(secure.getGroqKey().orEmpty()) }
    var groqSaved by remember { mutableStateOf(secure.getGroqKey()?.isNotBlank() == true) }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Haupttranskription · Groq Whisper Large v3", style = MaterialTheme.typography.titleMedium)
            Text(
                "Nach dem Auflegen wird das Gespräch automatisch an Groq Whisper Large v3 gesendet. Deutsch ist fest eingestellt; danach erstellt Stromruf wie bisher Notiz, Supabase-Eintrag und ggf. Wiedervorlage.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = groqKey,
                onValueChange = { groqKey = it; groqSaved = false },
                label = { Text("Groq API-Key") },
                placeholder = { Text("gsk_…") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    secure.saveGroqKey(groqKey)
                    groqSaved = groqKey.isNotBlank()
                }, enabled = groqKey.isNotBlank()) {
                    Text(if (groqSaved) "Groq-Key gespeichert" else "Groq-Key speichern")
                }
                if (groqSaved) {
                    OutlinedButton(onClick = {
                        secure.clearGroqKey()
                        groqKey = ""
                        groqSaved = false
                    }) { Text("Entfernen") }
                }
            }
            Text(
                if (groqSaved) "Aktiv: Groq Whisper Large v3 ist der automatische Haupttranskriptor."
                else "Ohne Groq-Key verwendet Stromruf automatisch den lokalen Whisper-Small-Fallback.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()
            Text("Offline-Fallback · Whisper Small", style = MaterialTheme.typography.titleSmall)
            Text(
                if (ready) "Fallback ist bereit. Wenn Groq oder Internet ausfällt, kann die Aufnahme lokal transkribiert werden."
                else "Optional einmal etwa 190 MB laden. Das Modell wird nur als Ausfallsicherung benötigt.",
                style = MaterialTheme.typography.bodySmall
            )
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (!ready) Button(onClick = {
                scope.launch(Dispatchers.IO) { LocalTranscripts.download(context) }
            }) { Text("Offline-Fallback laden / fortsetzen") }
        }
    }
}

@Composable
fun LocalGemmaSetup() { val context = LocalContext.current; val scope = rememberCoroutineScope(); var installed by remember { mutableStateOf(LocalGemma.ready(context)) }; var message by remember { mutableStateOf("") }; val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) scope.launch { message = "Gemma-Modell wird installiert …"; val result = LocalGemma.install(context, uri); installed = result.isSuccess; message = result.exceptionOrNull()?.message ?: "Gemma 3n E2B ist lokal bereit" } }; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Lokale KI für Notiz und nächsten Schritt · optional", style = MaterialTheme.typography.titleMedium); Text(if (installed) "Gemma 3n E2B ist installiert. Nach der Transkription erstellt sie die Gesprächsnotiz direkt auf dem Handy." else "Gemma 3n E2B kann Zusammenfassung und nächsten Schritt zusätzlich lokal formulieren. Ohne sie nutzt die App weiterhin die sichere Regel-Logik für Termine.", style = MaterialTheme.typography.bodySmall); if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall); if (!installed) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LocalGemma.MODEL_PAGE))) }) { Text("Modellseite öffnen") }; Button(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) { Text("Heruntergeladenes Modell installieren") } }; Text("Einmal auf der Modellseite die Gemma-Lizenz akzeptieren, die .litertlm-Datei herunterladen und hier auswählen. Das Modell ist zu groß für die APK; Audio und Gesprächsinhalt bleiben dabei auf deinem Handy.", style = MaterialTheme.typography.bodySmall) } } }
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
        Text(localError ?: snapshot.optString("message", "Groq Whisper Large v3"), style = MaterialTheme.typography.bodySmall)
        snapshot.optString("transcriptionSource").takeIf { it.isNotBlank() && it != "pending" }?.let {
            Text("Quelle: $it", style = MaterialTheme.typography.labelSmall)
        }
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
        }) { Text(if (state == "error") "Erneut transkribieren" else "Mit Groq Whisper Large v3 transkribieren") }
        if (snapshot.optString("text").isNotBlank()) TextButton(onClick = { showText = true }) {
            Text(if (state == "done") "Transkript öffnen" else "Bisherigen Text öffnen")
        }
    }
    if (showText) AlertDialog(
        onDismissRequest = { showText = false },
        title = { Text("Gesprächstext") },
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
