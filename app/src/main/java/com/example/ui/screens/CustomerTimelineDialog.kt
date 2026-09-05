package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.database.CallLogEntity
import com.example.util.SupabaseDbClient
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class CustomerNoteEntry(
    val id: String,
    val contactId: String?,
    val contactName: String?,
    val phone: String,
    val note: String,
    val source: String,
    val occurredAt: Long
)

private data class CustomerTimelineEntry(
    val key: String,
    val occurredAt: Long,
    val isCall: Boolean,
    val title: String,
    val detail: String?,
    val durationSeconds: Long = 0
)

@Composable
fun CustomerTimelineDialog(
    contactId: String?,
    contactName: String,
    phone: String,
    callLogs: List<CallLogEntity>,
    source: String,
    onDismiss: () -> Unit,
    onEditContact: (() -> Unit)? = null,
    onAddFollowUp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notes by remember(contactId, phone) { mutableStateOf<List<CustomerNoteEntry>>(emptyList()) }
    var noteText by remember(contactId, phone) { mutableStateOf("") }
    var loading by remember(contactId, phone) { mutableStateOf(true) }
    var saving by remember(contactId, phone) { mutableStateOf(false) }
    var error by remember(contactId, phone) { mutableStateOf<String?>(null) }
    var refreshKey by remember(contactId, phone) { mutableIntStateOf(0) }

    LaunchedEffect(contactId, phone, refreshKey) {
        loading = true
        error = null
        runCatching {
            val rows = SupabaseDbClient.fetchTableRows(context, "customer_notes")
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val entry = CustomerNoteEntry(
                        id = row.getString("id"),
                        contactId = row.optNullableString("contact_id"),
                        contactName = row.optNullableString("contact_name"),
                        phone = row.optString("phone"),
                        note = row.optString("note"),
                        source = row.optString("source", "activity"),
                        occurredAt = row.optLong("occurred_at_ms", 0L)
                    )
                    if ((contactId != null && entry.contactId == contactId) ||
                        phoneNumbersMatch(entry.phone, phone)
                    ) {
                        add(entry)
                    }
                }
            }
        }.onSuccess {
            notes = it
        }.onFailure {
            error = "Notizen konnten nicht aus Supabase geladen werden."
        }
        loading = false
    }

    val matchingCalls = remember(callLogs, phone) {
        callLogs.filter { phoneNumbersMatch(it.phone, phone) }
    }
    val timeline = remember(matchingCalls, notes) {
        val callEntries = matchingCalls.map { call ->
            CustomerTimelineEntry(
                key = "call_${call.id}",
                occurredAt = call.timestamp,
                isCall = true,
                title = callOutcomeLabel(call.outcome),
                detail = call.note?.takeIf { it.isNotBlank() },
                durationSeconds = call.durationSeconds
            )
        }
        val noteEntries = notes.map { note ->
            CustomerTimelineEntry(
                key = "note_${note.id}",
                occurredAt = note.occurredAt,
                isCall = false,
                title = "Notiz",
                detail = note.note
            )
        }
        (callEntries + noteEntries).sortedByDescending { it.occurredAt }
    }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .widthIn(max = 640.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF172033)),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = contactName.ifBlank { phone.ifBlank { "Kunde" } },
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (phone.isNotBlank()) {
                            Text(phone, color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Schließen", color = Color(0xFF67E8F9))
                    }
                }

                if (onEditContact != null || onAddFollowUp != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onAddFollowUp?.let { action ->
                            OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) {
                                Text("Wiedervorlage", fontSize = 12.sp)
                            }
                        }
                        onEditContact?.let { action ->
                            OutlinedButton(onClick = action, modifier = Modifier.weight(1f)) {
                                Text("Kontakt", fontSize = 12.sp)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        noteText = it
                        error = null
                    },
                    label = { Text("Neue Kundennotiz") },
                    placeholder = { Text("z. B. Angebot besprochen, nächste Schritte …") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF22D3EE),
                        unfocusedBorderColor = Color(0xFF475569)
                    )
                )
                Button(
                    onClick = {
                        val trimmed = noteText.trim()
                        if (trimmed.isEmpty()) return@Button
                        saving = true
                        error = null
                        scope.launch {
                            val now = System.currentTimeMillis()
                            val payload = JSONObject().apply {
                                put("id", UUID.randomUUID().toString())
                                if (!contactId.isNullOrBlank()) put("contact_id", contactId)
                                if (contactName.isNotBlank()) put("contact_name", contactName)
                                put("phone", phone)
                                put("note", trimmed)
                                put("source", source)
                                put("occurred_at_ms", now)
                            }
                            val saved = SupabaseDbClient.upsertTableRow(
                                context,
                                "customer_notes",
                                payload
                            )
                            saving = false
                            if (saved) {
                                noteText = ""
                                refreshKey++
                            } else {
                                error = "Speichern in Supabase fehlgeschlagen."
                            }
                        }
                    },
                    enabled = noteText.isNotBlank() && !saving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE))
                ) {
                    Text(
                        if (saving) "Wird gespeichert …" else "Notiz mit Datum & Uhrzeit speichern",
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold
                    )
                }

                error?.let {
                    Text(it, color = Color(0xFFF87171), fontSize = 12.sp)
                }

                Text(
                    "VERLAUF · NEUESTE ZUERST",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF22D3EE))
                    }
                    timeline.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Noch keine Anrufe oder Notizen.", color = Color(0xFF94A3B8))
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(timeline, key = { it.key }) { entry ->
                            val isMissed = entry.isCall &&
                                entry.title.startsWith("Nicht erreicht", ignoreCase = true)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(
                                    1.dp,
                                    if (isMissed) Color(0xFFF59E0B) else Color(0xFF334155)
                                )
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (entry.isCall) Icons.Default.Phone else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isMissed) Color(0xFFF59E0B) else Color(0xFF22D3EE),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                entry.title,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (entry.isCall) {
                                                Text(
                                                    formatDuration(entry.durationSeconds),
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Text(
                                            dateFormat.format(Date(entry.occurredAt)),
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                        entry.detail?.let { detail ->
                                            Text(
                                                detail,
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 5.dp)
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
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    optString(name).takeIf { it.isNotBlank() && it != "null" }

private fun phoneNumbersMatch(first: String, second: String): Boolean {
    val a = first.filter(Char::isDigit)
    val b = second.filter(Char::isDigit)
    if (a.isEmpty() || b.isEmpty()) return false
    val length = minOf(a.length, b.length, 9)
    return if (length < 7) a == b else a.takeLast(length) == b.takeLast(length)
}

private fun callOutcomeLabel(outcome: String): String = when (outcome.lowercase(Locale.GERMAN)) {
    "erreicht_interesse" -> "Erreicht · Interesse"
    "erreicht_abschluss" -> "Erreicht · Abschluss"
    "erreicht_kein_interesse" -> "Erreicht · kein Interesse"
    "nicht_erreicht" -> "Nicht erreicht"
    "falsche_nummer" -> "Falsche Nummer"
    else -> outcome.replace("_", " ").replaceFirstChar { it.titlecase(Locale.GERMAN) }
}

private fun formatDuration(seconds: Long): String =
    String.format(Locale.GERMANY, "%02d:%02d", seconds / 60, seconds % 60)
