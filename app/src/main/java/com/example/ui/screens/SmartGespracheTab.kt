package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.agent.AgentBackend
import com.example.agent.SmartCallNote
import com.example.transcription.offline.LocalTranscripts
import com.example.util.SecureIntegrationSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sichtbares Smart-Calls-Cockpit: Gespräche, Groq, lokales Whisper und Gemma. */
@Composable
fun SmartGespracheTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SecureIntegrationSettings(context) }

    val recordings by produceState(initialValue = emptyList<File>(), context) {
        while (true) {
            val folder = File(context.filesDir, "smart_calls_recordings")
            value = folder.listFiles()
                ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            delay(2_000)
        }
    }

    var remoteNotes by remember { mutableStateOf<List<SmartCallNote>>(emptyList()) }
    var notesLoading by remember { mutableStateOf(true) }
    var groqKey by remember { mutableStateOf("") }
    var groqSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        groqKey = withContext(Dispatchers.IO) { settings.getGroqKey().orEmpty() }
        remoteNotes = withContext(Dispatchers.IO) { AgentBackend.fetchSmartCallNotes(context, 50) }
        notesLoading = false
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Smart Calls", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Aufnahme → Groq Whisper Large v3 → lokales Whisper-Fallback → Gemma → Zusammenfassung & Termin → Supabase",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Groq Whisper Large v3", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Haupttranskriptor für Smart Calls. Der Schlüssel wird verschlüsselt auf dem Gerät gespeichert.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = groqKey,
                        onValueChange = { groqKey = it; groqSaved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Groq API-Key") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            settings.saveGroqKey(groqKey)
                            groqSaved = true
                        }) { Text("API-Key speichern") }
                        if (groqKey.isNotBlank()) {
                            OutlinedButton(onClick = {
                                settings.clearGroqKey()
                                groqKey = ""
                                groqSaved = false
                            }) { Text("Entfernen") }
                        }
                    }
                    Text(
                        when {
                            groqSaved -> "✓ Groq-Key gespeichert"
                            groqKey.isNotBlank() -> "Groq ist konfiguriert"
                            else -> "Kein Groq-Key: die App nutzt automatisch lokales Whisper als Fallback"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item { OfflineTranscriptionSetup() }
        item { LocalGemmaSetup() }

        item {
            Text("Gespräche & Zusammenfassungen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        if (recordings.isEmpty() && remoteNotes.isEmpty() && !notesLoading) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Noch keine Smart-Call-Aufnahmen oder Zusammenfassungen vorhanden. Nach dem nächsten Smart-Anruf erscheinen sie automatisch hier.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(recordings, key = { "local-${it.name}" }) { recording ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(recording.nameWithoutExtension.removePrefix("Call_"), fontWeight = FontWeight.SemiBold)
                    Text(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(recording.lastModified())), style = MaterialTheme.typography.bodySmall)
                    OfflineRecordingTranscript(recording)
                }
            }
        }

        if (remoteNotes.isNotEmpty()) {
            item {
                Text("In Supabase gespeicherte Zusammenfassungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(remoteNotes, key = { "remote-${it.id}" }) { note ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(note.contactName ?: note.phone, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(note.callStartedAt))} · ${note.durationSeconds}s",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(note.summary, style = MaterialTheme.typography.bodyMedium)
                        Text("Supabase synchronisiert", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
