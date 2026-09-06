package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.homesip.HomeSipCallUiState
import com.example.homesip.HomeSipStatus
import com.example.homesip.HomeSipTrunk
import com.example.ui.design.AppCard
import com.example.ui.design.PrimaryButton
import com.example.ui.theme.*
import com.example.util.ContactsUtil
import com.example.viewmodel.StromrufViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerCard(viewModel: StromrufViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val homeSip = remember(context) { HomeSipTrunk.get(context) }
    val sipState by homeSip.state.collectAsState()
    var quickName by remember { mutableStateOf("") }
    var quickPhone by remember { mutableStateOf("") }
    var pendingSmartPhone by remember { mutableStateOf<String?>(null) }
    var pendingSmartName by remember { mutableStateOf<String?>(null) }
    val contacts by viewModel.contacts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()

    fun clearPendingSmart() {
        pendingSmartPhone = null
        pendingSmartName = null
    }

    fun startSmartCallOnce(phone: String, name: String?) {
        HomeSipCallUiState.prepare(phone, name, smartCall = true)
        homeSip.startCall(phone)
        clearPendingSmart()
        quickPhone = ""
        quickName = ""
    }

    fun launchSmartCall() {
        val phone = quickPhone.trim()
        if (phone.isBlank()) return
        val name = quickName.takeIf { it.isNotBlank() }

        if (sipState.status == HomeSipStatus.READY) {
            startSmartCallOnce(phone, name)
        } else {
            pendingSmartPhone = phone
            pendingSmartName = name
            val saved = homeSip.savedSettings()
            homeSip.connect(saved)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchSmartCall()
    }

    LaunchedEffect(sipState.status, pendingSmartPhone) {
        val phone = pendingSmartPhone ?: return@LaunchedEffect
        when (sipState.status) {
            HomeSipStatus.READY -> startSmartCallOnce(phone, pendingSmartName)
            HomeSipStatus.DIALING, HomeSipStatus.RINGING, HomeSipStatus.IN_CALL -> clearPendingSmart()
            HomeSipStatus.ERROR, HomeSipStatus.OFFLINE -> clearPendingSmart()
            else -> Unit
        }
    }

    LaunchedEffect(quickPhone) {
        if (quickPhone.length >= 3) {
            val matchedName = withContext(Dispatchers.IO) { ContactsUtil.lookupContactName(context, quickPhone) }
            if (matchedName != null && quickName.isBlank()) quickName = matchedName
        }
    }

    var matchingSystem by remember { mutableStateOf<List<ContactsUtil.SystemContact>>(emptyList()) }
    val searchQuery = if (quickPhone.isNotBlank()) quickPhone else quickName
    LaunchedEffect(searchQuery) {
        matchingSystem = if (ContactsUtil.hasContactsPermission(context) && searchQuery.isNotBlank()) {
            withContext(Dispatchers.IO) { ContactsUtil.searchSystemContacts(context, searchQuery).take(3) }
        } else emptyList()
    }
    val matchingLocal = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) emptyList() else contacts.filter { it.phone.contains(searchQuery, true) || it.name.contains(searchQuery, true) }.take(3)
    }
    val matchingHistory = remember(searchQuery, callLogs) {
        if (searchQuery.isBlank()) emptyList() else callLogs.filter {
            it.phone.contains(searchQuery, true) || (it.contactName?.contains(searchQuery, true) == true)
        }.distinctBy { it.phone }.take(3)
    }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Direktwahl", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(quickName, { quickName = it }, label = { Text("Kundenname (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = quickPhone, onValueChange = { quickPhone = it }, label = { Text("Telefonnummer") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                trailingIcon = { if (quickPhone.isNotBlank()) IconButton(onClick = { quickPhone = "" }) { Icon(Icons.Default.Close, "Leeren") } },
                shape = RoundedCornerShape(8.dp)
            )

            if (matchingSystem.isNotEmpty() || matchingLocal.isNotEmpty() || matchingHistory.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SlateElevated).padding(8.dp)) {
                    matchingLocal.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { quickName = c.name; quickPhone = c.phone }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, "App Kontakt", tint = Emerald, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Column { Text(c.name, color = TextPrimary); Text(c.phone, color = TextMuted, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    matchingSystem.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { quickName = c.name; quickPhone = c.phone }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dialpad, "System Kontakt", tint = Cyan, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Column { Text(c.name, color = TextPrimary); Text(c.phone, color = TextMuted, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    matchingHistory.forEach { log ->
                        Row(Modifier.fillMaxWidth().clickable { quickName = log.contactName ?: ""; quickPhone = log.phone }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, "Verlauf", tint = TextMuted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Column { Text(log.contactName ?: log.phone, color = TextPrimary); Text(log.phone, color = TextMuted, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Anrufen", icon = Icons.Default.Phone, modifier = Modifier.weight(1f), onClick = {
                    if (quickPhone.isNotBlank()) {
                        viewModel.initiateCall(quickPhone.trim(), quickName.takeIf { it.isNotBlank() }, null)
                        quickPhone = ""; quickName = ""
                    }
                })
                PrimaryButton("Smart-Anruf", icon = Icons.Default.PhoneInTalk, modifier = Modifier.weight(1f), onClick = {
                    if (quickPhone.isBlank()) return@PrimaryButton
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) launchSmartCall()
                    else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                })
            }
            if (pendingSmartPhone != null || sipState.status in setOf(HomeSipStatus.CONNECTING, HomeSipStatus.DIALING, HomeSipStatus.RINGING, HomeSipStatus.IN_CALL)) {
                Spacer(Modifier.height(8.dp))
                Text("Smart-Anruf: ${sipState.message}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}
