package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            Text("Längere Aufnahmen brauchen Zeit und Akku. Während eines Smart Calls pausiert die Verarbeitung. Zusammenfassungen und Termine sind ein späterer Schritt.",
                style = MaterialTheme.typography.bodySmall)
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
