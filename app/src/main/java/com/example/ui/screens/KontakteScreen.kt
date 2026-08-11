package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.util.TelegramClient
import kotlinx.coroutines.launch
import com.example.database.ContactEntity
import com.example.ui.design.AppCard
import com.example.ui.design.BadgeTone
import com.example.ui.design.Dim
import com.example.ui.design.EmptyState
import com.example.ui.design.IconDisc
import com.example.ui.design.PersonRow
import com.example.ui.design.PrimaryButton
import com.example.ui.design.SecondaryButton
import com.example.ui.design.SectionHeader
import com.example.ui.design.StatusBadge
import com.example.ui.design.TimelineEntry
import com.example.ui.theme.ThemeSecondary
import com.example.ui.theme.ThemeAccent
import com.example.ui.theme.SlateHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarnAmber
import com.example.viewmodel.StromrufViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// KONTAKTE – Liste mit Suche + vollwertige Kunden-Detailseite
// mit gemeinsamer Aktivitäts-Timeline (Anrufe + Wiedervorlagen).
// ============================================================

private fun phoneMatches(a: String, b: String): Boolean {
    val da = a.filter { it.isDigit() }
    val db = b.filter { it.isDigit() }
    if (da.isEmpty() || db.isEmpty()) return false
    val tail = minOf(da.length, db.length, 8)
    return da.takeLast(tail) == db.takeLast(tail)
}

private fun outcomeTone(outcome: String?): BadgeTone = when {
    outcome == null -> BadgeTone.Neutral
    outcome.contains("Erreicht", true) && !outcome.contains("Nicht", true) -> BadgeTone.Success
    outcome.contains("Nicht", true) -> BadgeTone.Neutral
    outcome.contains("Angebot", true) -> BadgeTone.Info
    outcome.contains("Abschluss", true) -> BadgeTone.Success
    else -> BadgeTone.Neutral
}

@Composable
fun KontakteScreen(
    viewModel: StromrufViewModel,
    selectedContact: ContactEntity?,
    onSelectContact: (ContactEntity?) -> Unit,
    onAddContact: () -> Unit,
    onImportContacts: () -> Unit,
    onEditContact: (ContactEntity) -> Unit,
    onRequestFollowUp: (ContactEntity) -> Unit
) {
    val contacts by viewModel.contacts.collectAsState()
    val hotBoxLists by viewModel.hotBoxLists.collectAsState()
    val context = LocalContext.current

    // Detailansicht hat Vorrang – volle Fläche, Fokus-Prinzip.
    val current = selectedContact?.let { sel -> contacts.firstOrNull { it.id == sel.id } ?: sel }
    if (current != null) {
        KontaktDetail(
            contact = current,
            viewModel = viewModel,
            onBack = { onSelectContact(null) },
            onEdit = { onEditContact(current) },
            onRequestFollowUp = { onRequestFollowUp(current) }
        )
        return
    }

    var query by remember { mutableStateOf("") }
    val filtered = remember(contacts, query) {
        val q = query.trim()
        if (q.isBlank()) contacts.sortedBy { it.name.lowercase() }
        else contacts.filter {
            it.name.contains(q, true) ||
                    it.phone.contains(q) ||
                    (it.company?.contains(q, true) == true)
        }.sortedBy { it.name.lowercase() }
    }
    val grouped = remember(filtered) {
        filtered.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }.toSortedMap()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dim.screenPad, end = Dim.screenPad, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Kontakte", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                    Text("${contacts.size} gesamt", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                IconButton(onClick = onImportContacts) {
                    Icon(Icons.Default.Upload, "Importieren", tint = TextSecondary)
                }
                IconButton(onClick = onAddContact) {
                    Icon(Icons.Default.Person, "Neuer Kontakt", tint = ThemeSecondary)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name, Firma oder Nummer suchen", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                singleLine = true
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.Person,
                    if (query.isBlank()) "Noch keine Kontakte" else "Keine Treffer",
                    if (query.isBlank()) "Lege den ersten Kontakt an oder importiere bestehende."
                    else "Suche anpassen oder neuen Kontakt anlegen.",
                    actionLabel = "Neuer Kontakt", onAction = onAddContact
                )
            }
        } else {
            grouped.forEach { (letter, group) ->
                item(key = "hdr_$letter") {
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = ThemeAccent,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
                items(group.size, key = { i -> group[i].id }) { i ->
                    val c = group[i]
                    PersonRow(
                        title = c.name,
                        subtitle = listOfNotNull(c.company?.takeIf { it.isNotBlank() }, c.phone)
                            .joinToString(" · "),
                        leadingIcon = if (c.isHotBox) Icons.Default.Star else Icons.Default.Person,
                        leadingTint = if (c.isHotBox) ThemeAccent else ThemeSecondary,
                        onClick = { onSelectContact(c) },
                        badge = c.lastOutcome?.let {
                            { StatusBadge(it, outcomeTone(it)) }
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
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (c.isHotBox) Icons.Default.Star else Icons.Default.StarOutline,
                                            contentDescription = "Hot Box umschalten",
                                            tint = if (c.isHotBox) ThemeAccent else TextMuted,
                                            modifier = Modifier.size(19.dp)
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

                                IconButton(onClick = {
                                    viewModel.initiateCall(c.phone, c.name, c.id)
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
    }
}

// ---------------- Kunden-Detailseite ----------------

@Composable
private fun KontaktDetail(
    contact: ContactEntity,
    viewModel: StromrufViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRequestFollowUp: () -> Unit
) {
    val callLogs by viewModel.callLogs.collectAsState()
    val followUps by viewModel.activeFollowUps.collectAsState()

    val myCalls = remember(callLogs, contact.phone) {
        callLogs.filter { phoneMatches(it.phone, contact.phone) }
            .sortedByDescending { it.timestamp }
    }
    val myFollowUps = remember(followUps, contact) {
        followUps.filter {
            it.contactId == contact.id || phoneMatches(it.contactPhone, contact.phone)
        }.sortedBy { it.dueAt }
    }
    val nextStep = myFollowUps.firstOrNull()
    val now = System.currentTimeMillis()

    val dateFmt = remember { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN) }
    val dayFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dim.screenPad, end = Dim.screenPad, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Dim.gap)
    ) {
        // ---- Top-Leiste ----
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.toggleHotBox(contact.id) }) {
                    Icon(
                        if (contact.isHotBox) Icons.Default.Star else Icons.Default.StarOutline,
                        "Fokus", tint = if (contact.isHotBox) ThemeAccent else TextSecondary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "Bearbeiten", tint = TextSecondary)
                }
            }
        }

        // ---- Kopf: Wer + Wo stehen wir ----
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SlateHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            contact.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = ThemeAccent
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            contact.name, style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        val sub = listOfNotNull(
                            contact.company?.takeIf { it.isNotBlank() },
                            contact.callReason?.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(sub, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    contact.lastOutcome?.let { StatusBadge(it, outcomeTone(it)) }
                    contact.lastCallAt?.let {
                        StatusBadge("Letzter Kontakt ${dayFmt.format(Date(it))}", BadgeTone.Neutral)
                    } ?: StatusBadge("Noch nie kontaktiert", BadgeTone.Warn)
                    if (contact.isHotBox) StatusBadge("Fokusliste", BadgeTone.Success)
                }
            }
        }

        // ---- Hauptaktionen ----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    "Anrufen", icon = Icons.Default.Phone,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.initiateCall(contact.phone, contact.name, contact.id) }
                )
                SecondaryButton(
                    "Wiedervorlage", icon = Icons.Default.Notifications,
                    modifier = Modifier.weight(1f),
                    onClick = onRequestFollowUp
                )
            }
        }

        // ---- Notiz an Telegram ----
        item { SectionHeader("NOTIZ AN TELEGRAM") }
        item {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val telegram = remember { TelegramClient(context) }
            var noteText by remember(contact.id) { mutableStateOf("") }
            var sending by remember { mutableStateOf(false) }

            AppCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Notiz zu ${contact.name}…") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 6
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (telegram.isConfigured()) "Wird an deinen Telegram-Bot gesendet"
                            else "Telegram in den Einstellungen verknüpfen",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            if (sending) "Sendet…" else "Senden",
                            icon = Icons.AutoMirrored.Filled.Send,
                            enabled = !sending && noteText.isNotBlank() && telegram.isConfigured(),
                            onClick = {
                                scope.launch {
                                    sending = true
                                    val result = telegram.sendCustomerNoteDetailed(
                                        contactName = contact.name,
                                        company = contact.company,
                                        phone = contact.phone,
                                        note = noteText
                                    )
                                    sending = false
                                    // Supabase synchron halten (customer_messages inkl. Telegram-Status)
                                    val payload = org.json.JSONObject().apply {
                                        put("id", java.util.UUID.randomUUID().toString())
                                        put("contact_id", contact.id)
                                        put("contact_name", contact.name)
                                        put("contact_phone", contact.phone)
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
                                        Toast.makeText(context, "Notiz an Telegram gesendet ✓", Toast.LENGTH_SHORT).show()
                                        noteText = ""
                                    } else {
                                        Toast.makeText(context, "Senden fehlgeschlagen: ${result.detail}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // ---- Nächster Schritt ----
        item { SectionHeader("NÄCHSTER SCHRITT") }
        item {
            if (nextStep != null) {
                val overdue = nextStep.dueAt <= now
                AppCard(accent = if (overdue) WarnAmber else ThemeSecondary) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconDisc(Icons.Default.Notifications, if (overdue) WarnAmber else ThemeSecondary, size = 36)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                nextStep.note ?: "Wiedervorlage",
                                style = MaterialTheme.typography.titleMedium, color = TextPrimary
                            )
                            Text(
                                (if (overdue) "Fällig seit " else "Fällig am ") +
                                        dateFmt.format(Date(nextStep.dueAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overdue) WarnAmber else TextSecondary
                            )
                        }
                        IconButton(onClick = { viewModel.completeFollowUp(nextStep.id) }) {
                            Icon(Icons.Default.Check, "Erledigt", tint = ThemeAccent)
                        }
                    }
                }
            } else {
                AppCard {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Kein nächster Schritt geplant.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted, modifier = Modifier.weight(1f)
                        )
                        SecondaryButton("Planen", onClick = onRequestFollowUp, tint = ThemeSecondary)
                    }
                }
            }
        }

        // ---- Timeline ----
        item { SectionHeader("AKTIVITÄTEN · ${myCalls.size + myFollowUps.size}") }
        if (myCalls.isEmpty() && myFollowUps.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.Phone, "Noch keine Aktivitäten",
                    "Anrufe und Wiedervorlagen erscheinen hier automatisch."
                )
            }
        } else {
            item {
                AppCard {
                    Column(Modifier.padding(14.dp)) {
                        val planned = myFollowUps
                        val historyEntries = myCalls.take(20)
                        val total = planned.size + historyEntries.size
                        var idx = 0
                        planned.forEach { fu ->
                            idx++
                            TimelineEntry(
                                icon = Icons.Default.Notifications,
                                tint = if (fu.dueAt <= now) WarnAmber else ThemeSecondary,
                                title = fu.note ?: "Wiedervorlage geplant",
                                subtitle = null,
                                meta = dateFmt.format(Date(fu.dueAt)),
                                isLast = idx == total
                            )
                        }
                        historyEntries.forEach { log ->
                            idx++
                            val tone = outcomeTone(log.outcome)
                            TimelineEntry(
                                icon = Icons.Default.Phone,
                                tint = when (tone) {
                                    BadgeTone.Success -> ThemeAccent
                                    BadgeTone.Info -> ThemeSecondary
                                    else -> TextMuted
                                },
                                title = log.outcome,
                                subtitle = log.note?.takeIf { it.isNotBlank() },
                                meta = dateFmt.format(Date(log.timestamp)),
                                isLast = idx == total
                            )
                        }
                    }
                }
            }
        }

        // ---- Stammdaten ----
        item { SectionHeader("STAMMDATEN") }
        item {
            AppCard {
                Column(Modifier.padding(vertical = 6.dp)) {
                    DetailRow(Icons.Default.Phone, "Telefon", contact.phone)
                    DetailRow(Icons.Default.Email, "E-Mail", contact.email ?: "–")
                    DetailRow(Icons.Default.Work, "Firma", contact.company ?: "–")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
            modifier = Modifier.width(70.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
