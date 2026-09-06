package com.example.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.recording.RecordingStorageManager
import java.io.File
import kotlinx.coroutines.launch

/** One player for the entire list; leaving the screen releases the audio resource. */
class RecordingPlayback {
    var current by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    fun stop() {
        player?.release()
        player = null
        current = null
    }

    fun toggle(file: File) {
        if (current == file.name) { stop(); return }
        stop()
        error = null
        runCatching {
            val audio = MediaPlayer()
            player = audio
            current = file.name
            audio.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            audio.setDataSource(file.absolutePath)
            audio.setOnPreparedListener { if (player === it) it.start() }
            audio.setOnCompletionListener { if (player === it) stop() }
            audio.setOnErrorListener { failed, _, _ ->
                if (player === failed) { stop(); error = "Diese Aufnahme konnte nicht abgespielt werden." }
                true
            }
            audio.prepareAsync()
        }.onFailure { stop(); error = "Diese Aufnahme konnte nicht geöffnet werden." }
    }
}

@Composable
fun RecordingFolderSettings() {
    val context = LocalContext.current
    val storage = remember { RecordingStorageManager(context) }
    var destination by remember { mutableStateOf(storage.getStorageDisplayName()) }
    var autoExport by remember { mutableStateOf(storage.isAutoExportEnabled()) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (storage.setCustomFolderUri(uri)) {
                destination = storage.getStorageDisplayName()
                message = "Neue Aufnahmen werden zusätzlich in diesen Ordner exportiert."
            } else message = "Ordnerzugriff fehlgeschlagen. Bitte einen beschreibbaren Ordner wählen."
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aufnahmen & Speicherort", style = MaterialTheme.typography.titleMedium)
            Text(destination)
            OutlinedButton(onClick = { picker.launch(storage.getCustomFolderUri()) }) { Text("Speicherordner wählen") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = autoExport, onCheckedChange = {
                    autoExport = it; storage.setAutoExportEnabled(it)
                })
                Text("Neue Aufnahmen automatisch exportieren")
            }
            TextButton(onClick = {
                storage.resetToDefault(); destination = storage.getStorageDisplayName()
                message = "Aufnahmen bleiben im internen App-Speicher."
            }) { Text("Internen Speicher verwenden") }
            Text("Die lokale Aufnahme bleibt für Wiedergabe und Verarbeitung erhalten. Falls Google Drive in der Ordnerauswahl fehlt, nutze bei der Aufnahme „Teilen / Drive“.", style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun RecordingAudioControls(file: File, playback: RecordingPlayback) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { RecordingStorageManager(context) }
    var message by remember(file.name) { mutableStateOf<String?>(null) }
    var exporting by remember(file.name) { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { playback.toggle(file) }) {
            Text(if (playback.current == file.name) "Stoppen" else "Anhören")
        }
        TextButton(onClick = { storage.shareRecording(file) }) { Text("Teilen / Drive") }
    }
    TextButton(enabled = !exporting, onClick = {
        exporting = true
        scope.launch {
            val result = storage.saveFileToCustomFolder(file)
            message = if (result.isSuccess) "Aufnahme im gewählten Ordner gespeichert."
                else "Export fehlgeschlagen. Bitte Speicherordner und Schreibzugriff prüfen."
            exporting = false
        }
    }) { Text(if (exporting) "Export läuft …" else "In gewählten Ordner exportieren") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
