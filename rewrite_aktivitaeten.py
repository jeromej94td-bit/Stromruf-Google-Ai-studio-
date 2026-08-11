content = """package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.database.CallLogEntity
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
    
    var mode by remember { mutableStateOf(0) } // 0 = Verlauf, 1 = Geplant

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Dim.screenPad).padding(bottom = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Aktivitäten", style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onAddFollowUp) {
                Icon(Icons.Default.Add, "Wiedervorlage", tint = Cyan)
            }
        }
        
        Spacer(Modifier.height(Dim.gap))
        
        SegmentedControl(
            options = listOf("Verlauf", "Geplant"),
            selectedIndex = mode,
            onSelect = { mode = it }
        )
        
        Spacer(Modifier.height(Dim.gap))

        if (mode == 0) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dim.gap)) {
                historySection(callLogs)
            }
        } else {
            PlannedSection(followUps, viewModel)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlannedSection(followUps: List<FollowUpEntity>, viewModel: StromrufViewModel) {
    // Pages: 0: Letzter Monat, 1: Letzte Woche, 2: Gestern, 3: Heute, 4: Morgen, 5: Nächste Woche, 6: Nächster Monat
    val pagerState = rememberPagerState(initialPage = 3, pageCount = { 7 })
    val coroutineScope = rememberCoroutineScope()
    
    val pageTitles = listOf("Letzter Monat", "Letzte Woche", "Gestern", "Heute", "Morgen", "Nächste Woche", "Nächster Monat")
    
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(pageTitles[pagerState.currentPage], style = MaterialTheme.typography.titleMedium, color = Cyan)
        }
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Dim.gap)) {
                val now = System.currentTimeMillis()
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val startEnd: Pair<Long, Long> = when (page) {
                    0 -> { // Letzter Monat
                        val start = Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                        val end = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                        start to end
                    }
                    1 -> { // Letzte Woche
                        val start = todayStart - 14L * 24 * 3600_000 // Roughly, let's just do last 7-14 days
                        val end = todayStart - 7L * 24 * 3600_000
                        start to end
                    }
                    2 -> { // Gestern
                        val start = todayStart - 24L * 3600_000
                        val end = todayStart
                        start to end
                    }
                    3 -> { // Heute
                        val start = todayStart
                        val end = todayStart + 24L * 3600_000
                        start to end
                    }
                    4 -> { // Morgen
                        val start = todayStart + 24L * 3600_000
                        val end = todayStart + 48L * 3600_000
                        start to end
                    }
                    5 -> { // Nächste Woche
                        val start = todayStart + 48L * 3600_000
                        val end = todayStart + 9L * 24 * 3600_000
                        start to end
                    }
                    6 -> { // Nächster Monat
                        val start = todayStart + 9L * 24 * 3600_000
                        val end = todayStart + 40L * 24 * 3600_000
                        start to end
                    }
                    else -> 0L to 0L
                }
                
                val items = followUps.filter { it.dueAt in startEnd.first until startEnd.second }.sortedBy { it.dueAt }
                
                if (items.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Default.Notifications, "Keine Wiedervorlagen",
                            "Keine Termine für diesen Zeitraum."
                        )
                    }
                } else {
                    followUpGroup(pageTitles[page].uppercase(), items, Cyan, viewModel, now)
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
    item(key = "grp_$title") {
        SectionHeader("$title · ${items.size}")
    }

    items(items.size, key = { i -> items[i].id }) { i ->
        val fu = items[i]
        val overdue = fu.dueAt <= now

        PersonRow(
            title = fu.contactName,
            subtitle = fu.note ?: fu.contactPhone,
            leadingIcon = Icons.Default.Notifications,
            leadingTint = accent,
            badge = {
                StatusBadge(
                    timeFmt.format(Date(fu.dueAt)),
                    if (overdue) BadgeTone.Warn else BadgeTone.Info
                )
            },
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = {
                        viewModel.initiateCall(fu.contactPhone, fu.contactName, fu.contactId)
                    }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Phone, "Anrufen", tint = Emerald,
                            modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { viewModel.completeFollowUp(fu.id) },
                        modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Check, "Erledigt", tint = Cyan,
                            modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { viewModel.deleteFollowUp(fu.id) },
                        modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Delete, "Löschen", tint = TextMuted,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historySection(
    callLogs: List<CallLogEntity>
) {
    if (callLogs.isEmpty()) {
        item {
            EmptyState(
                Icons.Default.Phone, "Noch keine Anrufe protokolliert",
                "Nach jedem Gespräch wird der Verlauf hier automatisch erfasst."
            )
        }
        return
    }

    val sorted = callLogs.sortedByDescending { it.timestamp }
    val dayFmt = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMAN)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMAN)

    val grouped = sorted.groupBy { dayFmt.format(Date(it.timestamp)) }

    // Tageskennzahl-Kopf
    item {
        val today = sorted.count {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            it.timestamp >= c.timeInMillis
        }
        AppCard {
            Row(Modifier.padding(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("$today", style = MaterialTheme.typography.headlineMedium, color = Emerald)
                    Text("Anrufe heute", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
                Column(Modifier.weight(1f)) {
                    Text("${callLogs.size}", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Gesamt", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
        }
    }

    grouped.forEach { (dayStr, logs) ->
        item(key = "hdr_$dayStr") {
            SectionHeader(dayStr.uppercase())
        }
        items(logs.size, key = { i -> logs[i].id }) { i ->
            val log = logs[i]
            val durationMin = log.durationSeconds / 60
            val durationSec = log.durationSeconds % 60
            val durStr = String.format("%02d:%02d", durationMin, durationSec)

            val isReached = log.outcome.equals("Erreicht", true)
            val accent = if (isReached) Emerald else TextMuted

            PersonRow(
                title = log.contactName ?: log.number,
                subtitle = "${timeFmt.format(Date(log.timestamp))} · $durStr Min.",
                leadingIcon = Icons.Default.Phone,
                leadingTint = accent,
                badge = {
                    StatusBadge(
                        log.outcome,
                        if (isReached) BadgeTone.Success else BadgeTone.Neutral
                    )
                }
            )
        }
    }
}
"""

with open('./app/src/main/java/com/example/ui/screens/AktivitaetenScreen.kt', 'w') as f:
    f.write(content)
