package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.CallLogEntity
import com.example.database.ContactEntity
import com.example.database.FollowUpEntity
import com.example.ui.design.*
import com.example.ui.theme.*
import com.example.viewmodel.StromrufViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AktivitaetenScreen(
    viewModel: StromrufViewModel,
    onAddFollowUp: () -> Unit
) {
    val followUps by viewModel.activeFollowUps.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val hotBoxLists by viewModel.hotBoxLists.collectAsState()

    // 0 = Verlauf, 1 = Geplant, 2 = Kalender, 3 = Telegram-Notiz
    var mode by remember { mutableStateOf(0) }
    var contactToEditInAktivitaeten by remember { mutableStateOf<ContactEntity?>(null) }
    var contactForHistoryOptions by remember { mutableStateOf<ContactEntity?>(null) }
    var showAddFollowUpForContact by remember { mutableStateOf<ContactEntity?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.syncSystemCallLogs(context)
    }

    val onContactClick: (CallLogEntity) -> Unit = { log ->
        val cleanPhoneLog = log.phone.replace("[^\\d]".toRegex(), "")
        val matchedContact = contacts.find { c ->
            val cleanPhoneC = c.phone.replace("[^\\d]".toRegex(), "")
            if (cleanPhoneLog.isEmpty() || cleanPhoneC.isEmpty()) false
            else cleanPhoneLog.takeLast(8) == cleanPhoneC.takeLast(8)
        }
        if (matchedContact != null) {
            contactForHistoryOptions = matchedContact
        } else {
            contactForHistoryOptions = ContactEntity(
                id = java.util.UUID.randomUUID().toString(),
                name = log.contactName ?: "",
                phone = log.phone,
                company = null,
                email = null,
                lastCallAt = log.timestamp,
                lastOutcome = log.outcome,
                isHotBox = false
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Dim.screenPad).padding(bottom = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Aktivitäten",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddFollowUp) {
                Icon(Icons.Default.Add, "Wiedervorlage", tint = ThemeSecondary)
            }
        }

        Spacer(Modifier.height(Dim.gap))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SegmentedControl(
                options = listOf("Verlauf", "Geplant", "Kalender"),
                selectedIndex = if (mode == 3) -1 else mode,
                onSelect = { mode = it },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            PrimaryButton(
                text = "Telegram-Notiz",
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = { mode = 3 },
                modifier = Modifier.height(48.dp)
            )
        }

        Spacer(Modifier.height(Dim.gap))

        when (mode) {
            0 -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dim.gap)) {
                historySection(callLogs, contacts, viewModel, onContactClick)
            }
            1 -> PlannedSection(followUps, viewModel)
            2 -> ActivityCalendarContent(viewModel = viewModel, onAddFollowUp = onAddFollowUp)
            else -> TelegramNoteSection(viewModel)
        }
    }

    contactForHistoryOptions?.let { contact ->
        CustomerTimelineDialog(
            contactId = contact.id,
            contactName = contact.name,
            phone = contact.phone,
            callLogs = callLogs,
            source = "activity",
            onDismiss = { contactForHistoryOptions = null },
            onEditContact = {
                contactToEditInAktivitaeten = contact
                contactForHistoryOptions = null
            },
            onAddFollowUp = {
                showAddFollowUpForContact = contact
                contactForHistoryOptions = null
            }
        )
    }

    showAddFollowUpForContact?.let { contact ->
        com.example.AddFollowUpDialog(
            contacts = contacts,
            initialName = contact.name,
            initialPhone = contact.phone,
            onDismiss = { showAddFollowUpForContact = null },
            onConfirm = { name, phone, note, dueAt, callReason ->
                viewModel.addManualFollowUp(name, phone, note, dueAt, callReason)
                showAddFollowUpForContact = null
            }
        )
    }

    contactToEditInAktivitaeten?.let { contact ->
        com.example.ContactDialog(
            title = if (contacts.any { it.id == contact.id }) "Kontakt bearbeiten" else "Kontakt speichern",
            initialName = contact.name,
            initialPhone = contact.phone,
            initialCompany = contact.company ?: "",
            initialEmail = contact.email ?: "",
            initialIsHotBox = contact.isHotBox,
            initialHotBoxStartHour = contact.hotBoxStartHour,
            initialHotBoxEndHour = contact.hotBoxEndHour,
            initialHotBoxWeekdays = contact.hotBoxWeekdays,
            initialConsumption = contact.consumption,
            initialZipCode = contact.zipCode,
            initialEnergyType = contact.energyType,
            hotBoxLists = hotBoxLists,
            initialHotBoxListName = contact.hotBoxListName ?: viewModel.selectedHotBoxListName.value,
            onDismiss = { contactToEditInAktivitaeten = null },
            onConfirm = { name, phone, company, email, isHotBox, startHour, endHour, weekdays, consumption, zipCode, energyType, hotBoxListName ->
                val updated = contact.copy(
                    name = name,
                    phone = phone,
                    company = company.takeIf { it.isNotBlank() },
                    email = email.takeIf { it.isNotBlank() },
                    isHotBox = isHotBox,
                    hotBoxListName = if (isHotBox) hotBoxListName else null,
                    hotBoxStartHour = if (isHotBox) startHour else null,
                    hotBoxEndHour = if (isHotBox) endHour else null,
                    hotBoxWeekdays = if (isHotBox) weekdays else null,
                    consumption = consumption,
                    zipCode = zipCode,
                    energyType = energyType
                )
                viewModel.editContact(updated)
                contactToEditInAktivitaeten = null
            }
        )
    }
}

@Composable
fun TelegramNoteSection(viewModel: StromrufViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val telegram = remember { com.example.util.TelegramClient(context) }
    val contacts by viewModel.contacts.collectAsState()

    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactCompany by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var isDictating by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<android.speech.SpeechRecognizer?>(null) }

    val startSpeech = {
        isDictating = true
        try {
            if (recognizer == null) recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
            val listener = object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (isDictating) scope.launch {
                        kotlinx.coroutines.delay(400)
                        if (isDictating) runCatching {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().language)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            }
                            recognizer?.startListening(intent)
                        }
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val resultText = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (resultText.isNotBlank()) noteText = if (noteText.isBlank()) resultText else "$noteText $resultText"
                    if (isDictating) scope.launch {
                        kotlinx.coroutines.delay(300)
                        if (isDictating) runCatching {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().language)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            }
                            recognizer?.startListening(intent)
                        }
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            }
            recognizer?.setRecognitionListener(listener)
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().language)
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            isDictating = false
        }
    }

    val stopSpeech = {
        isDictating = false
        runCatching { recognizer?.stopListening(); recognizer?.destroy(); recognizer = null }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { recognizer?.stopListening(); recognizer?.destroy() } }
    }

    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startSpeech()
        else android.widget.Toast.makeText(context, "Mikrofon-Berechtigung erforderlich", android.widget.Toast.LENGTH_SHORT).show()
    }

    val filteredContacts = remember(contactName, contacts) {
        if (contactName.isBlank() || contactName.length < 2) emptyList()
        else contacts.filter {
            it.name.contains(contactName, ignoreCase = true) || (it.company ?: "").contains(contactName, ignoreCase = true)
        }.take(5)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dim.gap)
    ) {
        SectionHeader("NOTIZ AN TELEGRAM")
        Text(
            "Sende eine formatierte Kundennotiz schnell und direkt an deinen verknüpften Telegram-Bot.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        AppCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        OutlinedTextField(
                            value = contactName,
                            onValueChange = { contactName = it; showSuggestions = true },
                            label = { Text("Kundenname *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ThemeAccent,
                                focusedLabelColor = ThemeAccent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        if (showSuggestions && filteredContacts.isNotEmpty()) {
                            DropdownMenu(
                                expanded = showSuggestions,
                                onDismissRequest = { showSuggestions = false },
                                modifier = Modifier.fillMaxWidth(0.9f).background(SlateHigh)
                            ) {
                                filteredContacts.forEach { suggestion ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(suggestion.name, color = TextPrimary, fontWeight = FontWeight.Bold)
                                                Text(
                                                    listOfNotNull(suggestion.company?.takeIf { it.isNotBlank() }, suggestion.phone).joinToString(" · "),
                                                    color = TextMuted,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        },
                                        onClick = {
                                            contactName = suggestion.name
                                            contactPhone = suggestion.phone
                                            contactCompany = suggestion.company ?: ""
                                            showSuggestions = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    label = { Text("Telefonnummer (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = contactCompany,
                    onValueChange = { contactCompany = it },
                    label = { Text("Firma (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Nachricht / Notiz *", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .background(if (isDictating) Color(0xFFEF4444).copy(alpha = 0.2f) else SlateHigh)
                                .clickable {
                                    if (isDictating) stopSpeech()
                                    else {
                                        val status = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                                        if (status == android.content.pm.PackageManager.PERMISSION_GRANTED) startSpeech()
                                        else micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (isDictating) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isDictating) Color(0xFFEF4444) else ThemeAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                if (isDictating) "Stop Diktat" else "Diktieren 🎙",
                                color = if (isDictating) Color(0xFFEF4444) else ThemeAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Hier Text eingeben oder oben diktieren…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (telegram.isConfigured()) "Wird an deinen Telegram-Bot gesendet" else "Bitte richte Telegram in den Einstellungen ein",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        if (sending) "Sendet…" else "An Telegram senden",
                        icon = Icons.AutoMirrored.Filled.Send,
                        enabled = !sending && contactName.isNotBlank() && noteText.isNotBlank() && telegram.isConfigured(),
                        onClick = {
                            scope.launch {
                                sending = true
                                val result = telegram.sendCustomerNoteDetailed(
                                    contactName = contactName,
                                    company = contactCompany.takeIf { it.isNotBlank() },
                                    phone = contactPhone.takeIf { it.isNotBlank() },
                                    note = noteText
                                )
                                sending = false
                                val payload = org.json.JSONObject().apply {
                                    put("id", java.util.UUID.randomUUID().toString())
                                    put("contact_name", contactName)
                                    put("contact_phone", contactPhone.takeIf { it.isNotBlank() } ?: org.json.JSONObject.NULL)
                                    put("raw_note", noteText)
                                    put("subject", "")
                                    put("body", "")
                                    put("provider", "telegram")
                                    put("status", "notiz")
                                    put("created_at_ms", System.currentTimeMillis())
                                    put("telegram_status", if (result.ok) "sent" else "failed")
                                    put("telegram_sent_at_ms", if (result.ok) System.currentTimeMillis() else org.json.JSONObject.NULL)
                                    put("error_message", if (result.ok) org.json.JSONObject.NULL else result.detail)
                                }
                                com.example.util.SupabaseDbClient.upsertTableRow(context, "customer_messages", payload)
                                if (result.ok) {
                                    android.widget.Toast.makeText(context, "Notiz an Telegram gesendet ✓", android.widget.Toast.LENGTH_SHORT).show()
                                    noteText = ""; contactName = ""; contactPhone = ""; contactCompany = ""
                                } else {
                                    android.widget.Toast.makeText(context, "Senden fehlgeschlagen: ${result.detail}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlannedSection(followUps: List<FollowUpEntity>, viewModel: StromrufViewModel) {
    val pagerState = rememberPagerState(initialPage = 3, pageCount = { 7 })
    val pageTitles = listOf("Letzter Monat", "Letzte Woche", "Gestern", "Heute", "Morgen", "Nächste Woche", "Nächster Monat")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(pageTitles[pagerState.currentPage], style = MaterialTheme.typography.titleMedium, color = ThemeSecondary)
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val groupAccent = ThemeSecondary
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dim.gap)) {
                val now = System.currentTimeMillis()
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val startEnd: Pair<Long, Long> = when (page) {
                    0 -> Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis to
                        Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                    1 -> todayStart - 14L * 24 * 3600_000 to todayStart - 7L * 24 * 3600_000
                    2 -> todayStart - 24L * 3600_000 to todayStart
                    3 -> todayStart to todayStart + 24L * 3600_000
                    4 -> todayStart + 24L * 3600_000 to todayStart + 48L * 3600_000
                    5 -> todayStart + 48L * 3600_000 to todayStart + 9L * 24 * 3600_000
                    6 -> todayStart + 9L * 24 * 3600_000 to todayStart + 40L * 24 * 3600_000
                    else -> 0L to 0L
                }
                val items = followUps.filter { it.dueAt in startEnd.first until startEnd.second }.sortedBy { it.dueAt }
                if (items.isEmpty()) {
                    item { EmptyState(Icons.Default.Notifications, "Keine Wiedervorlagen", "Keine Termine für diesen Zeitraum.") }
                } else {
                    followUpGroup(pageTitles[page].uppercase(), items, groupAccent, viewModel, now)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.followUpGroup(
    title: String,
    items: List<FollowUpEntity>,
    accent: Color,
    viewModel: StromrufViewModel,
    now: Long
) {
    if (items.isEmpty()) return
    val timeFmt = SimpleDateFormat("EEE dd.MM. · HH:mm", Locale.GERMAN)
    item(key = "grp_$title") { SectionHeader("$title · ${items.size}") }
    items(items.size, key = { i -> items[i].id }) { i ->
        val fu = items[i]
        val overdue = fu.dueAt <= now
        PersonRow(
            title = fu.contactName,
            subtitle = fu.note ?: fu.contactPhone,
            leadingIcon = Icons.Default.Notifications,
            leadingTint = accent,
            badge = { StatusBadge(timeFmt.format(Date(fu.dueAt)), if (overdue) BadgeTone.Warn else BadgeTone.Info) },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { viewModel.initiateCall(fu.contactPhone, fu.contactName, fu.contactId) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Phone, "Anrufen", tint = ThemeAccent, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { viewModel.completeFollowUp(fu.id) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Check, "Erledigt", tint = ThemeSecondary, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { viewModel.deleteFollowUp(fu.id) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Delete, "Löschen", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historySection(
    callLogs: List<CallLogEntity>,
    contacts: List<ContactEntity>,
    viewModel: StromrufViewModel,
    onContactClick: (CallLogEntity) -> Unit
) {
    if (callLogs.isEmpty()) {
        item { EmptyState(Icons.Default.Phone, "Noch keine Anrufe protokolliert", "Nach jedem Gespräch wird der Verlauf hier automatisch erfasst.") }
        return
    }

    val sorted = callLogs.sortedByDescending { it.timestamp }
    val dayFmt = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMAN)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMAN)
    val grouped = sorted.groupBy { dayFmt.format(Date(it.timestamp)) }

    item {
        val todayStart = remember(sorted) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val today = sorted.count { it.timestamp >= todayStart }
        val context = androidx.compose.ui.platform.LocalContext.current
        AppCard {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$today", style = MaterialTheme.typography.headlineMedium, color = ThemeAccent)
                    Text("Anrufe heute", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Column(Modifier.weight(1f)) {
                    Text("${callLogs.size}", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Gesamt", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                OutlinedButton(
                    onClick = {
                        if (!com.example.util.ContactsUtil.hasCallLogPermission(context)) {
                            android.widget.Toast.makeText(context, "Anruflisten-Berechtigung erforderlich", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        viewModel.syncSystemCallLogs(context)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) { Text("🔄 Sync", color = ThemeAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }

    grouped.forEach { (dayStr, logs) ->
        item(key = "hdr_$dayStr") { SectionHeader(dayStr.uppercase()) }
        items(logs.size, key = { i -> logs[i].id }) { i ->
            val log = logs[i]
            val durationMin = log.durationSeconds / 60
            val durationSec = log.durationSeconds % 60
            val durStr = String.format("%02d:%02d", durationMin, durationSec)
            val isReached = log.outcome.startsWith("erreicht", ignoreCase = true)
            val accent = if (isReached) ThemeAccent else TextMuted
            val cleanPhoneLog = log.phone.replace("[^\\d]".toRegex(), "")
            val matchedContact = if (cleanPhoneLog.isNotEmpty()) contacts.find { c ->
                val cleanPhoneC = c.phone.replace("[^\\d]".toRegex(), "")
                cleanPhoneC.isNotEmpty() && cleanPhoneLog.takeLast(8) == cleanPhoneC.takeLast(8)
            } else null
            val displayName = if (matchedContact != null) {
                if (!matchedContact.company.isNullOrBlank()) "${matchedContact.name} (${matchedContact.company})" else matchedContact.name
            } else if (log.contactName.isNullOrBlank() || log.contactName in listOf("Unbekannt", "Unbekannter Kunde", "Anonym")) {
                log.phone.ifBlank { "Unbekannte Nummer" }
            } else log.contactName
            val subtitleText = buildString {
                append(timeFmt.format(Date(log.timestamp))); append(" · "); append(durStr); append(" Min.")
                if (displayName != log.phone && log.phone.isNotBlank()) { append(" · 📞 "); append(log.phone) }
            }
            val outcomeLabel = when (log.outcome) {
                "erreicht_interesse" -> "Interesse 👍"
                "erreicht_abschluss" -> "Abschluss 🏆"
                "erreicht_kein_interesse" -> "Kein Int. 👎"
                "nicht_erreicht" -> "Nicht err. ⏳"
                "falsche_nummer" -> "Falsche Nr. 🚫"
                else -> log.outcome.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString() }
            }
            val badgeTone = when {
                log.outcome == "erreicht_abschluss" -> BadgeTone.Success
                log.outcome == "erreicht_interesse" -> BadgeTone.Info
                log.outcome == "nicht_erreicht" -> BadgeTone.Warn
                log.outcome == "falsche_nummer" -> BadgeTone.Critical
                else -> BadgeTone.Neutral
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            PersonRow(
                title = displayName,
                subtitle = subtitleText,
                leadingIcon = Icons.Default.Phone,
                leadingTint = accent,
                badge = { StatusBadge(text = outcomeLabel, tone = badgeTone) },
                onClick = { onContactClick(log) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (matchedContact != null && matchedContact.isHotBox) {
                            IconButton(
                                onClick = {
                                    viewModel.toggleHotBox(matchedContact.id)
                                    android.widget.Toast.makeText(context, "${matchedContact.name} aus Hotbox entfernt", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(38.dp)
                            ) { Icon(Icons.Default.Delete, "Aus Hotbox entfernen", tint = Color(0xFFEF4444), modifier = Modifier.size(19.dp)) }
                        }
                        IconButton(onClick = { viewModel.initiateCall(log.phone, log.contactName, null) }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Phone, "Anrufen", tint = ThemeAccent, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            )
        }
    }
}
