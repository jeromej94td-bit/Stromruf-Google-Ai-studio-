package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.design.AiHintCard
import com.example.ui.design.AppCard
import com.example.ui.design.BadgeTone
import com.example.ui.design.Dim
import com.example.ui.design.EmptyState
import com.example.ui.design.IconDisc
import com.example.ui.design.KpiTile
import com.example.ui.design.PersonRow
import com.example.ui.design.PrimaryButton
import com.example.ui.design.SecondaryButton
import com.example.ui.design.SectionHeader
import com.example.ui.design.StatusBadge
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ThemeSecondary
import com.example.ui.design.pulsingAura
import com.example.ui.theme.ThemeAccent
import com.example.ui.theme.ThemeAura
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarnAmber
import com.example.viewmodel.StromrufViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.unit.dp

/**
 * HEUTE – die tägliche Arbeitszentrale.
 * Beantwortet: Was ist jetzt wichtig? Wen rufe ich als Nächstes an? Wie läuft mein Tag?
 */
@Composable
fun HeuteScreen(
    viewModel: StromrufViewModel,
    onNavigate: (String) -> Unit,
    onAddContact: () -> Unit,
    onAddFollowUp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiChat: () -> Unit
) {
    val context = LocalContext.current
    var isDefaultDialer by remember { mutableStateOf(com.example.util.ContactsUtil.isDefaultDialer(context)) }
    var isCallPermissionGranted by remember { mutableStateOf(com.example.util.ContactsUtil.hasCallPermission(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDefaultDialer = com.example.util.ContactsUtil.isDefaultDialer(context)
                isCallPermissionGranted = com.example.util.ContactsUtil.hasCallPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val callPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCallPermissionGranted = granted
    }

    val contacts by viewModel.contacts.collectAsState()
    val followUps by viewModel.activeFollowUps.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val heisseAngebote by viewModel.heisseAngebote.collectAsState()

    val now = System.currentTimeMillis()
    val startOfDay = remember(now / 60000) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val dueNow = followUps.filter { it.dueAt <= now }.sortedBy { it.dueAt }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.GERMAN) }
    val dueToday = followUps.filter { it.dueAt in now..(startOfDay + 24L * 3600_000) }
        .sortedBy { it.dueAt }
    val callsToday = callLogs.filter { it.timestamp >= startOfDay }
    val reachedToday = callsToday.count { it.outcome.startsWith("erreicht", ignoreCase = true) }
    val quote = if (callsToday.isNotEmpty()) (reachedToday * 100 / callsToday.size) else 0

    val nextFocusId by viewModel.nextHotBoxContactId.collectAsState()
    val nextFocus = remember(contacts, nextFocusId) {
        contacts.find { it.id == nextFocusId } ?: contacts.firstOrNull {
            it.isHotBox && !it.hasBeenCalledInHotCycle && it.isReachableNow()
        }
    }
    val focusOpenCount = contacts.count { it.isHotBox && !it.hasBeenCalledInHotCycle }

    val staleOffers = heisseAngebote.count { now - it.dateCreated > 3L * 24 * 3600_000 }



    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dim.screenPad, end = Dim.screenPad, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(Dim.gap)
    ) {
        // ---- Kopfbereich ----
        item {
            Spacer(Modifier.height(56.dp).statusBarsPadding())
        }

        if (!isDefaultDialer || !isCallPermissionGranted) {
            item {
                AppCard(
                    accent = Color(0xFFEF4444)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Warnung",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "WICHTIG: Telefonie einrichten",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (!isDefaultDialer) {
                                "Stromruf muss als Standard-Telefon-App festgelegt sein, um ausgehende Anrufe direkt zu tätigen und die Gesprächserfassung zu nutzen."
                            } else {
                                "Die Telefonberechtigung wird benötigt, um Anrufe direkt aus der App zu starten."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton(
                            text = if (!isDefaultDialer) "Als Standard-App setzen" else "Berechtigung erteilen",
                            icon = Icons.Default.Check,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    if (!isDefaultDialer) {
                                        com.example.util.ContactsUtil.requestDefaultDialer(activity, 1201)
                                    } else {
                                        callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        item { DialerCard(viewModel) }

        // ---- Jetzt fällig ----
        item {
            SectionHeader(
                title = if (dueNow.isNotEmpty()) "JETZT FÄLLIG · ${dueNow.size}" else "HEUTE ANSTEHEND",
                actionLabel = "Alle",
                onAction = { onNavigate("aktivitaeten") }
            )
        }
        val listToShow = (if (dueNow.isNotEmpty()) dueNow else dueToday).take(3)
        if (listToShow.isEmpty()) {
            item {
                AppCard {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconDisc(Icons.Default.Check, ThemeAccent, size = 34)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Keine offenen Wiedervorlagen – gute Arbeit.",
                            style = MaterialTheme.typography.bodyMedium, color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(listToShow.size) { i ->
                val fu = listToShow[i]
                val overdue = fu.dueAt <= now
                PersonRow(
                    title = fu.contactName,
                    subtitle = fu.note ?: fu.contactPhone,
                    leadingIcon = Icons.Default.Notifications,
                    leadingTint = if (overdue) WarnAmber else ThemeSecondary,
                    badge = {
                        StatusBadge(
                            text = (if (overdue) "Fällig " else "") + timeFmt.format(Date(fu.dueAt)),
                            tone = if (overdue) BadgeTone.Warn else BadgeTone.Info
                        )
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                viewModel.initiateCall(fu.contactPhone, fu.contactName, fu.contactId)
                            }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.Phone, "Anrufen", tint = ThemeAccent, modifier = Modifier.size(19.dp))
                            }
                            IconButton(onClick = {
                                viewModel.completeFollowUp(fu.id)
                            }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.Check, "Erledigt", tint = TextMuted, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                )
            }
        }

        // ---- Tagesleistung ----
        item { SectionHeader("MEIN TAG") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dim.gap)) {
                KpiTile(
                    value = callsToday.size.toString(), label = "Anrufe heute",
                    modifier = Modifier.weight(1f), tone = BadgeTone.Info
                )
                KpiTile(
                    value = "$quote %", label = "Erreicht-Quote",
                    modifier = Modifier.weight(1f),
                    tone = if (quote >= 50) BadgeTone.Success else BadgeTone.Neutral
                )
                KpiTile(
                    value = followUps.size.toString(), label = "Offene WV",
                    modifier = Modifier.weight(1f),
                    tone = if (dueNow.isNotEmpty()) BadgeTone.Warn else BadgeTone.Neutral
                )
            }
        }

        // ---- Schnellaktionen ----
        item { SectionHeader("SCHNELLAKTIONEN") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "Kontakt", onClick = onAddContact,
                    icon = Icons.Default.Add, modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    "Wiedervorlage", onClick = onAddFollowUp,
                    icon = Icons.Default.Notifications, modifier = Modifier.weight(1f)
                )
                
            }
        }
    }
}
