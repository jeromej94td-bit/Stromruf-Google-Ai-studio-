package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.homesip.HomeSipSettings
import com.example.homesip.HomeSipStatus
import com.example.homesip.HomeSipTrunk
import com.example.ui.design.AppCard
import com.example.ui.design.Dim
import com.example.ui.design.PrimaryButton
import com.example.ui.design.SecondaryButton
import com.example.ui.design.SectionHeader
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThemeAccent

@Composable
fun HomeSipTrunkCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val client = remember { HomeSipTrunk.get(context) }
    val state by client.state.collectAsState()
    val stored = remember { client.savedSettings() }
    var user by remember { mutableStateOf(stored.user) }
    var authUser by remember { mutableStateOf(stored.authUser) }
    var password by remember { mutableStateOf(stored.password) }
    var registrar by remember { mutableStateOf(stored.registrar) }
    var port by remember { mutableStateOf(stored.port.toString()) }
    var destination by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) client.startCall(destination)
    }

    SectionHeader("SIP-TRUNK · EASYBELL")
    AppCard(accent = statusColor(state.status)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SettingsEthernet, "SIP-Trunk", tint = statusColor(state.status))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sichere SIP-Telefonie", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Icon(
                    if (state.status == HomeSipStatus.READY) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    null,
                    tint = statusColor(state.status)
                )
            }

            if (settingsOpen) {
                AssistChip(
                    onClick = {
                        registrar = "secure.sip.easybell.de"
                        port = "5061"
                    },
                    label = { Text("Easybell TLS · secure.sip.easybell.de:5061") }
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("SIP-Benutzer / Rufnummer") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = authUser, onValueChange = { authUser = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Auth-Benutzer (optional)") }
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("SIP-Passwort") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { androidx.compose.material3.TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Verbergen" else "Zeigen") } }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = registrar, onValueChange = { registrar = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Registrar") })
                    OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit) }, modifier = Modifier.width(88.dp), singleLine = true, label = { Text("Port") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                PrimaryButton(
                    text = if (state.status == HomeSipStatus.CONNECTING) "Verbinde …" else "SIP-Trunk verbinden",
                    icon = Icons.Default.SettingsEthernet,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val config = HomeSipSettings(user, authUser, password, registrar, port.toIntOrNull() ?: 5061)
                        client.saveSettings(config)
                        client.connect(config)
                    }
                )
            } else {
                SecondaryButton("Zugangsdaten", { settingsOpen = true }, icon = Icons.Default.SettingsEthernet, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(2.dp))
            OutlinedTextField(
                value = destination, onValueChange = { destination = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, label = { Text("Zielrufnummer") }, placeholder = { Text("z. B. 030 123456") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            when (state.status) {
                HomeSipStatus.IN_CALL, HomeSipStatus.DIALING, HomeSipStatus.RINGING ->
                    PrimaryButton("Auflegen", { client.hangUp() }, icon = Icons.Default.Stop, modifier = Modifier.fillMaxWidth())
                else -> PrimaryButton(
                    text = "Über SIP anrufen", icon = Icons.Default.Call, modifier = Modifier.fillMaxWidth(),
                    onClick = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }
                )
            }
            Text("TLS + SRTP sind fest aktiv. Die Zugangsdaten bleiben verschlüsselt auf diesem Gerät.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
private fun statusColor(status: HomeSipStatus): Color = when (status) {
    HomeSipStatus.READY, HomeSipStatus.IN_CALL -> ThemeAccent
    HomeSipStatus.ERROR -> Color(0xFFEF4444)
    HomeSipStatus.CONNECTING, HomeSipStatus.DIALING, HomeSipStatus.RINGING -> Color(0xFFFFC864)
    HomeSipStatus.OFFLINE -> TextMuted
}
