package com.example.ui.screens

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.NeukundeEntity
import com.example.database.ContactEntity
import com.example.database.CallLogEntity
import com.example.leads.LeadWorkflow
import com.example.ui.design.pulsingAura
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val Cyan = Color(0xFF39C6FF)
private val Teal = Color(0xFF21D4B4)
private val Gold = Color(0xFFFFC857)
private val Violet = Color(0xFF9A72FF)
private val Orange = Color(0xFFFF914D)
private val DeepNavy = Color(0xFF061522)

private fun leadGlassBrush(accent: Color, strength: Float = .16f): Brush = Brush.linearGradient(
    listOf(
        accent.copy(alpha = strength),
        SlateHigh.copy(alpha = .72f),
        Color(0xFF081724).copy(alpha = .92f)
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviousHotBoxCallCard(
    contact: ContactEntity,
    callLogs: List<CallLogEntity>,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onEditReachability: () -> Unit
) {
    val context = LocalContext.current
    val phoneKey = contact.phone.filter(Char::isDigit).takeLast(10)
    val logs = callLogs.filter { it.phone.filter(Char::isDigit).takeLast(10) == phoneKey }
    val last = logs.maxByOrNull { it.timestamp }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(leadGlassBrush(Violet, .10f))
            .border(1.dp, Violet.copy(alpha = .5f), RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { copyCustomerNumber(context, contact) })
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = Violet)
            Spacer(Modifier.width(8.dp))
            Text("Vorheriger Hotbox-Anruf", color = Violet, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${logs.size} Versuche", color = TextSecondary, fontSize = 12.sp)
        }
        Text(contact.name, color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(contact.customerNumber?.let { "Kd.-Nr. $it" }, contact.company, contact.phone).joinToString(" · "),
            color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        last?.let {
            Text(
                "${SimpleDateFormat("dd.MM. · HH:mm", Locale.GERMANY).format(Date(it.timestamp))} · ${it.outcome}",
                color = TextMuted, fontSize = 12.sp
            )
        }
        Text(reachabilityText(contact), color = if (contact.isReachableNow()) Teal else Gold, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEditReachability, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Schedule, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp)); Text("Zeiten")
            }
            Button(onClick = onCall, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                Icon(Icons.Default.Phone, null, tint = DeepNavy, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp)); Text("Erneut", color = DeepNavy)
            }
        }
        Text("Gedrückt halten: Kundennummer kopieren", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
fun LeadWeekBanner(weekCount: Int, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Cyan.copy(alpha = .22f), Color(0xFF0B2C45).copy(alpha = .72f), SlateHigh.copy(alpha = .50f))
                )
            )
            .border(1.dp, Cyan.copy(alpha = .72f), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Cyan.copy(alpha = .12f))
                .border(1.dp, Cyan.copy(alpha = .45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Diese Woche", color = Cyan, fontWeight = FontWeight.SemiBold)
        Text(" · $weekCount neue Leads", color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Neuer Lead", tint = Cyan) }
    }
}

@Composable
fun LeadTaskGrid(leads: List<NeukundeEntity>) {
    val active = leads.filter { it.status in LeadWorkflow.active && it.archivedAt == null }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10283A).copy(alpha = .72f), SlateHigh.copy(alpha = .42f), Color(0xFF071522).copy(alpha = .78f))
                )
            )
            .border(1.dp, Cyan.copy(alpha = .20f), RoundedCornerShape(24.dp))
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Heute", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Deine Prioritäten für heute", color = TextMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LeadTaskTile(Icons.Default.Phone, active.count { it.status == LeadWorkflow.CALL }, "anrufen", Teal, Modifier.weight(1f))
            LeadTaskTile(Icons.Default.Email, active.count { it.status == LeadWorkflow.MAIL }, "Datenmails", Cyan, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LeadTaskTile(Icons.Default.Send, active.count { it.status in setOf(LeadWorkflow.OFFER, LeadWorkflow.OFFER_SENT) }, "Angebote", Gold, Modifier.weight(1f))
            LeadTaskTile(Icons.Default.Schedule, active.count { it.status == LeadWorkflow.FOLLOW_UP }, "nachfassen", Violet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LeadTaskTile(icon: ImageVector, count: Int, label: String, color: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(color.copy(alpha = .18f), color.copy(alpha = .07f), Color(0xFF0A1824).copy(alpha = .72f))
                )
            )
            .border(1.dp, color.copy(alpha = if (count > 0) .72f else .32f), RoundedCornerShape(18.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .18f))
                .border(1.dp, color.copy(alpha = .24f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$count", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NextLeadCallCard(lead: NeukundeEntity?, onOpen: (NeukundeEntity) -> Unit, onCall: (NeukundeEntity) -> Unit) {
    if (lead == null) return
    val pulse by rememberInfiniteTransition(label = "nextLeadPulse").animateFloat(
        initialValue = .45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse), label = "nextLeadAlpha"
    )
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth()
            .pulsingAura(Cyan, enabled = true, maxRadiusFactor = .68f)
            .alpha(.94f + pulse * .06f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Cyan.copy(alpha = .24f), Color(0xFF0A3B5B).copy(alpha = .42f), SlateHigh.copy(alpha = .76f))
                )
            )
            .border(1.35.dp, Cyan.copy(alpha = .62f + pulse * .30f), RoundedCornerShape(24.dp))
            .combinedClickable(onClick = { onOpen(lead) }, onLongClick = { copyCustomerNumber(context, lead) })
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).pulsingAura(Cyan, enabled = true, maxRadiusFactor = .90f)
                    .clip(CircleShape).background(Cyan.copy(alpha = .14f))
                    .border(1.dp, Cyan.copy(alpha = .65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bolt, null, tint = Cyan, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text("Nächste Aktion", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Surface(
                color = Gold.copy(alpha = .10f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .65f))
            ) {
                Text("${lead.callAttempts + 1}. Versuch", color = Gold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 11.sp)
            }
        }
        Text(leadTitle(lead), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        Text("Kd.-Nr. ${lead.customerNumber.ifBlank { "offen" }} · ${lead.phone}", color = TextSecondary)
        lead.nextActionAt?.let { due ->
            Text("Geplant: ${SimpleDateFormat("dd.MM. · HH:mm", Locale.GERMANY).format(Date(due))}", color = Gold, fontSize = 12.sp)
        }
        Button(
            onClick = { onCall(lead) },
            modifier = Modifier.fillMaxWidth().height(58.dp)
                .pulsingAura(Cyan, enabled = true, maxRadiusFactor = .72f),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .30f))
        ) {
            Icon(Icons.Default.Phone, null, tint = DeepNavy)
            Spacer(Modifier.width(10.dp))
            Text("Jetzt anrufen", color = DeepNavy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeadCustomerCard(
    lead: NeukundeEntity,
    onOpen: (NeukundeEntity) -> Unit,
    onCall: (NeukundeEntity) -> Unit,
    onMissed: (NeukundeEntity) -> Unit,
    onAdvance: (NeukundeEntity) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember(lead.id) { mutableStateOf(false) }
    val color = statusColor(lead.status)
    Column(
        Modifier.fillMaxWidth().animateContentSize()
            .pulsingAura(color, enabled = expanded, maxRadiusFactor = .58f)
            .clip(RoundedCornerShape(22.dp))
            .background(leadGlassBrush(color, if (expanded) .18f else .08f))
            .border(
                if (expanded) 1.35.dp else 1.dp,
                color.copy(alpha = if (expanded) .82f else .42f),
                RoundedCornerShape(22.dp)
            )
            .combinedClickable(onClick = { expanded = !expanded }, onLongClick = { copyCustomerNumber(context, lead) })
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp)
                    .pulsingAura(color, enabled = expanded, maxRadiusFactor = .92f)
                    .clip(CircleShape).background(color.copy(alpha = .16f))
                    .border(1.dp, color.copy(alpha = if (expanded) .78f else .34f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(leadTitle(lead).take(2).uppercase(), color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(leadTitle(lead), color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Kd.-Nr. ${lead.customerNumber.ifBlank { "offen" }}", color = TextMuted, fontSize = 12.sp)
            }
            Surface(
                modifier = Modifier.pulsingAura(color, enabled = expanded, maxRadiusFactor = .75f),
                color = color.copy(alpha = .12f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = if (expanded) .82f else .65f))
            ) {
                Text(shortStatus(lead.status), color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val contact = lead.phone.ifBlank { lead.email.orEmpty() }
        if (contact.isNotBlank()) Text(contact, color = TextSecondary, fontSize = 13.sp)
        LeadProgress(lead.status)
        val due = lead.nextActionAt
        if (due != null) Text("Nächste Aktion: ${SimpleDateFormat("dd.MM. · HH:mm", Locale.GERMANY).format(Date(due))}", color = color, fontSize = 12.sp)
        if (expanded) {
            TextButton(onClick = { onOpen(lead) }) { Text("Kundendetails", color = Cyan) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lead.phone.isNotBlank()) {
                    OutlinedButton(onClick = { onCall(lead) }) {
                        Icon(Icons.Default.Phone, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Anrufen")
                    }
                }
                if (lead.status == LeadWorkflow.CALL) TextButton(onClick = { onMissed(lead) }) { Text("Nicht erreicht", color = Orange) }
            }
            Button(
                onClick = { onAdvance(lead) },
                modifier = Modifier.fillMaxWidth().pulsingAura(color, enabled = true, maxRadiusFactor = .58f),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .24f))
            ) { Text(actionLabel(lead.status), color = DeepNavy, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LeadProgress(status: String) {
    val index = when (status) {
        LeadWorkflow.CALL -> 1
        LeadWorkflow.MAIL -> 2
        LeadWorkflow.OFFER -> 3
        LeadWorkflow.OFFER_SENT -> 4
        LeadWorkflow.FOLLOW_UP -> 5
        LeadWorkflow.DONE -> 6
        else -> 0
    }
    val labels = listOf("Anruf", "Mail", "Angebot", "Gesendet", "Nachfassen", "Kunde")
    val colors = listOf(Teal, Cyan, Gold, Gold, Violet, Color(0xFF56E39F))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(6) { step ->
            val current = index > 0 && step == index - 1
            val completed = index > 0 && step < index - 1
            val stepColor = colors[step]
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier.size(if (current) 12.dp else if (completed) 9.dp else 8.dp)
                        .pulsingAura(stepColor, enabled = current, maxRadiusFactor = 1.35f)
                        .clip(CircleShape)
                        .background(
                            when {
                                current -> stepColor
                                completed -> Cyan.copy(alpha = .88f)
                                else -> TextMuted.copy(alpha = .30f)
                            }
                        )
                        .then(
                            if (current) Modifier.border(1.dp, Color.White.copy(alpha = .72f), CircleShape)
                            else Modifier
                        )
                )
                Text(
                    labels[step],
                    color = when {
                        current -> stepColor
                        completed -> TextSecondary
                        else -> TextMuted.copy(alpha = .72f)
                    },
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LeadReviewTray(count: Int) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Cyan.copy(alpha = .06f), SlateHigh.copy(alpha = .44f), Violet.copy(alpha = .05f))
                )
            )
            .border(1.dp, BorderSubtle, RoundedCornerShape(19.dp)).padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Inventory2, null, tint = TextSecondary)
        Spacer(Modifier.width(10.dp))
        Text("Prüfliste · $count", color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = TextMuted)
    }
}

private fun leadTitle(lead: NeukundeEntity) = lead.company ?: lead.customerName ?: lead.customerNumber.takeIf { it.isNotBlank() }?.let { "Kunde $it" } ?: "Neuer Lead"
private fun shortStatus(status: String) = when (status) {
    LeadWorkflow.CALL -> "Anruf offen"
    LeadWorkflow.MAIL -> "Datenmail"
    LeadWorkflow.OFFER -> "Angebot vorbereiten"
    LeadWorkflow.OFFER_SENT -> "Angebot gesendet"
    LeadWorkflow.FOLLOW_UP -> "Nachfassen"
    LeadWorkflow.DONE -> "Erledigt"
    else -> status
}
private fun actionLabel(status: String) = when (status) {
    LeadWorkflow.CALL -> "Erreicht"
    LeadWorkflow.MAIL -> "Mail erledigt"
    LeadWorkflow.OFFER -> "Angebot gesendet"
    LeadWorkflow.OFFER_SENT -> "Nachfassen"
    LeadWorkflow.FOLLOW_UP -> "Stand geprüft"
    else -> "Weiter"
}
private fun statusColor(status: String) = when (status) {
    LeadWorkflow.CALL -> Teal
    LeadWorkflow.MAIL -> Cyan
    LeadWorkflow.OFFER, LeadWorkflow.OFFER_SENT -> Gold
    LeadWorkflow.FOLLOW_UP -> Violet
    LeadWorkflow.DONE -> Color(0xFF56E39F)
    else -> TextMuted
}
private fun copyCustomerNumber(context: Context, lead: NeukundeEntity) {
    if (lead.customerNumber.isBlank()) {
        Toast.makeText(context, "Keine Kundennummer hinterlegt", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kundennummer", lead.customerNumber.filter(Char::isDigit)))
    Toast.makeText(context, "Kundennummer kopiert", Toast.LENGTH_SHORT).show()
}

private fun copyCustomerNumber(context: Context, contact: ContactEntity) {
    val number = contact.customerNumber?.filter(Char::isDigit).orEmpty()
    if (number.isBlank()) {
        Toast.makeText(context, "Keine Kundennummer hinterlegt", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kundennummer", number))
    Toast.makeText(context, "Kundennummer kopiert", Toast.LENGTH_SHORT).show()
}

private fun reachabilityText(contact: ContactEntity): String {
    val days = contact.hotBoxWeekdays.orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
    val dayNames = mapOf(
        Calendar.MONDAY to "Mo", Calendar.TUESDAY to "Di", Calendar.WEDNESDAY to "Mi",
        Calendar.THURSDAY to "Do", Calendar.FRIDAY to "Fr", Calendar.SATURDAY to "Sa", Calendar.SUNDAY to "So"
    )
    val dayText = if (days.isEmpty()) "täglich" else days.mapNotNull(dayNames::get).joinToString(" · ")
    fun time(value: Int?): String? = value?.let {
        val minutes = if (it in 0..24) it * 60 else it
        "%02d:%02d".format(Locale.GERMANY, minutes / 60, minutes % 60)
    }
    val start = time(contact.hotBoxStartHour)
    val end = time(contact.hotBoxEndHour)
    val timeText = if (start != null && end != null) "$start–$end Uhr" else "ganztägig"
    return "Erreichbar: $dayText · $timeText"
}
