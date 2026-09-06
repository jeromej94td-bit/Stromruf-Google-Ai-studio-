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
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val Cyan = Color(0xFF39C6FF)
private val Teal = Color(0xFF21D4B4)
private val Gold = Color(0xFFFFC857)
private val Violet = Color(0xFF9A72FF)
private val Orange = Color(0xFFFF914D)

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
            .background(SlateHigh.copy(alpha = .46f))
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
                Icon(Icons.Default.Phone, null, tint = Color(0xFF071421), modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp)); Text("Erneut", color = Color(0xFF071421))
            }
        }
        Text("Gedrückt halten: Kundennummer kopieren", color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
fun LeadWeekBanner(weekCount: Int, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(Cyan.copy(alpha = .17f), SlateHigh.copy(alpha = .55f))))
            .border(1.dp, Cyan.copy(alpha = .65f), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = Cyan)
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
            .background(SlateHigh.copy(alpha = .42f)).border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Heute", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
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
        modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .13f))
            .border(1.dp, color.copy(alpha = .7f), RoundedCornerShape(18.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column { Text("$count", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(label, color = TextSecondary, fontSize = 12.sp) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NextLeadCallCard(lead: NeukundeEntity?, onOpen: (NeukundeEntity) -> Unit, onCall: (NeukundeEntity) -> Unit) {
    if (lead == null) return
    val pulse by rememberInfiniteTransition(label = "nextLeadPulse").animateFloat(
        initialValue = .5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "nextLeadAlpha"
    )
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().alpha(.92f + pulse * .08f).clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(Cyan.copy(alpha = .18f), SlateHigh.copy(alpha = .72f))))
            .border(1.dp, Cyan.copy(alpha = .55f + pulse * .35f), RoundedCornerShape(24.dp))
            .combinedClickable(onClick = { onOpen(lead) }, onLongClick = { copyCustomerNumber(context, lead) })
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bolt, null, tint = Cyan)
            Spacer(Modifier.width(7.dp))
            Text("Nächster Anruf", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text("${lead.callAttempts + 1}. Versuch", color = Gold, fontSize = 12.sp)
        }
        Text(leadTitle(lead), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        Text("Kd.-Nr. ${lead.customerNumber.ifBlank { "offen" }} · ${lead.phone}", color = TextSecondary)
        lead.nextActionAt?.let { due ->
            Text("Geplant: ${SimpleDateFormat("dd.MM. · HH:mm", Locale.GERMANY).format(Date(due))}", color = Gold, fontSize = 12.sp)
        }
        Button(
            onClick = { onCall(lead) }, modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan), shape = RoundedCornerShape(18.dp)
        ) { Icon(Icons.Default.Phone, null, tint = Color(0xFF061521)); Spacer(Modifier.width(10.dp)); Text("Jetzt anrufen", color = Color(0xFF061521), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
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
        Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(22.dp))
            .background(SlateHigh.copy(alpha = .48f)).border(1.dp, color.copy(alpha = .42f), RoundedCornerShape(22.dp))
            .combinedClickable(onClick = { expanded = !expanded }, onLongClick = { copyCustomerNumber(context, lead) })
            .padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Text(leadTitle(lead).take(2).uppercase(), color = color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(leadTitle(lead), color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Kd.-Nr. ${lead.customerNumber.ifBlank { "offen" }}", color = TextMuted, fontSize = 12.sp)
            }
            Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .65f))) {
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
                if (lead.phone.isNotBlank()) OutlinedButton(onClick = { onCall(lead) }) { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(5.dp)); Text("Anrufen") }
                if (lead.status == LeadWorkflow.CALL) TextButton(onClick = { onMissed(lead) }) { Text("Nicht erreicht", color = Orange) }
            }
            Button(
                onClick = { onAdvance(lead) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color)
            ) { Text(actionLabel(lead.status), color = Color(0xFF071421)) }
        }
    }
}

@Composable
private fun LeadProgress(status: String) {
    val index = when (status) {
        LeadWorkflow.CALL -> 1; LeadWorkflow.MAIL -> 2; LeadWorkflow.OFFER -> 3
        LeadWorkflow.OFFER_SENT -> 4; LeadWorkflow.FOLLOW_UP -> 5; LeadWorkflow.DONE -> 6; else -> 0
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(6) { step ->
            Box(Modifier.size(if (step < index) 8.dp else 7.dp).clip(CircleShape)
                .background(if (step < index) Cyan else TextMuted.copy(alpha = .35f)))
        }
    }
}

@Composable
fun LeadReviewTray(count: Int) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(SlateHigh.copy(alpha = .42f))
        .border(1.dp, BorderSubtle, RoundedCornerShape(19.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Inventory2, null, tint = TextSecondary)
        Spacer(Modifier.width(10.dp)); Text("Prüfliste · $count", color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f)); Icon(Icons.Default.KeyboardArrowDown, null, tint = TextMuted)
    }
}

private fun leadTitle(lead: NeukundeEntity) = lead.company ?: lead.customerName ?: lead.customerNumber.takeIf { it.isNotBlank() }?.let { "Kunde $it" } ?: "Neuer Lead"
private fun shortStatus(status: String) = when (status) {
    LeadWorkflow.CALL -> "Anruf offen"; LeadWorkflow.MAIL -> "Datenmail"; LeadWorkflow.OFFER -> "Angebot vorbereiten"
    LeadWorkflow.OFFER_SENT -> "Angebot gesendet"; LeadWorkflow.FOLLOW_UP -> "Nachfassen"; LeadWorkflow.DONE -> "Erledigt"; else -> status
}
private fun actionLabel(status: String) = when (status) {
    LeadWorkflow.CALL -> "Erreicht"; LeadWorkflow.MAIL -> "Mail erledigt"; LeadWorkflow.OFFER -> "Angebot gesendet"
    LeadWorkflow.OFFER_SENT -> "Nachfassen"; LeadWorkflow.FOLLOW_UP -> "Stand geprüft"; else -> "Weiter"
}
private fun statusColor(status: String) = when (status) {
    LeadWorkflow.CALL -> Teal; LeadWorkflow.MAIL -> Cyan; LeadWorkflow.OFFER, LeadWorkflow.OFFER_SENT -> Gold
    LeadWorkflow.FOLLOW_UP -> Violet; LeadWorkflow.DONE -> Color(0xFF56E39F); else -> TextMuted
}
private fun copyCustomerNumber(context: Context, lead: NeukundeEntity) {
    if (lead.customerNumber.isBlank()) { Toast.makeText(context, "Keine Kundennummer hinterlegt", Toast.LENGTH_SHORT).show(); return }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kundennummer", lead.customerNumber.filter(Char::isDigit)))
    Toast.makeText(context, "Kundennummer kopiert", Toast.LENGTH_SHORT).show()
}

private fun copyCustomerNumber(context: Context, contact: ContactEntity) {
    val number = contact.customerNumber?.filter(Char::isDigit).orEmpty()
    if (number.isBlank()) { Toast.makeText(context, "Keine Kundennummer hinterlegt", Toast.LENGTH_SHORT).show(); return }
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
