package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.database.FollowUpEntity
import com.example.ui.design.AppCard
import com.example.ui.design.BadgeTone
import com.example.ui.design.StatusBadge
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThemeAccent
import com.example.ui.theme.ThemeSecondary
import com.example.util.SupabaseDbClient
import com.example.viewmodel.StromrufViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

/**
 * Activity no longer gets a permanent floating calendar card. The calendar is a real
 * sub-tab inside AktivitaetenScreen and this host only keeps the existing shell API stable.
 */
@Composable
fun AktivitaetenCalendarHost(
    viewModel: StromrufViewModel,
    onAddFollowUp: () -> Unit
) {
    AktivitaetenScreen(
        viewModel = viewModel,
        onAddFollowUp = onAddFollowUp
    )
}

/** Full calendar content rendered directly in the Aktivitaet sub-tab. */
@Composable
fun ActivityCalendarContent(
    viewModel: StromrufViewModel,
    onAddFollowUp: () -> Unit
) {
    val localFollowUps by viewModel.activeFollowUps.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var remoteFollowUps by remember { mutableStateOf<List<FollowUpEntity>>(emptyList()) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncAt by remember { mutableStateOf<Long?>(null) }
    var monthAnchor by remember { mutableStateOf(firstDayOfMonth(System.currentTimeMillis())) }
    var selectedDay by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }

    suspend fun refreshFromSupabase() {
        isSyncing = true
        remoteFollowUps = runCatching { SupabaseDbClient.fetchFollowUps(context) }.getOrDefault(remoteFollowUps)
        lastSyncAt = System.currentTimeMillis()
        isSyncing = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshFromSupabase()
            delay(15_000)
        }
    }

    val events = remember(localFollowUps, remoteFollowUps) {
        val merged = LinkedHashMap<String, FollowUpEntity>()
        remoteFollowUps.forEach { merged[it.id] = it }
        localFollowUps.forEach { merged[it.id] = it }
        merged.values.sortedBy { it.dueAt }
    }
    val localIds = remember(localFollowUps) { localFollowUps.mapTo(hashSetOf()) { it.id } }
    val cells = remember(monthAnchor) { monthCells(monthAnchor) }
    val selectedEvents = remember(events, selectedDay) { eventsForDay(events, selectedDay).sortedBy { it.dueAt } }

    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.GERMAN) }
    val selectedFmt = remember { SimpleDateFormat("EEEE, dd. MMMM", Locale.GERMAN) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.GERMAN) }
    val syncFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMAN) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Kalender", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    if (lastSyncAt == null) "Supabase wird verbunden"
                    else "Supabase synchron · ${syncFmt.format(Date(lastSyncAt))}",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = { scope.launch { refreshFromSupabase() } }) {
                Icon(Icons.Default.Refresh, "Aktualisieren", tint = if (isSyncing) ThemeAccent else TextSecondary)
            }
            FilledTonalIconButton(onClick = onAddFollowUp) {
                Icon(Icons.Default.Add, "Termin hinzufügen")
            }
        }

        AppCard(accent = ThemeSecondary) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        monthAnchor = shiftMonth(monthAnchor, -1)
                        selectedDay = monthAnchor
                    }) { Icon(Icons.Default.ChevronLeft, "Vorheriger Monat", tint = TextSecondary) }

                    Text(
                        monthFmt.format(Date(monthAnchor)).replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString()
                        },
                        modifier = Modifier.weight(1f),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = {
                        monthAnchor = firstDayOfMonth(System.currentTimeMillis())
                        selectedDay = startOfDay(System.currentTimeMillis())
                    }) { Text("Heute") }

                    IconButton(onClick = {
                        monthAnchor = shiftMonth(monthAnchor, 1)
                        selectedDay = monthAnchor
                    }) { Icon(Icons.Default.ChevronRight, "Nächster Monat", tint = TextSecondary) }
                }

                WeekdayHeader()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            MonthDayCell(
                                dayStart = day,
                                selected = day != null && sameDay(day, selectedDay),
                                today = day != null && sameDay(day, System.currentTimeMillis()),
                                dayEvents = if (day == null) emptyList() else eventsForDay(events, day),
                                onSelect = { selectedDay = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selectedFmt.format(Date(selectedDay)).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString()
                    },
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${selectedEvents.size} Termin${if (selectedEvents.size == 1) "" else "e"}",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (selectedEvents.any { !it.isCompleted }) {
                StatusBadge("${selectedEvents.count { !it.isCompleted }} offen", BadgeTone.Info)
            }
        }

        if (selectedEvents.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CalendarMonth, null, tint = TextMuted, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Keine Termine an diesem Tag", color = TextSecondary)
                    TextButton(onClick = onAddFollowUp) { Text("Termin anlegen") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(selectedEvents, key = { it.id }) { event ->
                    CalendarEventCard(
                        event = event,
                        timeText = timeFmt.format(Date(event.dueAt)),
                        isLocal = event.id in localIds,
                        onCall = {
                            if (event.contactPhone.isNotBlank() && event.contactPhone != "-") {
                                viewModel.initiateCall(event.contactPhone, event.contactName, event.contactId)
                            }
                        },
                        onComplete = { viewModel.completeFollowUp(event.id) },
                        onDelete = { viewModel.deleteFollowUp(event.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { name ->
            Text(
                name,
                modifier = Modifier.weight(1f),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun MonthDayCell(
    dayStart: Long?,
    selected: Boolean,
    today: Boolean,
    dayEvents: List<FollowUpEntity>,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (dayStart == null) {
        Box(modifier.height(42.dp))
        return
    }
    val day = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_MONTH)
    val background = when {
        selected -> ThemeSecondary.copy(alpha = 0.22f)
        today -> ThemeAccent.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Surface(
        onClick = { onSelect(dayStart) },
        modifier = modifier.padding(1.dp).height(42.dp),
        shape = RoundedCornerShape(10.dp),
        color = background
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "$day",
                color = if (selected) ThemeSecondary else TextPrimary,
                fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal
            )
            if (dayEvents.isNotEmpty()) {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp)
                        .clip(CircleShape)
                        .background(if (dayEvents.any { !it.isCompleted }) ThemeAccent else TextMuted)
                )
            }
        }
    }
}

@Composable
private fun CalendarEventCard(
    event: FollowUpEntity,
    timeText: String,
    isLocal: Boolean,
    onCall: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    var showFullNote by remember(event.id) { mutableStateOf(false) }
    val noteText = event.note?.takeIf { it.isNotBlank() }
        ?: event.callReason?.takeIf { it.isNotBlank() }
        ?: event.contactPhone

    AppCard(accent = if (event.isCompleted) null else ThemeAccent) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeText, color = if (event.isCompleted) TextMuted else ThemeAccent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    event.contactName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(if (event.isCompleted) "Erledigt" else "Offen", if (event.isCompleted) BadgeTone.Neutral else BadgeTone.Info)
            }

            if (noteText.isNotBlank()) {
                Text(
                    noteText,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = { showFullNote = true }, contentPadding = PaddingValues(0.dp)) {
                    Text("Notiz vollständig anzeigen")
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!event.isCompleted && event.contactPhone.isNotBlank() && event.contactPhone != "-") {
                    IconButton(onClick = onCall) { Icon(Icons.Default.Phone, "Anrufen", tint = ThemeAccent) }
                }
                if (!event.isCompleted && isLocal) {
                    IconButton(onClick = onComplete) { Icon(Icons.Default.Check, "Erledigt", tint = ThemeSecondary) }
                }
                if (isLocal) {
                    TextButton(onClick = onDelete) { Text("Löschen", color = TextMuted) }
                } else {
                    StatusBadge("Supabase", BadgeTone.Neutral)
                }
            }
        }
    }

    if (showFullNote) {
        AlertDialog(
            onDismissRequest = { showFullNote = false },
            title = { Text(event.contactName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$timeText Uhr", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                    Text(noteText, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showFullNote = false }) { Text("Schließen") } }
        )
    }
}

private fun firstDayOfMonth(timeMillis: Long): Long = Calendar.getInstance().apply {
    this.timeInMillis = timeMillis
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().apply {
    this.timeInMillis = timeMillis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun shiftMonth(anchor: Long, amount: Int): Long = Calendar.getInstance().apply {
    timeInMillis = anchor
    set(Calendar.DAY_OF_MONTH, 1)
    add(Calendar.MONTH, amount)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun monthCells(anchor: Long): List<Long?> {
    val first = Calendar.getInstance().apply {
        timeInMillis = anchor
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val leading = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val result = MutableList<Long?>(leading) { null }
    for (day in 1..first.getActualMaximum(Calendar.DAY_OF_MONTH)) {
        val cell = first.clone() as Calendar
        cell.set(Calendar.DAY_OF_MONTH, day)
        result += cell.timeInMillis
    }
    while (result.size % 7 != 0) result += null
    return result
}

private fun eventsForDay(events: List<FollowUpEntity>, dayStart: Long): List<FollowUpEntity> {
    val start = startOfDay(dayStart)
    val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    return events.filter { it.dueAt >= start && it.dueAt < end }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
