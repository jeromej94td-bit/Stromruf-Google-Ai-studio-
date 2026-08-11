package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.database.ContactEntity
import com.example.ui.design.AppCard
import com.example.ui.design.BadgeTone
import com.example.ui.design.ChipRow
import com.example.ui.design.Dim
import com.example.ui.design.EmptyState
import com.example.ui.design.PersonRow
import com.example.ui.design.PrimaryButton
import com.example.ui.design.SecondaryButton
import com.example.ui.design.SectionHeader
import com.example.ui.design.SegmentedControl
import com.example.ui.design.SelectChip
import com.example.ui.design.StatusBadge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import com.example.ui.design.pulsingAura
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.SlateHigh
import com.example.ui.theme.ThemeSecondary
import com.example.ui.theme.ThemeAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarnAmber
import com.example.viewmodel.StromrufViewModel
import java.util.Calendar

// ============================================================
// LEADS – Fokusliste (Priorisierung), Neukunden (Zeitraumfilter),
// Heiße Angebote. Mit speicherbaren Ansichten.
// ============================================================

enum class Zeitraum(val label: String) {
    HEUTE("Heute"), GESTERN("Gestern"), TAGE7("7 Tage"),
    WOCHE("Diese Woche"), MONAT("Dieser Monat"), LETZTER_MONAT("Letzter Monat"),
    QUARTAL("Quartal"), ALLE("Alle");

    fun matches(ts: Long): Boolean {
        if (this == ALLE) return true
        val now = Calendar.getInstance()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return when (this) {
            HEUTE -> ts >= start.timeInMillis
            GESTERN -> {
                val y = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
                ts >= y.timeInMillis && ts < start.timeInMillis
            }
            TAGE7 -> ts >= start.timeInMillis - 6L * 24 * 3600_000
            WOCHE -> {
                val w = (start.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }
                ts >= w.timeInMillis
            }
            MONAT -> {
                val m = (start.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                ts >= m.timeInMillis
            }
            LETZTER_MONAT -> {
                val mStart = (start.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1)
                }
                val mEnd = (start.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                ts >= mStart.timeInMillis && ts < mEnd.timeInMillis
            }
            QUARTAL -> {
                val q = (start.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.MONTH, (now.get(Calendar.MONTH) / 3) * 3)
                }
                ts >= q.timeInMillis
            }
            ALLE -> true
        }
    }

    companion object {
        fun fromLabel(l: String) = entries.firstOrNull { it.label == l } ?: ALLE
    }
}

private data class SavedView(val name: String, val segment: Int, val zeitraum: String, val status: String) {
    fun serialize() = "$name|$segment|$zeitraum|$status"
    companion object {
        fun parse(s: String): SavedView? {
            val p = s.split("|")
            return if (p.size == 4) SavedView(p[0], p[1].toIntOrNull() ?: 0, p[2], p[3]) else null
        }
    }
}

private fun daysAgo(ts: Long): Int = ((System.currentTimeMillis() - ts) / (24L * 3600_000)).toInt()

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeadsScreen(
    viewModel: StromrufViewModel,
    onOpenContact: (ContactEntity) -> Unit,
    onAddNeukunde: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("stromruf_prefs", Context.MODE_PRIVATE) }

    val contacts by viewModel.contacts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val neukunden by viewModel.neukunden.collectAsState()
    val angebote by viewModel.heisseAngebote.collectAsState()
    val hotBoxLists by viewModel.hotBoxLists.collectAsState()
    val selectedFocusList by viewModel.selectedHotBoxListName.collectAsState()
    val selectedHotBoxListNames by viewModel.selectedHotBoxListNames.collectAsState()

    val isAutoCallActive by viewModel.isAutoCallActive.collectAsState()
    val isAutoCallPaused by viewModel.isAutoCallPaused.collectAsState()
    val autoCallCountdown by viewModel.autoCallCountdown.collectAsState()
    val nextHotBoxId by viewModel.nextHotBoxContactId.collectAsState()

    var segment by remember { mutableStateOf(0) } // 0 Fokus, 1 Neukunden, 2 Angebote, 3 Kontakte
    var zeitraum by remember { mutableStateOf(Zeitraum.TAGE7) }
    var statusFilter by remember { mutableStateOf("Alle") }
    var searchQuery by remember { mutableStateOf("") }
    var contactSortBy by remember { mutableStateOf("Anlagedatum") }
    var showAddContactInLeads by remember { mutableStateOf(false) }
    var energyTypeFilter by remember { mutableStateOf("Alle") }
    var showImportContactsInLeads by remember { mutableStateOf(false) }

    // --- Gespeicherte Ansichten ---
    var savedViews by remember {
        mutableStateOf(
            (prefs.getStringSet("stromruf_saved_views", emptySet()) ?: emptySet())
                .mapNotNull { SavedView.parse(it) }
        )
    }
    fun persistViews(list: List<SavedView>) {
        savedViews = list
        prefs.edit().putStringSet("stromruf_saved_views", list.map { it.serialize() }.toSet()).apply()
    }
    var showSaveDialog by remember { mutableStateOf(false) }
    var newViewName by remember { mutableStateOf("") }
    
    var listToDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showCreateListDialogInLeads by remember { mutableStateOf(false) }
    var newListNameInLeads by remember { mutableStateOf("") }
    var contactToDeleteConfirm by remember { mutableStateOf<ContactEntity?>(null) }
    var contactToEditInLeads by remember { mutableStateOf<ContactEntity?>(null) }

    val neukundenStatusOptions = listOf("Alle", "Anrufen", "Datenmail schreiben", "Angebot erstellen", "Zum Stand fragen")

    val sortedContacts = remember(contacts, contactSortBy, searchQuery, energyTypeFilter) {
        val filtered = if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter {
                val cleanQuery = searchQuery.replace("[^\\d]".toRegex(), "")
                val cleanPhone = it.phone.replace("[^\\d]".toRegex(), "")
                val matchesPhone = if (cleanQuery.isNotEmpty()) {
                    cleanPhone.contains(cleanQuery) || it.phone.contains(searchQuery)
                } else {
                    it.phone.contains(searchQuery)
                }
                it.name.contains(searchQuery, ignoreCase = true) ||
                matchesPhone ||
                (it.company ?: "").contains(searchQuery, ignoreCase = true) ||
                (it.email ?: "").contains(searchQuery, ignoreCase = true) ||
                (it.zipCode ?: "").contains(searchQuery)
            }
        }
        val filteredByEnergy = when (energyTypeFilter) {
            "Strom" -> filtered.filter { it.energyType == "Strom" }
            "Gas" -> filtered.filter { it.energyType == "Gas" }
            "Beide" -> filtered.filter { it.energyType == "Beide" }
            else -> filtered
        }
        when (contactSortBy) {
            "Anlagedatum" -> filteredByEnergy.sortedByDescending { it.dateCreated }
            "Verbrauch" -> filteredByEnergy.sortedWith(compareByDescending<ContactEntity> { it.consumption ?: 0L }.thenBy { it.name.lowercase() })
            "Postleitzahl" -> filteredByEnergy.sortedWith(compareBy<ContactEntity> { it.zipCode ?: "zzzzz" }.thenBy { it.name.lowercase() })
            "Name" -> filteredByEnergy.sortedBy { it.name.lowercase() }
            else -> filteredByEnergy
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dim.screenPad, end = Dim.screenPad, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Dim.gap)
    ) {
        // ---- Kopf ----
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Leads", style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onAddNeukunde) {
                    Icon(Icons.Default.PersonAdd, "Neuer Lead", tint = ThemeSecondary)
                }
                IconButton(onClick = { showSaveDialog = true }) {
                    Icon(Icons.Default.Bookmark, "Ansicht speichern", tint = TextSecondary)
                }
            }
        }
        item {
            SegmentedControl(
                options = listOf("Fokus", "Neukunden", "Angebote", "Kontakte"),
                selectedIndex = segment,
                onSelect = { segment = it }
            )
        }

        // ---- Gespeicherte Ansichten ----
        if (savedViews.isNotEmpty()) {
            item { SectionHeader("GESPEICHERTE ANSICHTEN") }
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedViews.size) { i ->
                        val v = savedViews[i]
                        val active = v.segment == segment &&
                                v.zeitraum == zeitraum.label && v.status == statusFilter
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectChip(v.name, active, onClick = {
                                segment = v.segment
                                zeitraum = Zeitraum.fromLabel(v.zeitraum)
                                statusFilter = v.status
                            }, accent = ThemeSecondary)
                            IconButton(
                                onClick = { persistViews(savedViews - v) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Löschen",
                                    tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // ================= SEGMENT: FOKUS =================
        if (segment == 0) {
            val listContacts = contacts.filter {
                it.isHotBox && viewModel.getEffectiveHotBoxListName(it.hotBoxListName) in selectedHotBoxListNames
            }
            val open = listContacts.filter { !it.hasBeenCalledInHotCycle }
            val eligibleNow = open.filter { it.isReachableNow() }
            val allSorted = open.sortedWith(
                compareByDescending<ContactEntity> { it.isReachableNow() }
                    .thenBy { it.lastCallAt ?: 0L }
            ) + listContacts.filter { it.hasBeenCalledInHotCycle }

            val sorted = if (searchQuery.isBlank()) {
                allSorted
            } else {
                allSorted.filter {
                    val cleanQuery = searchQuery.replace("[^\\d]".toRegex(), "")
                    val cleanPhone = it.phone.replace("[^\\d]".toRegex(), "")
                    val matchesPhone = if (cleanQuery.isNotEmpty()) {
                        cleanPhone.contains(cleanQuery) || it.phone.contains(searchQuery)
                    } else {
                        it.phone.contains(searchQuery)
                    }
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    matchesPhone ||
                    (it.company ?: "").contains(searchQuery, ignoreCase = true) ||
                    (it.callReason ?: "").contains(searchQuery, ignoreCase = true)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val lists = hotBoxLists.toList()
                        items(lists) { opt ->
                            val isSelected = selectedHotBoxListNames.contains(opt)
                            val cfg = com.example.ui.theme.LocalThemeConfig.current
                            val bg by animateColorAsState(
                                if (isSelected) ThemeAccent.copy(alpha = 0.20f) else SlateHigh.copy(alpha = 0.55f),
                                tween(200), label = "chipBg"
                            )
                            val border by animateColorAsState(
                                if (isSelected) ThemeAccent.copy(alpha = 0.7f) else BorderSubtle,
                                tween(200), label = "chipBorder"
                            )
                            Box(
                                modifier = Modifier
                                    .pulsingAura(cfg.auraColor, enabled = isSelected, maxRadiusFactor = 0.55f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bg)
                                    .border(1.dp, border, RoundedCornerShape(20.dp))
                                    .combinedClickable(
                                        onClick = { viewModel.toggleHotBoxListSelection(opt) },
                                        onLongClick = { viewModel.selectHotBoxList(opt) }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = opt,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) ThemeAccent else TextSecondary,
                                        maxLines = 1
                                    )
                                    // Custom lists can be deleted (except protected ones)
                                    if (opt != "No wave") {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Liste löschen",
                                            tint = if (isSelected) ThemeAccent else TextMuted,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    listToDeleteConfirm = opt
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Add new list button
                    IconButton(
                        onClick = { showCreateListDialogInLeads = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(SlateHigh.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Neue Liste",
                            tint = ThemeAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            item {
                // HOTBOX-KOMMANDOZENTRALE
                // Die große Feuer-Aura um die komplette Karte wurde entfernt.
                // Feuer bleibt ausschließlich am orangefarbenen Hotbox-Button (fire = true).
                AppCard(
                    modifier = Modifier.padding(top = 26.dp, bottom = 18.dp),
                    accent = ThemeAccent
                ) {
                    Column(Modifier.padding(14.dp)) {
                        val nextContact = open.firstOrNull { it.id == nextHotBoxId } ?: open.firstOrNull()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${listContacts.size - open.size} / ${listContacts.size} im Zyklus erledigt",
                                    style = MaterialTheme.typography.titleMedium, color = TextPrimary
                                )
                                Text(
                                    "${eligibleNow.size} jetzt erreichbar",
                                    style = MaterialTheme.typography.bodySmall, color = TextMuted
                                )
                                if (nextContact != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Als nächstes: ${nextContact.name}",
                                        style = MaterialTheme.typography.bodyMedium, color = ThemeAccent,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(onClick = {
                                viewModel.resetCurrentHotBoxCycle()
                                Toast.makeText(context, "Zyklus zurückgesetzt", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Refresh, "Zyklus zurücksetzen", tint = TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        
                        val autoActive = isAutoCallActive
                        val autoPaused = isAutoCallPaused
                        val countdown = autoCallCountdown

                        val btnText = when {
                            countdown != null -> "Hotbox ruft an in $countdown s..."
                            autoActive && !autoPaused -> "Hotbox läuft..."
                            autoActive && autoPaused -> "Hotbox pausiert"
                            else -> "Hotbox starten"
                        }

                        PrimaryButton(
                            text = btnText,
                            icon = if (autoActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = listContacts.isNotEmpty(),
                            pulsing = true,
                            onLongClick = {
                                val numberRegex = Regex("\\d+")
                                val targetContact = contacts.find { it.id == nextHotBoxId } ?: contacts.firstOrNull { it.isHotBox }
                                if (targetContact != null) {
                                    val digitsOnly = numberRegex.find(targetContact.name)?.value 
                                        ?: numberRegex.find(targetContact.company ?: "")?.value 
                                        ?: ""
                                    if (digitsOnly.isNotEmpty()) {
                                        try {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Kundennummer", digitsOnly))
                                            Toast.makeText(context, "Kundennummer $digitsOnly kopiert! 📋", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ⚠️", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Kein Hotbox-Kontakt vorhanden! ⚠️", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onClick = {
                                if (autoActive) {
                                    viewModel.setAutoCallActive(false)
                                    Toast.makeText(context, "Hotbox gestoppt", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setAutoCallActive(true)
                                    Toast.makeText(context, "Hotbox gestartet", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Fokusliste durchsuchen...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ThemeAccent, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Leeren", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThemeAccent,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SlateHigh.copy(alpha = 0.3f),
                        unfocusedContainerColor = SlateHigh.copy(alpha = 0.3f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedPlaceholderColor = TextMuted,
                        unfocusedPlaceholderColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }
            item { SectionHeader("PRIORISIERTE LISTE · ${sorted.size}") }
            if (sorted.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.Star, "Fokusliste ist leer",
                        "Markiere Kontakte mit dem Stern, um sie hier zu priorisieren."
                    )
                }
            } else {
                items(sorted.size) { i ->
                    val c = sorted[i]
                    val done = c.hasBeenCalledInHotCycle
                    PersonRow(
                        title = c.name,
                        subtitle = listOfNotNull(c.company, c.callReason)
                            .joinToString(" · ").ifBlank { c.phone },
                        leadingIcon = Icons.Default.Star,
                        leadingTint = if (done) TextMuted else ThemeAccent,
                        onClick = { onOpenContact(c) },
                        onLongClick = {
                            val nameDigits = Regex("\\d+").find(c.name)?.value ?: ""
                            val digits = if (nameDigits.isNotEmpty()) nameDigits else {
                                Regex("\\d+").find(c.phone)?.value ?: ""
                            }
                            if (digits.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Kundennummer", digits)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kundennummer $digits kopiert! 📋", Toast.LENGTH_SHORT).show()
                            }
                        },
                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                val isNext = c.id == nextHotBoxId
                                if (isNext) {
                                    StatusBadge("ALS NÄCHSTES", BadgeTone.Warn)
                                }
                                when {
                                    done -> StatusBadge("Zyklus erledigt", BadgeTone.Neutral)
                                    c.isReachableNow() -> StatusBadge("Jetzt erreichbar", BadgeTone.Success)
                                    else -> StatusBadge("Außerhalb Zeitfenster", BadgeTone.Neutral)
                                }
                                val last = c.lastCallAt
                                if (last == null) StatusBadge("Nie kontaktiert", BadgeTone.Warn)
                                else StatusBadge("Vor ${daysAgo(last)} T.", BadgeTone.Info)
                                
                                val logs = callLogs.filter { it.phone == c.phone }
                                val reached = logs.count { it.outcome.equals("Erreicht", ignoreCase = true) }
                                val notReached = logs.size - reached
                                if (logs.isNotEmpty()) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.background(Color.White.copy(alpha=0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White)
                                        Text("$reached/$notReached", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        },
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { viewModel.toggleHotBox(c.id) },
                                    modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Close, "Aus Fokus entfernen",
                                        tint = TextMuted, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = {
                                    viewModel.initiateCall(c.phone, c.name, c.id)
                                }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Phone, "Anrufen",
                                        tint = ThemeAccent, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = {
                                    contactToEditInLeads = c
                                }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Edit, "Bearbeiten",
                                        tint = ThemeSecondary, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = {
                                    contactToDeleteConfirm = c
                                }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Delete, "Kontakt löschen",
                                        tint = Color(0xFFEF4444), modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    )
                }
            }
        }

        // ================= SEGMENT: NEUKUNDEN =================
        if (segment == 1) {
            item {
                ChipRow(
                    options = Zeitraum.entries.map { it.label },
                    selected = zeitraum.label,
                    onSelect = { zeitraum = Zeitraum.fromLabel(it) },
                    accent = ThemeSecondary
                )
            }
            item {
                ChipRow(
                    options = neukundenStatusOptions,
                    selected = statusFilter,
                    onSelect = { statusFilter = it },
                    accent = ThemeAccent
                )
            }
            val filtered = neukunden
                .filter { zeitraum.matches(it.dateCreated) }
                .filter { statusFilter == "Alle" || it.status == statusFilter }
                .sortedByDescending { it.dateCreated }
            item { 
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionHeader("NEUKUNDEN · ${filtered.size}") 
                    PrimaryButton(
                        text = "Neuer Lead 👤+",
                        onClick = onAddNeukunde,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.PersonAdd
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.PersonAdd, "Keine Neukunden im Zeitraum",
                        "Passe Zeitraum oder Status an – oder lege einen neuen Lead an.",
                        actionLabel = "Neuer Lead", onAction = onAddNeukunde
                    )
                }
            } else {
                items(filtered.size) { i ->
                    val n = filtered[i]
                    val statusTone = when (n.status) {
                        "Anrufen" -> BadgeTone.Info
                        "Datenmail schreiben" -> BadgeTone.Warn
                        "Angebot erstellen" -> BadgeTone.Success
                        else -> BadgeTone.Neutral
                    }
                    val displayName = if (!n.customerName.isNullOrBlank()) {
                        if (!n.company.isNullOrBlank()) "${n.customerName} (${n.company})" else n.customerName
                    } else if (!n.company.isNullOrBlank()) {
                        n.company
                    } else {
                        "Kd.-Nr. ${n.customerNumber}"
                    }

                    val detailsText = buildString {
                        append(n.phone)
                        if (!n.deliveryAddress.isNullOrBlank()) {
                            append(" | 📍 ")
                            append(n.deliveryAddress)
                        }
                        if (n.consumption != null) {
                            append(" | 📊 ")
                            val decimalFormat = java.text.NumberFormat.getIntegerInstance(java.util.Locale.GERMANY)
                            append(decimalFormat.format(n.consumption))
                            append(" kWh")
                            if (!n.energyType.isNullOrBlank()) {
                                append(" (")
                                append(n.energyType)
                                append(")")
                            }
                        }
                    }

                    PersonRow(
                        title = displayName,
                        subtitle = detailsText,
                        leadingIcon = Icons.Default.PersonAdd,
                        leadingTint = ThemeSecondary,
                        onClick = { 
                            onOpenContact(
                                ContactEntity(
                                    id = n.id,
                                    name = n.customerName ?: "Kunde ${n.customerNumber}",
                                    phone = n.phone,
                                    company = n.company,
                                    email = n.email,
                                    lastCallAt = n.dateCreated,
                                    lastOutcome = null,
                                    isHotBox = false
                                )
                            )
                        },
                        onLongClick = {
                            val match = Regex("\\d+").find(n.customerNumber)
                            val digits = match?.value ?: ""
                            if (digits.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Kundennummer", digits)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kundennummer $digits kopiert", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Keine Zahlen in Kundennummer gefunden", Toast.LENGTH_SHORT).show()
                            }
                        },
                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(n.status, statusTone)
                                StatusBadge("${n.callAttempts} Versuche", BadgeTone.Neutral)
                                StatusBadge("Vor ${daysAgo(n.dateCreated)} T.", BadgeTone.Neutral)
                            }
                        },
                        trailing = {
                            Row {
                                IconButton(onClick = {
                                    viewModel.incrementNeukundeCallAttempts(n)
                                    viewModel.initiateCall(n.phone, "Kd. ${n.customerNumber}", null)
                                }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Phone, "Anrufen",
                                        tint = ThemeAccent, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = {
                                    viewModel.advanceNeukundeStatus(n) {
                                        Toast.makeText(context, "Lead abgeschlossen", Toast.LENGTH_SHORT).show()
                                    }
                                }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Nächster Schritt",
                                        tint = ThemeSecondary, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    )
                }
            }
        }

        // ================= SEGMENT: HEISSE ANGEBOTE =================
        if (segment == 2) {
            item {
                ChipRow(
                    options = Zeitraum.entries.map { it.label },
                    selected = zeitraum.label,
                    onSelect = { zeitraum = Zeitraum.fromLabel(it) },
                    accent = ThemeSecondary
                )
            }
            val filtered = angebote
                .filter { zeitraum.matches(it.dateCreated) }
                .sortedBy { it.dateCreated } // Älteste zuerst = dringendste
            item { SectionHeader("OFFENE ANGEBOTE · ${filtered.size}") }
            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.LocalFireDepartment, "Keine offenen Angebote",
                        "Angebote erscheinen hier automatisch nach dem Gesprächsabschluss."
                    )
                }
            } else {
                items(filtered.size) { i ->
                    val a = filtered[i]
                    val age = daysAgo(a.dateCreated)
                    PersonRow(
                        title = "Kd.-Nr. ${a.customerNumber}",
                        subtitle = a.notes.ifBlank { a.phone },
                        leadingIcon = Icons.Default.LocalFireDepartment,
                        leadingTint = if (age > 3) WarnAmber else ThemeAccent,
                        onClick = {
                            onOpenContact(ContactEntity(id = a.id, name = "Angebot ${a.customerNumber}", phone = a.phone, company = null, email = null, lastCallAt = a.dateCreated, lastOutcome = null, isHotBox = false))
                        },
                        onLongClick = {
                            val match = Regex("\\d+").find(a.customerNumber)
                            val digits = match?.value ?: ""
                            if (digits.isNotEmpty()) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Kundennummer", digits)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kundennummer $digits kopiert", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Keine Zahlen in Kundennummer gefunden", Toast.LENGTH_SHORT).show()
                            }
                        },
                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(
                                    "Wartet seit $age T.",
                                    if (age > 3) BadgeTone.Warn else BadgeTone.Success
                                )
                                StatusBadge("${a.callAttempts} Versuche", BadgeTone.Neutral)
                            }
                        },
                        trailing = {
                            Row {
                                IconButton(onClick = { viewModel.deleteHeissAngebot(a.id) },
                                    modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.PlaylistAddCheck, "Erledigt",
                                        tint = TextMuted, modifier = Modifier.size(19.dp))
                                }
                                IconButton(onClick = {
                                    viewModel.incrementHeissAngebotCallAttempts(a)
                                    viewModel.initiateCall(a.phone, "Kd. ${a.customerNumber}", null)
                                }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Phone, "Anrufen",
                                        tint = ThemeAccent, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    )
                }
            }
        }

        // ================= SEGMENT: KONTAKTE =================
        if (segment == 3) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Kunden & Kontakte suchen...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ThemeAccent, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Leeren", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ThemeAccent,
                        unfocusedBorderColor = BorderSubtle,
                        focusedContainerColor = SlateHigh.copy(alpha = 0.4f),
                        unfocusedContainerColor = SlateHigh.copy(alpha = 0.2f),
                        cursorColor = ThemeAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sortieren nach:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        ChipRow(
                            options = listOf("Anlagedatum", "Verbrauch", "Postleitzahl", "Name"),
                            selected = contactSortBy,
                            onSelect = { contactSortBy = it },
                            accent = ThemeSecondary
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Energieart filtern:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    ChipRow(
                        options = listOf("Alle", "Strom", "Gas", "Beide"),
                        selected = energyTypeFilter,
                        onSelect = { energyTypeFilter = it },
                        accent = ThemeSecondary
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionHeader("KONTAKTE · ${sortedContacts.size}")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrimaryButton(
                            text = "Kunde hinzufügen 👤+",
                            onClick = { showAddContactInLeads = true },
                            modifier = Modifier.weight(1.5f),
                            icon = Icons.Default.PersonAdd
                        )
                        SecondaryButton(
                            text = "Importieren 📥",
                            onClick = { showImportContactsInLeads = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Upload
                        )
                    }
                }
            }

            if (sortedContacts.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.Person, "Keine Kontakte gefunden",
                        "Lege einen neuen Kontakt an oder passe deine Suche an.",
                        actionLabel = "Kunde hinzufügen", onAction = { showAddContactInLeads = true }
                    )
                }
            } else {
                items(sortedContacts.size) { i ->
                    val c = sortedContacts[i]
                    val displayName = if (!c.company.isNullOrBlank()) "${c.name} (${c.company})" else c.name
                    val detailsText = buildString {
                        append(c.phone)
                        if (!c.email.isNullOrBlank()) {
                            append(" | ✉️ ")
                            append(c.email)
                        }
                        if (!c.zipCode.isNullOrBlank()) {
                            append(" | 📍 PLZ: ")
                            append(c.zipCode)
                        }
                        if (c.consumption != null) {
                            append(" | 📊 ")
                            val decimalFormat = java.text.NumberFormat.getIntegerInstance(java.util.Locale.GERMANY)
                            append(decimalFormat.format(c.consumption))
                            append(" kWh")
                        }
                    }

                    PersonRow(
                        title = displayName,
                        subtitle = detailsText,
                        leadingIcon = if (c.isHotBox) Icons.Default.Star else Icons.Default.Person,
                        leadingTint = if (c.isHotBox) ThemeAccent else ThemeSecondary,
                        onClick = { onOpenContact(c) },
                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!c.energyType.isNullOrBlank()) {
                                    val tone = when (c.energyType) {
                                        "Strom" -> BadgeTone.Info
                                        "Gas" -> BadgeTone.Warn
                                        "Beide" -> BadgeTone.Success
                                        else -> BadgeTone.Neutral
                                    }
                                    StatusBadge(c.energyType, tone)
                                }
                                if (c.consumption != null) {
                                    StatusBadge("${c.consumption} kWh", BadgeTone.Info)
                                }
                                if (!c.zipCode.isNullOrBlank()) {
                                    StatusBadge("PLZ: ${c.zipCode}", BadgeTone.Success)
                                }
                                val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(c.dateCreated))
                                StatusBadge("Erstellt: $dateStr", BadgeTone.Neutral)
                            }
                        },
                        trailing = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var showListSelectorMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = {
                                            if (c.isHotBox) {
                                                viewModel.toggleHotBox(c.id)
                                                Toast.makeText(context, "${c.name} aus Hotbox entfernt", Toast.LENGTH_SHORT).show()
                                            } else {
                                                showListSelectorMenu = true
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (c.isHotBox) Icons.Default.Star else Icons.Default.StarOutline,
                                            contentDescription = "Hot Box umschalten",
                                            tint = if (c.isHotBox) ThemeAccent else TextMuted,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showListSelectorMenu,
                                        onDismissRequest = { showListSelectorMenu = false }
                                    ) {
                                        hotBoxLists.forEach { listName ->
                                            DropdownMenuItem(
                                                text = { Text(listName) },
                                                onClick = {
                                                    viewModel.addToHotBoxList(c.id, listName)
                                                    showListSelectorMenu = false
                                                    Toast.makeText(context, "${c.name} zu $listName hinzugefügt", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.initiateCall(c.phone, c.name, c.id)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Anrufen",
                                        tint = ThemeAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        contactToEditInLeads = c
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Bearbeiten",
                                        tint = ThemeSecondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // ---- Dialog: Ansicht speichern ----
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Ansicht speichern") },
            text = {
                Column {
                    Text(
                        "Speichert Segment, Zeitraum und Statusfilter als schnelle Ansicht.",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newViewName,
                        onValueChange = { newViewName = it },
                        placeholder = { Text("z. B. Neue Leads diese Woche") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newViewName.isNotBlank()) {
                        persistViews(
                            savedViews + SavedView(
                                newViewName.trim(), segment, zeitraum.label, statusFilter
                            )
                        )
                        newViewName = ""
                        showSaveDialog = false
                    }
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    listToDeleteConfirm?.let { listToDelete ->
        AlertDialog(
            onDismissRequest = { listToDeleteConfirm = null },
            title = { Text("Liste löschen?") },
            text = { Text("Möchtest du die Liste '$listToDelete' wirklich löschen? Alle zugeordneten Kontakte werden aus der Hotbox entfernt.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeHotBoxList(listToDelete)
                        listToDeleteConfirm = null
                    }
                ) {
                    Text("Löschen", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDeleteConfirm = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showCreateListDialogInLeads) {
        AlertDialog(
            onDismissRequest = {
                showCreateListDialogInLeads = false
                newListNameInLeads = ""
            },
            title = { Text("Neue Hotbox-Liste") },
            text = {
                Column {
                    Text("Gib den Namen für die neue Kampagnenliste ein:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newListNameInLeads,
                        onValueChange = { newListNameInLeads = it },
                        label = { Text("Listenname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newListNameInLeads.trim()
                        if (name.isNotEmpty()) {
                            viewModel.addHotBoxList(name)
                        }
                        showCreateListDialogInLeads = false
                        newListNameInLeads = ""
                    },
                    enabled = newListNameInLeads.trim().isNotEmpty()
                ) {
                    Text("Erstellen", color = ThemeAccent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateListDialogInLeads = false
                        newListNameInLeads = ""
                    }
                ) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (contactToDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { contactToDeleteConfirm = null },
            title = { Text("Kontakt löschen", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("Möchtest du '${contactToDeleteConfirm?.name}' wirklich dauerhaft aus der App löschen?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactToDeleteConfirm?.id?.let { id ->
                            viewModel.deleteContact(id)
                        }
                        contactToDeleteConfirm = null
                    }
                ) {
                    Text("Löschen", color = Color(0xFFEF4444), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDeleteConfirm = null }) {
                    Text("Abbrechen", color = TextSecondary)
                }
            }
        )
    }

    contactToEditInLeads?.let { contact ->
        com.example.ContactDialog(
            title = "Kontakt bearbeiten",
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
            onDismiss = { contactToEditInLeads = null },
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
                contactToEditInLeads = null
            }
        )
    }

    if (showAddContactInLeads) {
        com.example.ContactDialog(
            title = "Neuer Kontakt",
            hotBoxLists = hotBoxLists,
            initialHotBoxListName = viewModel.selectedHotBoxListName.value,
            onDismiss = { showAddContactInLeads = false },
            onConfirm = { name, phone, company, email, isHotBox, startHour, endHour, weekdays, consumption, zipCode, energyType, hotBoxListName ->
                viewModel.addManualContact(
                    name = name,
                    phone = phone,
                    company = company,
                    email = email,
                    isHotBox = isHotBox,
                    hotBoxStartHour = startHour,
                    hotBoxEndHour = endHour,
                    hotBoxWeekdays = weekdays,
                    hotBoxListName = hotBoxListName,
                    consumption = consumption,
                    zipCode = zipCode,
                    energyType = energyType
                )
                showAddContactInLeads = false
            }
        )
    }

    if (showImportContactsInLeads) {
        com.example.SystemContactImportDialog(
            viewModel = viewModel,
            onDismiss = { showImportContactsInLeads = false }
        )
    }
}
