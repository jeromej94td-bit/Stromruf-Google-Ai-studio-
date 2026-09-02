package com.example.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.recording.RecordingStorageManager
import com.example.agent.AgentBackend
import com.example.agent.SmartCallNote
import com.example.sip.NativeSipClient
import com.example.sip.SipAccountConfig
import com.example.sip.SipState
import com.example.sip.SipTransportProtocol
import com.example.transcription.GeminiAudioTranscriber
import com.example.transcription.TranscriptionCache
import com.example.transcription.TranscriptionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCallsTab() {
    val ctx = LocalContext.current

    // Secure local preferences storage
    val prefs = remember {
        try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                "smart_calls_prefs_v2",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            ctx.getSharedPreferences("smart_calls_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    // State Variables
    var displayName by remember { mutableStateOf(prefs.getString("displayName", "") ?: "") }
    var sipUser by remember { mutableStateOf(prefs.getString("sipUser", "") ?: "") }
    var authUser by remember { mutableStateOf(prefs.getString("authUser", "") ?: "") }
    var sipPassword by remember { mutableStateOf(prefs.getString("sipPassword", "") ?: "") }
    var sipRegistrar by remember { mutableStateOf(prefs.getString("sipRegistrar", "voip.easybell.de") ?: "voip.easybell.de") }
    var selectedProtocol by remember {
        val protoStr = prefs.getString("protocol", SipTransportProtocol.TLS.name)
        mutableStateOf(SipTransportProtocol.entries.find { it.name == protoStr } ?: SipTransportProtocol.TLS)
    }
    var sipPort by remember {
        mutableStateOf(prefs.getInt("port", selectedProtocol.defaultPort).toString())
    }

    var showPassword by remember { mutableStateOf(false) }
    var targetNumber by remember { mutableStateOf("") }
    var showDialpad by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }

    // Native SIP Client instance
    val sipClient = remember { NativeSipClient(ctx) }
    val sipState by sipClient.state.collectAsState()
    val statusText by sipClient.statusText.collectAsState()
    val callDuration by sipClient.callDurationSeconds.collectAsState()
    val isRecording by sipClient.isRecording.collectAsState()
    val lastRecordingFile by sipClient.lastRecordingFile.collectAsState()

    LaunchedEffect(isMuted) {
        sipClient.setMuted(isMuted)
    }

    LaunchedEffect(isSpeakerOn) {
        sipClient.setSpeakerphoneOn(isSpeakerOn)
    }

    // Recordings list state
    var recordingsList by remember { mutableStateOf<List<File>>(emptyList()) }
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Storage Destination & Cloud Sync (Google Drive / Local SAF)
    val storageManager = remember { RecordingStorageManager(ctx) }
    var storageDisplayName by remember { mutableStateOf(storageManager.getStorageDisplayName()) }
    var isGoogleDrive by remember { mutableStateOf(storageManager.isGoogleDrive()) }
    var autoExportEnabled by remember { mutableStateOf(storageManager.isAutoExportEnabled()) }
    var isExportingAll by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Summary-only Smart Calls memory in the Stromruf Supabase project
    var smartCallNotes by remember { mutableStateOf<List<SmartCallNote>>(emptyList()) }
    var isLoadingSmartCallNotes by remember { mutableStateOf(false) }
    var recordingDurations by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    // Gemini Audio Transcription Engine & Cache
    val transcriber = remember { GeminiAudioTranscriber(ctx) }
    val transcriptCache = remember { TranscriptionCache(ctx) }
    var cachedTranscripts by remember { mutableStateOf<Map<String, TranscriptionResult>>(emptyMap()) }
    var transcribingFiles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeTranscriptResult by remember { mutableStateOf<TranscriptionResult?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var pendingFileToTranscribe by remember { mutableStateOf<File?>(null) }

    fun reloadSmartCallNotes() {
        coroutineScope.launch {
            isLoadingSmartCallNotes = true
            try {
                smartCallNotes = AgentBackend.fetchSmartCallNotes(ctx, limit = 50)
            } finally {
                isLoadingSmartCallNotes = false
            }
        }
    }

    fun phoneForRecording(file: File): String {
        val fromName = file.nameWithoutExtension
            .removePrefix("Call_")
            .substringBeforeLast("_")
            .trim()
        return fromName
            .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            ?: targetNumber.trim().ifBlank { "Unbekannt" }
    }

    fun syncCachedNotesToSupabase() {
        val filesByName = recordingsList.associateBy { it.name }
        val cached = cachedTranscripts.values.toList()
        if (cached.isEmpty()) return

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                cached.forEach { result ->
                    val file = filesByName[result.fileName] ?: return@forEach
                    val durationSeconds = maxOf(
                        result.estimatedDurationSeconds,
                        transcriber.estimateDurationSeconds(
                            file,
                            recordingDurations[file.name] ?: 0L
                        )
                    )
                    if (durationSeconds > 60L) {
                        AgentBackend.saveSmartCallNote(
                            context = ctx,
                            phone = phoneForRecording(file),
                            contactId = null,
                            contactName = null,
                            callStartedAt = file.lastModified(),
                            durationSeconds = durationSeconds,
                            summary = result.summary,
                            sourceFileName = file.name
                        )
                    }
                }
            }
            reloadSmartCallNotes()
        }
    }

    fun reloadTranscripts() {
        cachedTranscripts = transcriptCache.getAll().associateBy { it.fileName }
    }

    fun startTranscription(file: File) {
        if (!transcriber.hasApiKey()) {
            pendingFileToTranscribe = file
            showApiKeyDialog = true
            return
        }

        coroutineScope.launch {
            transcribingFiles = transcribingFiles + file.name
            val res = transcriber.transcribeAndSummarize(
                file,
                fallbackDurationSeconds = recordingDurations[file.name] ?: 0L
            )
            transcribingFiles = transcribingFiles - file.name

            if (res.isSuccess) {
                val result = res.getOrNull()
                reloadTranscripts()
                if (result != null) {
                    activeTranscriptResult = result

                    val durationSeconds = maxOf(
                        result.estimatedDurationSeconds,
                        recordingDurations[file.name] ?: 0L
                    )
                    val remoteMessage = if (durationSeconds > 60L) {
                        val saved = AgentBackend.saveSmartCallNote(
                            context = ctx,
                            phone = phoneForRecording(file),
                            contactId = null,
                            contactName = null,
                            callStartedAt = file.lastModified(),
                            durationSeconds = durationSeconds,
                            summary = result.summary,
                            sourceFileName = file.name
                        )
                        if (saved) {
                            reloadSmartCallNotes()
                            "Zusammenfassung in Supabase gespeichert."
                        } else {
                            "Lokal erstellt; Supabase-Speicherung fehlgeschlagen."
                        }
                    } else {
                        "Lokal erstellt; nicht gespeichert (Gespräch höchstens 1 Minute)."
                    }

                    Toast.makeText(
                        ctx,
                        "Transkription & Notiz erfolgreich erstellt! ✨\n$remoteMessage",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Unbekannter Fehler"
                Toast.makeText(ctx, "Fehler: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val ok = storageManager.setCustomFolderUri(uri)
            if (ok) {
                storageDisplayName = storageManager.getStorageDisplayName()
                isGoogleDrive = storageManager.isGoogleDrive()
                Toast.makeText(ctx, "Zielordner gespeichert:\n$storageDisplayName", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ctx, "Ordner konnte nicht zugewiesen werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveSingleToTarget(file: File) {
        val uri = storageManager.getCustomFolderUri()
        if (uri == null) {
            Toast.makeText(ctx, "Bitte zuerst einen Zielordner oder Google Drive auswählen", Toast.LENGTH_SHORT).show()
            folderPickerLauncher.launch(null)
            return
        }
        coroutineScope.launch {
            val res = storageManager.saveFileToCustomFolder(file)
            if (res.isSuccess) {
                Toast.makeText(ctx, "In Zielordner gespeichert:\n${res.getOrNull()}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Fehler beim Speichern: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun copyAllToTarget() {
        val uri = storageManager.getCustomFolderUri()
        if (uri == null) {
            Toast.makeText(ctx, "Bitte zuerst einen Zielordner oder Google Drive auswählen", Toast.LENGTH_SHORT).show()
            folderPickerLauncher.launch(null)
            return
        }
        if (recordingsList.isEmpty()) {
            Toast.makeText(ctx, "Keine Aufnahmen zum Kopieren vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            isExportingAll = true
            val (count, errors) = storageManager.copyAllToCustomFolder(recordingsList)
            isExportingAll = false
            if (errors.isEmpty()) {
                Toast.makeText(ctx, "$count Aufnahme(n) erfolgreich im Zielordner gespeichert!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(ctx, "$count gespeichert, ${errors.size} Fehler", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshRecordings() {
        val dir = File(ctx.filesDir, "smart_calls_recordings")
        if (dir.exists() && dir.isDirectory) {
            recordingsList = dir.listFiles()?.filter { it.isFile && (it.name.endsWith(".mp4") || it.name.endsWith(".wav") || it.name.endsWith(".m4a")) }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            recordingsList = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        refreshRecordings()
        reloadTranscripts()
        syncCachedNotesToSupabase()
        reloadSmartCallNotes()
    }

    LaunchedEffect(lastRecordingFile) {
        refreshRecordings()
        reloadTranscripts()
    }

    LaunchedEffect(lastRecordingFile, callDuration) {
        val file = lastRecordingFile
        if (file != null && callDuration > 0) {
            recordingDurations = recordingDurations + (file.name to callDuration.toLong())
        }
    }

    LaunchedEffect(isRecording) {
        if (!isRecording) {
            val finishedFile = lastRecordingFile
            if (
                finishedFile != null &&
                callDuration > 60 &&
                cachedTranscripts[finishedFile.name] == null &&
                !transcribingFiles.contains(finishedFile.name)
            ) {
                startTranscription(finishedFile)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sipClient.disconnect()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    fun saveConfig() {
        prefs.edit().apply {
            putString("displayName", displayName)
            putString("sipUser", sipUser)
            putString("authUser", authUser)
            putString("sipPassword", sipPassword)
            putString("sipRegistrar", sipRegistrar)
            putString("protocol", selectedProtocol.name)
            putInt("port", sipPort.toIntOrNull() ?: selectedProtocol.defaultPort)
            apply()
        }
    }

    fun connect() {
        if (sipUser.isBlank() || sipPassword.isBlank() || sipRegistrar.isBlank()) {
            Toast.makeText(ctx, "Bitte Benutzername, Passwort und Registrar ausfüllen", Toast.LENGTH_SHORT).show()
            return
        }
        saveConfig()
        val port = sipPort.toIntOrNull() ?: selectedProtocol.defaultPort
        val config = SipAccountConfig(
            displayName = displayName,
            sipUser = sipUser.trim(),
            authUser = authUser.trim(),
            sipPassword = sipPassword.trim(),
            sipRegistrar = sipRegistrar.trim(),
            protocol = selectedProtocol,
            port = port
        )
        sipClient.register(config)
    }

    // Automatically connect when the Smart Calls 2 tab is opened.
    // Credentials are already stored locally by saveConfig().
    LaunchedEffect(Unit) {
        if (sipUser.isNotBlank() &&
            sipPassword.isNotBlank() &&
            sipRegistrar.isNotBlank()
        ) {
            connect()
        }
    }

    fun playRecording(file: File) {
        try {
            if (currentlyPlayingPath == file.absolutePath && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                currentlyPlayingPath = null
                return
            }
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    currentlyPlayingPath = null
                }
            }
            currentlyPlayingPath = file.absolutePath
        } catch (e: Exception) {
            Toast.makeText(ctx, "Fehler beim Abspielen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteRecording(file: File) {
        if (currentlyPlayingPath == file.absolutePath) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentlyPlayingPath = null
        }
        file.delete()
        refreshRecordings()
        Toast.makeText(ctx, "Aufnahme gelöscht", Toast.LENGTH_SHORT).show()
    }

    // Color Palette
    val Bg = Color(0xFF0B1120)
    val SurfaceColor = Color(0xFF1E293B)
    val CardBorder = Color(0xFF334155)
    val NeonCyan = Color(0xFF00F0FF)
    val NeonGreen = Color(0xFF10B981)
    val WarningYellow = Color(0xFFF59E0B)
    val DangerRed = Color(0xFFEF4444)
    val TextMuted = Color(0xFF94A3B8)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Smart Calls",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Manuelle SIP-Telefonie (UDP / TCP / TLS)",
                    fontSize = 12.sp,
                    color = NeonCyan
                )
            }

            // Status Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when (sipState) {
                    SipState.REGISTERED -> NeonGreen.copy(alpha = 0.15f)
                    SipState.IN_CALL -> NeonGreen.copy(alpha = 0.25f)
                    SipState.DIALING, SipState.RINGING, SipState.CONNECTING -> WarningYellow.copy(alpha = 0.15f)
                    SipState.ERROR -> DangerRed.copy(alpha = 0.15f)
                    SipState.DISCONNECTED -> TextMuted.copy(alpha = 0.15f)
                },
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when (sipState) {
                        SipState.REGISTERED -> NeonGreen
                        SipState.IN_CALL -> NeonGreen
                        SipState.DIALING, SipState.RINGING, SipState.CONNECTING -> WarningYellow
                        SipState.ERROR -> DangerRed
                        SipState.DISCONNECTED -> CardBorder
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (sipState) {
                                    SipState.REGISTERED -> NeonGreen
                                    SipState.IN_CALL -> NeonGreen
                                    SipState.DIALING, SipState.RINGING, SipState.CONNECTING -> WarningYellow
                                    SipState.ERROR -> DangerRed
                                    SipState.DISCONNECTED -> TextMuted
                                }
                            )
                    )
                    Text(
                        text = when (sipState) {
                            SipState.REGISTERED -> "Bereit"
                            SipState.IN_CALL -> "Im Gespräch"
                            SipState.DIALING -> "Wählt..."
                            SipState.RINGING -> "Klingelt..."
                            SipState.CONNECTING -> "Verbinde..."
                            SipState.ERROR -> "Fehler"
                            SipState.DISCONNECTED -> "Offline"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Live Status Information Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = SurfaceColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (sipState) {
                        SipState.REGISTERED -> Icons.Default.CheckCircle
                        SipState.IN_CALL -> Icons.Default.PhoneInTalk
                        SipState.DIALING, SipState.RINGING -> Icons.Default.RingVolume
                        SipState.CONNECTING -> Icons.Default.Sync
                        SipState.ERROR -> Icons.Default.ErrorOutline
                        SipState.DISCONNECTED -> Icons.Default.WifiOff
                    },
                    contentDescription = null,
                    tint = when (sipState) {
                        SipState.REGISTERED, SipState.IN_CALL -> NeonGreen
                        SipState.DIALING, SipState.RINGING, SipState.CONNECTING -> WarningYellow
                        SipState.ERROR -> DangerRed
                        SipState.DISCONNECTED -> TextMuted
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ACTIVE CALL PANEL (When in call or dialing/ringing)
        if (sipState == SipState.IN_CALL || sipState == SipState.DIALING || sipState == SipState.RINGING) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF132338)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonCyan)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (sipState == SipState.IN_CALL) "AKTIVER ANRUF" else "VERBINDUNGSAUFBAU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = targetNumber,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )

                    // Call Duration Timer
                    if (sipState == SipState.IN_CALL) {
                        val minutes = callDuration / 60
                        val seconds = callDuration % 60
                        val durationFormatted = String.format("%02d:%02d", minutes, seconds)

                        Text(
                            text = durationFormatted,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen,
                            fontFamily = FontFamily.Monospace
                        )

                        // Pulsing Recording Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed)
                            )
                            Text(
                                text = "HD-Aufnahme läuft (Lokal & Verschlüsselt)",
                                fontSize = 12.sp,
                                color = DangerRed,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = statusText,
                            fontSize = 14.sp,
                            color = WarningYellow
                        )
                    }

                    // Call Action Buttons (Mute, Speaker, Hangup)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute Button
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) WarningYellow else SurfaceColor)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (isMuted) Color.Black else Color.White
                            )
                        }

                        // Hangup Button (Big Red)
                        IconButton(
                            onClick = { sipClient.hangUp() },
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(DangerRed)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Auflegen",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Speaker Button
                        IconButton(
                            onClick = { isSpeakerOn = !isSpeakerOn },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isSpeakerOn) NeonCyan else SurfaceColor)
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = "Speaker",
                                tint = if (isSpeakerOn) Color.Black else Color.White
                            )
                        }
                    }
                }
            }
        }

        // DIALER & CALLING CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Zielrufnummer",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 15.sp
                )

                OutlinedTextField(
                    value = targetNumber,
                    onValueChange = { targetNumber = it },
                    placeholder = { Text("z.B. 01701234567 oder 0049...", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = Bg,
                        unfocusedContainerColor = Bg
                    ),
                    trailingIcon = {
                        Row {
                            if (targetNumber.isNotEmpty()) {
                                IconButton(onClick = { targetNumber = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Löschen", tint = TextMuted)
                                }
                            }
                            IconButton(onClick = { showDialpad = !showDialpad }) {
                                Icon(
                                    Icons.Default.Dialpad,
                                    contentDescription = "Tastatur",
                                    tint = if (showDialpad) NeonCyan else TextMuted
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Dialpad toggle
                AnimatedVisibility(visible = showDialpad) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val buttons = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("*", "0", "#")
                        )
                        for (row in buttons) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (digit in row) {
                                    Button(
                                        onClick = { targetNumber += digit },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                    ) {
                                        Text(digit, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Call Action Button
                Button(
                    onClick = {
                        when (sipState) {
                            SipState.REGISTERED -> sipClient.makeCall(targetNumber)
                            SipState.CONNECTING -> Toast.makeText(ctx, "Verbindung wird noch aufgebaut...", Toast.LENGTH_SHORT).show()
                            SipState.ERROR -> Toast.makeText(ctx, "SIP Fehler. Bitte neu verbinden.", Toast.LENGTH_SHORT).show()
                            else -> Toast.makeText(ctx, "Bitte zuerst SIP-Konto verbinden", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = targetNumber.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        disabledContainerColor = Color(0xFF273549)
                    )
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("Jetzt Anrufen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // SIP CONFIGURATION CARD (Easybell, TLS, UDP, TCP)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SIP-Zugangsdaten (Easybell / VoIP)",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    // Quick Easybell Preset Button
                    AssistChip(
                        onClick = {
                            sipRegistrar = "voip.easybell.de"
                            selectedProtocol = SipTransportProtocol.TLS
                            sipPort = "5061"
                        },
                        label = { Text("Easybell Preset", fontSize = 11.sp, color = NeonCyan) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = NeonCyan.copy(alpha = 0.1f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                    )
                }

                // Protocol Selector (UDP / TCP / TLS)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Transport-Protokoll & Verschlüsselung",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SipTransportProtocol.entries.forEach { proto ->
                            val isSelected = selectedProtocol == proto
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedProtocol = proto
                                        sipPort = proto.defaultPort.toString()
                                    },
                                color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else Bg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) NeonCyan else CardBorder
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = proto.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) NeonCyan else Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Form Fields
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = sipRegistrar,
                        onValueChange = { sipRegistrar = it },
                        label = { Text("Registrar / Server") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = Bg,
                            unfocusedContainerColor = Bg
                        )
                    )

                    OutlinedTextField(
                        value = sipPort,
                        onValueChange = { sipPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = Bg,
                            unfocusedContainerColor = Bg
                        )
                    )
                }

                OutlinedTextField(
                    value = sipUser,
                    onValueChange = { sipUser = it },
                    label = { Text("SIP-Benutzername / Rufnummer (z.B. 0049...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = Bg,
                        unfocusedContainerColor = Bg
                    )
                )

                OutlinedTextField(
                    value = authUser,
                    onValueChange = { authUser = it },
                    label = { Text("Auth-Benutzername (Optional)") },
                    placeholder = { Text("Leer lassen falls identisch mit SIP-Benutzer", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = Bg,
                        unfocusedContainerColor = Bg
                    )
                )

                OutlinedTextField(
                    value = sipPassword,
                    onValueChange = { sipPassword = it },
                    label = { Text("SIP-Passwort") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = Bg,
                        unfocusedContainerColor = Bg
                    )
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Anzeigename (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = Bg,
                        unfocusedContainerColor = Bg
                    )
                )

                // Connect / Disconnect Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (sipState == SipState.DISCONNECTED || sipState == SipState.ERROR) {
                        Button(
                            onClick = { connect() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(8.dp))
                            Text("SIP Verbinden (${selectedProtocol.name})", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { sipClient.disconnect() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Verbindung Trennen", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // STORAGE DESTINATION & GOOGLE DRIVE SYNC CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isGoogleDrive) Icons.Default.CloudSync else Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = if (isGoogleDrive) Color(0xFF4285F4) else NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Aufnahme-Speicherort & Google Drive",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Zielordner frei wählen (lokal oder Google Drive)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                // Current Destination Surface Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Bg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isGoogleDrive) Color(0xFF4285F4).copy(alpha = 0.4f) else CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isGoogleDrive) Color(0xFF4285F4).copy(alpha = 0.2f) else if (storageManager.getCustomFolderUri() != null) NeonGreen.copy(alpha = 0.2f) else SurfaceColor,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isGoogleDrive) Icons.Default.CloudDone else if (storageManager.getCustomFolderUri() != null) Icons.Default.FolderOpen else Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isGoogleDrive) Color(0xFF4285F4) else if (storageManager.getCustomFolderUri() != null) NeonGreen else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = storageDisplayName,
                                fontWeight = FontWeight.Bold,
                                color = if (isGoogleDrive) Color(0xFF93C5FD) else Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isGoogleDrive) "Aufnahmen werden direkt in Google Drive gesichert" else if (storageManager.getCustomFolderUri() != null) "Aufnahmen werden in diesen lokalen Ordner synchronisiert" else "Standardmäßig im internen App-Speicher abgelegt",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isGoogleDrive) Color(0xFF4285F4) else NeonCyan
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFolderUpload,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Zielordner / Drive wählen",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (storageManager.getCustomFolderUri() != null) {
                        OutlinedButton(
                            onClick = {
                                storageManager.resetToDefault()
                                storageDisplayName = storageManager.getStorageDisplayName()
                                isGoogleDrive = storageManager.isGoogleDrive()
                                Toast.makeText(ctx, "Auf Standard-Speicher zurückgesetzt", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Standard", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Auto-Sync Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Automatisch im Zielordner speichern",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Jede neue SmartCall-Aufnahme direkt übertragen",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = autoExportEnabled,
                        onCheckedChange = {
                            autoExportEnabled = it
                            storageManager.setAutoExportEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = NeonGreen.copy(alpha = 0.3f)
                        )
                    )
                }

                // Batch Copy Button
                if (recordingsList.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { copyAllToTarget() },
                        enabled = !isExportingAll,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isGoogleDrive) Color(0xFF4285F4).copy(alpha = 0.6f) else NeonGreen.copy(alpha = 0.6f))
                    ) {
                        if (isExportingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NeonGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Wird übertragen...", color = Color.White, fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = if (isGoogleDrive) Icons.Default.CloudUpload else Icons.Default.FolderCopy,
                                contentDescription = null,
                                tint = if (isGoogleDrive) Color(0xFF4285F4) else NeonGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Alle ${recordingsList.size} Aufnahmen jetzt in Zielordner kopieren",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // LOCAL RECORDINGS ARCHIVE CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gespeicherte Aufnahmen (${recordingsList.size})",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Ab 1 Minute automatisch mit Gemini zusammenfassen & speichern",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showApiKeyDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Gemini API-Key",
                                tint = if (transcriber.hasApiKey()) Color(0xFF8B5CF6) else TextMuted
                            )
                        }
                        IconButton(onClick = {
                            refreshRecordings()
                            reloadTranscripts()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = TextMuted)
                        }
                    }
                }

                if (recordingsList.isEmpty()) {
                    Text(
                        text = "Noch keine Anrufe aufgezeichnet. Anrufe über Smart Calls werden automatisch hier gespeichert.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recordingsList.forEach { file ->
                            val isPlaying = currentlyPlayingPath == file.absolutePath
                            val isTranscribing = transcribingFiles.contains(file.name)
                            val transcriptResult = cachedTranscripts[file.name]
                            val isTranscribed = transcriptResult != null

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Bg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isPlaying) NeonCyan else if (isTranscribed) Color(0xFF8B5CF6).copy(alpha = 0.5f) else CardBorder
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Row 1: File Info & Audio Actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = file.name,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                if (isTranscribed) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.AutoAwesome,
                                                                contentDescription = null,
                                                                tint = Color(0xFF8B5CF6),
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Text(
                                                                text = "KI-Notiz",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFFC4B5FD)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            val kb = file.length() / 1024
                                            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(file.lastModified()))
                                            Text(
                                                text = "$date · ${kb} KB",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // Play / Pause
                                            IconButton(onClick = { playRecording(file) }) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Abspielen",
                                                    tint = if (isPlaying) NeonCyan else NeonGreen
                                                )
                                            }

                                            // Save to Target / Drive
                                            IconButton(
                                                onClick = { saveSingleToTarget(file) }
                                            ) {
                                                Icon(
                                                    imageVector = if (isGoogleDrive) Icons.Default.CloudUpload else Icons.Default.SaveAlt,
                                                    contentDescription = "In Zielordner speichern",
                                                    tint = if (isGoogleDrive) Color(0xFF4285F4) else NeonCyan
                                                )
                                            }

                                            // Share / Open in Drive
                                            IconButton(
                                                onClick = { storageManager.shareRecording(file) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Teilen / In Drive senden",
                                                    tint = TextMuted
                                                )
                                            }

                                            // Delete
                                            IconButton(onClick = {
                                                deleteRecording(file)
                                                transcriptCache.delete(file.name)
                                                reloadTranscripts()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Löschen",
                                                    tint = DangerRed.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Transcription Area / Note Preview
                                    if (isTranscribing) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color(0xFF8B5CF6),
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = "✨ Gemini analysiert Audio & erstellt Notiz...",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFC4B5FD),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else if (isTranscribed && transcriptResult != null) {
                                        // Preview card with quick view button
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { activeTranscriptResult = transcriptResult },
                                            shape = RoundedCornerShape(8.dp),
                                            color = SurfaceColor,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.25f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = transcriptResult.summary.ifBlank { transcriptResult.rawText }
                                                        .lineSequence()
                                                        .filter { it.isNotBlank() && !it.startsWith("#") }
                                                        .take(3)
                                                        .joinToString("\n"),
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFE2E8F0),
                                                    maxLines = 3,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    lineHeight = 16.sp
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Tippen für vollständige Notiz & Transkript",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF93C5FD)
                                                    )
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Visibility,
                                                            contentDescription = null,
                                                            tint = Color(0xFF8B5CF6),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Ansehen",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF8B5CF6)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Transcribe Button
                                        OutlinedButton(
                                            onClick = { startTranscription(file) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFFC4B5FD)
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Mit Gemini transkribieren & Notiz erstellen",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SUPABASE SMART CALL NOTES (summary only)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gespeicherte Smart-Call-Notizen (${smartCallNotes.size})",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Nur Zusammenfassungen über 1 Minute · kein Audio und kein Transkript in Supabase",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = { reloadSmartCallNotes() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Smart-Call-Notizen aktualisieren",
                            tint = TextMuted
                        )
                    }
                }

                when {
                    isLoadingSmartCallNotes -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan
                            )
                        }
                    }
                    smartCallNotes.isEmpty() -> {
                        Text(
                            text = "Noch keine passenden Smart-Call-Zusammenfassungen gespeichert.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            smartCallNotes.forEach { note ->
                                val minutes = note.durationSeconds / 60
                                val seconds = note.durationSeconds % 60
                                val duration = String.format(
                                    Locale.GERMANY,
                                    "%02d:%02d Min.",
                                    minutes,
                                    seconds
                                )
                                val date = SimpleDateFormat(
                                    "dd.MM.yyyy HH:mm",
                                    Locale.GERMANY
                                ).format(Date(note.callStartedAt))
                                val title = note.contactName?.takeIf { it.isNotBlank() }
                                    ?: note.phone

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Bg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        CardBorder
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = duration,
                                                color = NeonCyan,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Text(
                                            text = "${note.phone} · $date",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = note.summary,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            maxLines = 8
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // TRANSCRIPTION DETAIL DIALOG
        activeTranscriptResult?.let { result ->
            TranscriptionDetailDialog(
                result = result,
                onDismiss = { activeTranscriptResult = null },
                onReTranscribe = {
                    val file = recordingsList.find { it.name == result.fileName }
                    activeTranscriptResult = null
                    if (file != null) {
                        startTranscription(file)
                    } else {
                        Toast.makeText(ctx, "Audiodatei nicht gefunden", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // GEMINI API KEY DIALOG
        if (showApiKeyDialog) {
            GeminiApiKeyDialog(
                onDismiss = {
                    showApiKeyDialog = false
                    pendingFileToTranscribe = null
                },
                onKeySaved = { key ->
                    showApiKeyDialog = false
                    pendingFileToTranscribe?.let { file ->
                        startTranscription(file)
                        pendingFileToTranscribe = null
                    }
                }
            )
        }
    }
}
