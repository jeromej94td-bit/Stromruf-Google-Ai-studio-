package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.ContactEntity
import com.example.database.CallLogEntity
import com.example.ui.design.*
import com.example.viewmodel.StromrufViewModel
import com.example.util.ContactsUtil
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerCard(
    viewModel: StromrufViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var quickName by remember { mutableStateOf("") }
    var quickPhone by remember { mutableStateOf("") }
    val contacts by viewModel.contacts.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    
    LaunchedEffect(quickPhone) {
        if (quickPhone.isNotBlank() && quickPhone.length >= 3) {
            val matchedName = withContext(Dispatchers.IO) {
                ContactsUtil.lookupContactName(context, quickPhone)
            }
            if (matchedName != null && quickName.isBlank()) {
                quickName = matchedName
            }
        }
    }
    
    var matchingSystem by remember { mutableStateOf<List<ContactsUtil.SystemContact>>(emptyList()) }
    val searchQuery = if (quickPhone.isNotBlank()) quickPhone else quickName
    
    LaunchedEffect(searchQuery) {
        if (ContactsUtil.hasContactsPermission(context) && searchQuery.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val list = ContactsUtil.searchSystemContacts(context, searchQuery).take(3)
                withContext(Dispatchers.Main) {
                    matchingSystem = list
                }
            }
        } else {
            matchingSystem = emptyList()
        }
    }
    
    val matchingLocal = remember(searchQuery, contacts) {
        if (searchQuery.isBlank()) emptyList() else {
            contacts.filter { 
                 it.phone.contains(searchQuery, ignoreCase = true) || 
                 it.name.contains(searchQuery, ignoreCase = true) 
            }.take(3)
        }
    }
    
    val matchingHistory = remember(searchQuery, callLogs) {
        if (searchQuery.isBlank()) emptyList() else {
            callLogs.filter {
                it.phone.contains(searchQuery, ignoreCase = true) ||
                (it.contactName != null && it.contactName.contains(searchQuery, ignoreCase = true))
            }.distinctBy { it.phone }.take(3)
        }
    }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Direktwahl", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = quickName,
                onValueChange = { quickName = it },
                label = { Text("Kundenname (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = quickPhone,
                    onValueChange = { quickPhone = it },
                    label = { Text("Telefonnummer") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    trailingIcon = {
                        if (quickPhone.isNotBlank()) {
                            IconButton(onClick = { quickPhone = "" }) { Icon(Icons.Default.Close, "Leeren") }
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            if (matchingSystem.isNotEmpty() || matchingLocal.isNotEmpty() || matchingHistory.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SlateElevated).padding(8.dp)) {
                    matchingLocal.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { quickName = c.name; quickPhone = c.phone }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, "App Kontakt", tint = Emerald, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(c.phone, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    matchingSystem.forEach { c ->
                        Row(Modifier.fillMaxWidth().clickable { quickName = c.name; quickPhone = c.phone }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dialpad, "System Kontakt", tint = Cyan, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(c.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(c.phone, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    matchingHistory.forEach { log ->
                        Row(Modifier.fillMaxWidth().clickable { quickName = log.contactName ?: ""; quickPhone = log.phone }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, "Verlauf", tint = TextMuted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(log.contactName ?: log.phone, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(log.phone, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Anrufen", icon = Icons.Default.Phone, modifier = Modifier.fillMaxWidth(), onClick = {
                if (quickPhone.isNotBlank()) {
                    viewModel.initiateCall(quickPhone.trim(), quickName.takeIf { it.isNotBlank() }, null)
                    quickPhone = ""
                    quickName = ""
                }
            })
        }
    }
}
