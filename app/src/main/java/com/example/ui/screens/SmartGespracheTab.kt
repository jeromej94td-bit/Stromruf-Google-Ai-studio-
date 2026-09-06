package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.transcription.offline.LocalTranscripts
import java.io.File

/**
 * The independent Smart-Gespräche area. It deliberately has no SIP settings or
 * dial controls: the current Home SIP trunk remains the sole phone implementation.
 */
@Composable
fun SmartGespracheTab() {
    val context = LocalContext.current
    val recordings by produceState(initialValue = emptyList<File>(), context) {
        while (true) {
            val folder = File(context.filesDir, "smart_calls_recordings")
            value = folder.listFiles()
                ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            kotlinx.coroutines.delay(2_000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Smart Gespräche", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Aufnahmen des neuen SIP-Trunks werden hier lokal in Text, Notiz und nächsten Schritt verwandelt.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item { OfflineTranscriptionSetup() }
        item { LocalGemmaSetup() }
        item {
            Text("Gespeicherte Gespräche", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (recordings.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Noch keine SIP-Aufnahme vorhanden. Nach einem Gespräch erscheint sie automatisch hier.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(recordings, key = { it.name }) { recording ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(recording.nameWithoutExtension.removePrefix("Call_"), fontWeight = FontWeight.SemiBold)
                        OfflineRecordingTranscript(recording)
                    }
                }
            }
        }
    }
}
