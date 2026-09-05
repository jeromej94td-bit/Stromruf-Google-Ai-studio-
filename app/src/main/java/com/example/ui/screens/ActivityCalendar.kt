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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 * Host fuer den Aktivitaeten-Bereich mit einem kleinen Kalender-Fenster.
 *
 * Der bestehende Aktivitaeten-Screen bleibt unangetastet. Das Kalenderfenster
 * liest zusaetzlich direkt aus Supabase, damit auch Termine sichtbar werden,
 * die ausserhalb dieses Geraets angelegt wurden. Ein Tap oeffnet den Kalender
 * als echten Vollbild-Dialog.
 */
@Composable
fun AktivitaetenCalendarHost(
    viewModel: StromrufViewModel,
    onAddFollowUp: () -> Unit
) {
    val localFollowUps by viewModel.activeFollowUps.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var remoteFollowUps by remember { mutableStateOf<List<FollowUpEntity>>(emptyList()) }
    var showFullCalendar by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var lastSyncAt by remember { mutableStateOf<Long?>(null) }

    suspend fun refreshFromSupabase() {
        isSyncing = true
        remoteFollowUps = SupabaseDbClient.fetchFollowUps(context)
        lastSyncAt = System.currentTimeMillis()
        isSyncing = false
    }

    // Solange der Aktivitaeten-Reiter sichtbar ist, Supabase regelmaessig lesen.
    // Das passt zum bereits vorhandenen Polling-Modell der App und benoetigt
    // weder eine neue Tabelle noch eine zusaetzliche Realtime-Verbindung.
    LaunchedEffect(Unit) {
        while (true) {
            refreshFromSupabase()
            delay(15_000)
        }
    }

    val mergedEvents = remember(localFollowUps, remoteFollowUps) {
        val byId = LinkedHashMap<String, FollowUpEntity>()
        remoteFollowUps.forEach { byId[it.id] = it }
        // Lokaler Stand gewinnt bei gleicher ID, damit gerade ausgefuehrte
        // Aenderungen sofort sichtbar sind, noch bevor der naechste Sync laeuft.
        localFollowUps.forEach { byId[it.id] = it }
        byId.values.sortedBy { it.dueAt }
    }

    Box(Modifier.fillMaxSize()) {
        AktivitaetenScreen(
            viewModel = viewModel,
            onAddFollowUp = onAddFollowUp
        )

        ActivityCalendarPeek(
            events = mergedEvents,
            remoteCount = remoteFollowUps.size,
            isSyncing = isSyncing,
            lastSyncAt = lastSyncAt,
            onOpen = { showFullCalendar = true },
            onRefresh = { scope.launch { refreshFromSupabase() } },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp)
                .widthIn(min = 205.dp, max = 245.dp)
        )
    }

    if (showFullCalendar) {
        FullScreenActivityCalendar(
            events = mergedEvents,
            localEventIds = localFollowUps.mapTo(hashSetOf()) { it.id },
            isSyncing = isSyncing,
            lastSyncAt = lastSyncAt,
            onDismiss = { showFullCalendar = false },
            onRefresh = { scope.launch { refreshFromSupabase() } },
            onAdd = {
                showFullCalendar = false
                onAddFollowUp()
            },
            onCall = { event ->
                if (event.contactPhone.isNotBlank() && event.contactPhone != "-") {
                    viewModel.initiateCall(event.contactPhone, event.contactName, event.contactId)
                }
            },
            onCompleteLocal = { event -> viewModel.completeFollowUp(event.id) },
            onDeleteLocal = { event -> viewModel.deleteFollowUp(event.id) }
        )
    }
}

@Composable
private fun ActivityCalendarPeek(
    events: List<FollowUpEntity>,
    remoteCount: Int,
    isSyncing: Boolean,
    lastSyncAt: Long?,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val active = events.filter { !it.isCompleted }
    val next = active.minByOrNull { kotlin.math.abs(it.dueAt - now) }
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.GERMAN) }
    val dateFmt = remember { SimpleDateFormat("EEE, dd.MM. · HH:mm", Locale.GERMAN) }
    val syncFmt = remember { SimpleDateFormat("HH:mm", Locale.GERMAN) }

    AppCard(
        modifier = modifier,
        accent = ThemeSecondary,
        onClick = onOpen
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ThemeSecondary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = ThemeSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kalender", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(
                        monthFmt.format(Date()),
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Kalender aktualisieren",
                        tint = if (isSyncing) ThemeAccent else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            if (next != null) {
                val overdue = next.dueAt < now
                Text(
                    text = if (overdue) "Naechste offene Wiedervorlage" else "Naechster Termin",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = next.contactName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFmt.format(Date(next.dueAt)),
                    color = if (overdue) MaterialTheme.colorScheme.error else ThemeAccent,
                    style = MaterialTheme.typography.labelMedium
                )
            } else {
                Text(
                    "Keine offenen Termine",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Tippen fuer Monatsansicht und Terminverlauf",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (lastSyncAt != null) ThemeAccent else TextMuted)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        append("Supabase")
                        if (lastSyncAt != null) append(" · ${syncFmt.format(Date(lastSyncAt))}")
                        if (remoteCount > 0) append(" · $remoteCount")
                    },
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("Vollbild", color = ThemeSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FullScreenActivityCalendar(
    events: List<FollowUpEntity>,
    localEventIds: Set<String>,
    isSyncing: Boolean,
    lastSyncAt: Long?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onCall: (FollowUpEntity) -> Unit,
    onCompleteLocal: (FollowUpEntity) -> Unit,
    onDeleteLocal: (FollowUpEntity) -> Unit
) {
    var monthAnchor by remember { mutableStateOf(firstDayOfMonth(System.currentTimeMillis())) }
    var selectedDay by remember { mutableStateOf(startOfDay(System.currentTimeMillis())) }

    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.GERMAN) }
    val selectedFmt = remember { SimpleDateFormat("EEEE, dd. MMMM", Locale.GERMAN) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.GERMAN) }
    val syncFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.GERMAN) }

    val cells = remember(monthAnchor) { monthCells(monthAnchor) }
    val selectedEvents = remember(events, selectedDay) {
        eventsForDay(events, selectedDay).sortedBy { it.dueAt }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Kalender schliessen", tint = TextPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Kalender",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (lastSyncAt == null) "Supabase wird verbunden"
                            else "Supabase synchron · ${syncFmt.format(Date(lastSyncAt))}",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            "Aktualisieren",
                            tint = if (isSyncing) ThemeAccent else TextSecondary
                        )
                    }
                    FilledTonalIconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, "Termin hinzufuegen")
                    }
                }

                Spacer(Modifier.height(8.dp))

                AppCard(accent = ThemeSecondary) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                monthAnchor = shiftMonth(monthAnchor, -1)
                                selectedDay = monthAnchor
                            }) {
                                Icon(Icons.Default.ChevronLeft, "Vorheriger Monat", tint = TextSecondary)
                            }
                            Text(
                                monthFmt.format(Date(monthAnchor)).replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString()
                                },
                                modifier = Modifier.weight(1f),
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = {
                                monthAnchor = firstDayOfMonth(System.currentTimeMillis())
                                selectedDay = startOfDay(System.currentTimeMillis())
                            }) {
                                Text("Heute")
                            }
                            IconButton(onClick = {
                                monthAnchor = shiftMonth(monthAnchor, 1)
                                selectedDay = monthAnchor
                            }) {
                                Icon(Icons.Default.ChevronRight, "Naechster Monat", tint = TextSecondary)
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        WeekdayHeader()
                        Spacer(Modifier.height(4.dp))

                        cells.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    MonthDayCell(
                                        dayStart = day,
                                        selected = day != null && sameDay(day, selectedDay),
                                        today = day != null && sameDay(day, System.currentTimeMillis()),
                                        dayEvents = if (day == null) emptyList() else eventsForDay(events, day),
                                        onSelect = { selected -> selectedDay = selected },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        StatusBadge(
                            "${selectedEvents.count { !it.isCompleted }} offen",
                            BadgeTone.Info
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (selectedEvents.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                null,
                                tint = TextMuted,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Keine Termine an diesem Tag", color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onAdd) { Text("Termin anlegen") }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(selectedEvents, key = { it.id }) { event ->
                            CalendarEventCard(
                                event = event,
                                timeText = timeFmt.format(Date(event.dueAt)),
                                isLocal = event.id in localEventIds,
                                onCall = { onCall(event) },
                                onComplete = { onCompleteLocal(event) },
                                onDelete = { onDeleteLocal(event) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val names = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    Row(Modifier.fillMaxWidth()) {
        names.forEach { name ->
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
        Box(modifier.height(46.dp))
        return
    }

    val dayNumber = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_MONTH)
    val hasOpen = dayEvents.any { !it.isCompleted }
    val cellBg = when {
        selected -> ThemeSecondary.copy(alpha = 0.22f)
        today -> ThemeAccent.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Surface(
        onClick = { onSelect(dayStart) },
        modifier = modifier.padding(2.dp).height(46.dp),
        shape = RoundedCornerShape(12.dp),
        color = cellBg
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "$dayNumber",
                color = if (selected) ThemeSecondary else TextPrimary,
                fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal
            )
            if (dayEvents.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .size(if (dayEvents.size > 2) 6.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (hasOpen) ThemeAccent else TextMuted)
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
    AppCard(accent = if (event.isCompleted) null else ThemeAccent) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        timeText,
                        color = if (event.isCompleted) TextMuted else ThemeAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(54.dp)
                    )
                    Text(
                        event.contactName,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                val detail = event.note?.takeIf { it.isNotBlank() }
                    ?: event.callReason?.takeIf { it.isNotBlank() }
                    ?: event.contactPhone
                if (detail.isNotBlank()) {
                    Text(
                        detail,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(
                        if (event.isCompleted) "Erledigt" else "Offen",
                        if (event.isCompleted) BadgeTone.Neutral else BadgeTone.Info
                    )
                    if (!isLocal) {
                        StatusBadge("Supabase", BadgeTone.Neutral)
                    }
                }
            }

            if (!event.isCompleted && event.contactPhone.isNotBlank() && event.contactPhone != "-") {
                IconButton(onClick = onCall) {
                    Icon(Icons.Default.Phone, "Anrufen", tint = ThemeAccent)
                }
            }
            if (!event.isCompleted && isLocal) {
                IconButton(onClick = onComplete) {
                    Icon(Icons.Default.Check, "Erledigt", tint = ThemeSecondary)
                }
            }
            if (isLocal) {
                TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("Loeschen", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun firstDayOfMonth(timeMillis: Long): Long {
    return Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun startOfDay(timeMillis: Long): Long {
    return Calendar.getInstance().apply {
        this.timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun shiftMonth(anchor: Long, amount: Int): Long {
    return Calendar.getInstance().apply {
        timeInMillis = anchor
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, amount)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun monthCells(anchor: Long): List<Long?> {
    val first = Calendar.getInstance().apply {
        timeInMillis = anchor
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    // Calendar: Sonntag=1. Fuer Montag als erste Spalte auf 0..6 abbilden.
    val leadingEmpty = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val result = MutableList<Long?>(leadingEmpty) { null }
    val maxDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (day in 1..maxDay) {
        val cell = first.clone() as Calendar
        cell.set(Calendar.DAY_OF_MONTH, day)
        result += cell.timeInMillis
    }
    while (result.size % 7 != 0) result += null
    return result
}

private fun eventsForDay(events: List<FollowUpEntity>, dayStart: Long): List<FollowUpEntity> {
    val start = startOfDay(dayStart)
    val end = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    return events.filter { it.dueAt >= start && it.dueAt < end }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
