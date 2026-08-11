package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.database.AiCallEntity
import com.example.database.ContactEntity
import com.example.viewmodel.StromrufViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAnrufScreen(
    viewModel: StromrufViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("dialer") } // "dialer" or "notes"
    var showTestDialog by remember { mutableStateOf(false) }

    // Database state
    val aiCalls by viewModel.aiCalls.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diktat & Call-Notizen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Schließen",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        Color(0xFF1E293B).copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == "dialer") Color(0xFF334155) else Color.Transparent)
                        .clickable { activeTab = "dialer" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (activeTab == "dialer") Color(0xFF10B981) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Neuer Anruf & Diktat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == "dialer") Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == "notes") Color(0xFF334155) else Color.Transparent)
                        .clickable { activeTab = "notes" }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = if (activeTab == "notes") Color(0xFF10B981) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Gespeicherte Notizen (${aiCalls.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == "notes") Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .weight(0.8f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2563EB).copy(alpha = 0.25f))
                        .clickable { showTestDialog = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Testen",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "STT Test",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF60A5FA)
                        )
                    }
                }
            }

            // Crossfade for Tab Content
            Crossfade(
                targetState = activeTab,
                animationSpec = tween(300),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { tab ->
                when (tab) {
                    "dialer" -> {
                        DialerAndDictationTab(
                            contacts = contacts,
                            viewModel = viewModel
                        )
                    }
                    "notes" -> {
                        SavedNotesTab(
                            aiCalls = aiCalls,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    if (showTestDialog) {
        STTTestDialog(
            onDismiss = { showTestDialog = false }
        )
    }
}

@Composable
fun DialerAndDictationTab(
    contacts: List<ContactEntity>,
    viewModel: StromrufViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var dialNumber by remember { mutableStateOf("") }
    var selectedContactName by remember { mutableStateOf("") }
    var isContactsDropdownExpanded by remember { mutableStateOf(false) }

    // Transcription / Notepad state
    var noteText by remember { mutableStateOf("") }
    var isDictating by remember { mutableStateOf(false) }
    var speechErrorText by remember { mutableStateOf<String?>(null) }
    var recognizer: SpeechRecognizer? by remember { mutableStateOf(null) }

    val activeCall = com.example.service.DialerInCallService.activeCall.value
    val callTranscript = com.example.service.DialerInCallService.activeCallTranscript.value

    LaunchedEffect(callTranscript) {
        if (activeCall != null && callTranscript.isNotBlank()) {
            noteText = callTranscript
        }
    }

    // Safe Speech-to-Text stop helper
    val stopSpeechToText = {
        isDictating = false
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Safe Speech-to-Text start helper
    val startSpeechToText = {
        isDictating = true
        speechErrorText = null
        try {
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    // Speech timeout or silence is common, we restart if dictating is active
                    if (isDictating) {
                        coroutineScope.launch {
                            delay(400)
                            if (isDictating) {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    }
                                    recognizer?.startListening(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val resultText = matches?.firstOrNull() ?: ""
                    if (resultText.isNotBlank()) {
                        noteText = if (noteText.isBlank()) resultText else "$noteText $resultText"
                    }
                    
                    // Auto restart for continuous typing
                    if (isDictating) {
                        coroutineScope.launch {
                            delay(300)
                            if (isDictating) {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    }
                                    recognizer?.startListening(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            recognizer?.setRecognitionListener(listener)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            speechErrorText = "Dienst nicht verfügbar"
            isDictating = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            isDictating = false
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechToText()
        } else {
            Toast.makeText(context, "Mikrofon-Berechtigung erforderlich für Diktieren", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Contact dropdown selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("ai_contact_selector_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Empfänger auswählen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF334155).copy(alpha = 0.5f))
                            .clickable { isContactsDropdownExpanded = !isContactsDropdownExpanded }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedContactName.isNotBlank()) "$selectedContactName ($dialNumber)" else "Kontakt wählen...",
                            color = if (selectedContactName.isNotBlank()) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = if (isContactsDropdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    if (isContactsDropdownExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                if (contacts.isEmpty()) {
                                    item {
                                        Text(
                                            text = "Keine Kontakte vorhanden",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    items(contacts) { contact ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    dialNumber = contact.phone
                                                    selectedContactName = contact.name
                                                    isContactsDropdownExpanded = false
                                                }
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = contact.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = contact.phone,
                                                color = Color(0xFF10B981),
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
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

    // Dialer Number Field and Call Button Row
    item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = dialNumber,
                    onValueChange = {
                        dialNumber = it
                        val matching = contacts.find { c -> c.phone == it }
                        selectedContactName = matching?.name ?: ""
                    },
                    placeholder = {
                        Text(
                            "Rufnummer...",
                            color = Color.White.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_dial_number_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        cursorColor = Color(0xFF10B981)
                    )
                )

                // Green Call button placed on the right
                Button(
                    onClick = {
                        if (dialNumber.isBlank()) {
                            Toast.makeText(context, "Bitte geben Sie eine Rufnummer ein", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Initiate real call
                        viewModel.initiateCall(dialNumber, selectedContactName, callType = "ai_anruf")
                        Toast.makeText(context, "Anruf wird gestartet...", Toast.LENGTH_SHORT).show()

                        // Automatically request mic and start transcription
                        val recordAudioStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        if (recordAudioStatus == PackageManager.PERMISSION_GRANTED) {
                            startSpeechToText()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("ai_start_real_call_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Anrufen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Grid Dial Pad
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val padRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                padRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .clickable { dialNumber += digit }
                                    .border(
                                        BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = digit,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            if (dialNumber.isNotEmpty()) {
                                dialNumber = dialNumber.dropLast(1)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF334155).copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Löschen",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Help Information Text
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tipp: Kehren Sie nach dem Anrufstart in diese App zurück, um Ihre Worte mitschreiben zu lassen (z.B. auf Freisprecher).",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Live Notepad / Transcription Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().testTag("ai_notepad_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isDictating) Color(0xFFEF4444) else Color.White.copy(alpha = 0.2f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isDictating) "Transkription aktiv 🎤" else "Diktat inaktiv",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDictating) Color(0xFFEF4444) else Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Toggle Dictation
                        Button(
                            onClick = {
                                if (isDictating) {
                                    stopSpeechToText()
                                } else {
                                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    if (status == PackageManager.PERMISSION_GRANTED) {
                                        startSpeechToText()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDictating) Color(0xFFEF4444) else Color(0xFF334155)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isDictating) Icons.Default.Close else Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDictating) "Stop" else "Diktieren",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Notepad TextField
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = {
                            Text(
                                "Hier werden Ihre gesprochenen Worte live transkribiert. Sie können den Text auch jederzeit mit der Tastatur bearbeiten...",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 13.sp
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                            .testTag("ai_note_text_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981).copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons (Clear & Save)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { noteText = "" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Text("Leeren", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (noteText.isBlank()) {
                                    Toast.makeText(context, "Bitte geben Sie Text ein oder diktieren Sie etwas.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val targetName = selectedContactName.ifBlank { "Unbekannter Partner" }
                                val newCall = AiCallEntity(
                                    id = UUID.randomUUID().toString(),
                                    phone = dialNumber.ifBlank { "Unbekannt" },
                                    contactName = targetName,
                                    timestamp = System.currentTimeMillis(),
                                    audioFilePath = null,
                                    transcript = noteText,
                                    durationSeconds = 0,
                                    notes = "Anrufs-Notiz"
                                )
                                viewModel.saveAiCall(newCall)
                                Toast.makeText(context, "Notiz erfolgreich gespeichert! 💾", Toast.LENGTH_SHORT).show()
                                noteText = ""
                                stopSpeechToText()
                            },
                            modifier = Modifier.weight(1.5f).testTag("ai_save_note_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Notiz Speichern", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedNotesTab(
    aiCalls: List<AiCallEntity>,
    viewModel: StromrufViewModel
) {
    val context = LocalContext.current
    var expandedNoteId by remember { mutableStateOf<String?>(null) }

    if (aiCalls.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "Keine gespeicherten Notizen vorhanden.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Starten Sie ein Diktat oder einen Anruf, um hier wichtige Notizen festzuhalten.",
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.width(240.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(aiCalls) { call ->
                val isExpanded = call.id == expandedNoteId
                val sdf = remember { SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMAN) }
                val formattedDate = sdf.format(Date(call.timestamp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isExpanded) Color(0xFF10B981).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedNoteId = if (isExpanded) null else call.id }
                        .testTag("saved_note_item_${call.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = call.contactName ?: "Unbekannter Partner",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = call.phone,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = formattedDate,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )

                                IconButton(
                                    onClick = {
                                        viewModel.deleteAiCall(call.id)
                                        Toast.makeText(context, "Notiz gelöscht", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Löschen",
                                        tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Notepad contents (truncated or full)
                        Text(
                            text = call.transcript,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = if (isExpanded) 100 else 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
                        )

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // Action buttons inside expanded card
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clipData = android.content.ClipData.newPlainText("Anrufsnotiz", call.transcript)
                                        clipboardManager.setPrimaryClip(clipData)
                                        Toast.makeText(context, "Text in die Zwischenablage kopiert! 📋", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kopieren", fontSize = 11.sp, color = Color(0xFF10B981))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun STTTestDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isListening by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Bereit zum Testen") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var volumeLevel by remember { mutableStateOf(0f) }

    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Request permission state
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Mikrofon-Berechtigung erforderlich!", Toast.LENGTH_SHORT).show()
        }
    }

    // Initialize/Destroy Recognizer with dialog lifecycle
    DisposableEffect(hasPermission) {
        if (hasPermission) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                errorText = "Dienst auf diesem Gerät nicht verfügbar"
            }
        }
        onDispose {
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val startListening = {
        if (hasPermission) {
            isListening = true
            errorText = null
            statusText = "Zuhören..."
            try {
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        statusText = "Bereit... Bitte sprechen Sie jetzt!"
                    }
                    override fun onBeginningOfSpeech() {
                        statusText = "Sprechen erkannt..."
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        volumeLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        statusText = "Verarbeite..."
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        volumeLevel = 0f
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio-Aufnahmefehler"
                            SpeechRecognizer.ERROR_CLIENT -> "Client-Fehler"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Fehlende Berechtigungen"
                            SpeechRecognizer.ERROR_NETWORK -> "Netzwerkfehler"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Netzwerk-Timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Nichts verstanden. Bitte lauter sprechen."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Spracherkennungsdienst belegt"
                            SpeechRecognizer.ERROR_SERVER -> "Serverfehler"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keine Spracheingabe erkannt"
                            else -> "Unbekannter Fehler ($error)"
                        }
                        errorText = msg
                        statusText = "Test gestoppt"
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        volumeLevel = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            transcribedText = if (transcribedText.isBlank()) text else "$transcribedText\n$text"
                            statusText = "Erfolgreich transkribiert!"
                        } else {
                            statusText = "Kein Text erkannt"
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            statusText = "Schreibt: \"$text\""
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }

                recognizer?.setRecognitionListener(listener)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                errorText = e.localizedMessage ?: "Fehler beim Starten"
                isListening = false
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val stopListening = {
        isListening = false
        volumeLevel = 0f
        statusText = "Inaktiv"
        try {
            recognizer?.stopListening()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
                Text("Diktat-Erkennung testen", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Sprechen Sie in Ihr Mikrofon, um das Diktieren live zu testen. Ihre gesprochenen Worte werden in Echtzeit mitgeschrieben.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                // Visualizer area
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isListening) {
                        // Ripple Animation based on volume level
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale1 by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.3f + (volumeLevel * 0.7f),
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale1"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        )
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.25f))
                        )
                    }

                    // Microphone Icon button
                    IconButton(
                        onClick = {
                            if (isListening) stopListening() else startListening()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFFEF4444) else Color(0xFF10B981))
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Close else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stoppen" else "Sprechen starten",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Status indicators
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = statusText,
                        color = if (isListening) Color(0xFF10B981) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )

                    errorText?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Live Transcribed Text Result Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        if (transcribedText.isBlank()) {
                            Text(
                                "Ihr gesprochener Text erscheint hier...",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 13.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item {
                                    Text(
                                        text = transcribedText,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { transcribedText = "" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Text("Leeren", fontSize = 12.sp)
                    }

                    if (transcribedText.isNotBlank()) {
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = android.content.ClipData.newPlainText("Diktat-Test", transcribedText)
                                clipboardManager.setPrimaryClip(clipData)
                                Toast.makeText(context, "Text kopiert! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Kopieren", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    stopListening()
                    onDismiss()
                }
            ) {
                Text("Fertig", color = Color(0xFF10B981))
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}
