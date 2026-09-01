package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.agent.OwnSipConfig
import com.example.agent.OwnSipEngine
import com.example.agent.OwnSipSettings

@Composable
fun SmartCallsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { OwnSipSettings(context) }
    var config by remember { mutableStateOf(settings.load()) }
    var number by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val sipStatus by OwnSipEngine.status.collectAsState()
    val activeNumber by OwnSipEngine.activeNumber.collectAsState()
    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) message = "Mikrofon-Berechtigung wird für Smart Calls benötigt."
    }
    LaunchedEffect(Unit) { OwnSipEngine.init(context) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Smart Calls", style = MaterialTheme.typography.headlineSmall)
        Text("Eigene Easybell-SIP-Telefonie mit automatischer lokaler Aufnahme.")
        Text("Status: $sipStatus")
        OutlinedTextField(config.username, { config = config.copy(username = it) }, label = { Text("Easybell SIP-Benutzername") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(config.password, { config = config.copy(password = it) }, label = { Text("SIP-Passwort") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(config.registrar, { config = config.copy(registrar = it) }, label = { Text("SIP-Registrar") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(number, { number = it }, label = { Text("Zielnummer") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { settings.save(config); OwnSipEngine.register(config) { _, text -> message = text } }) {
                Icon(Icons.Default.Lock, null); Spacer(Modifier.width(6.dp)); Text("Easybell verbinden")
            }
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestMic.launch(Manifest.permission.RECORD_AUDIO)
                else OwnSipEngine.call(number, config) { _, text -> message = text }
            }, enabled = activeNumber == null) {
                Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("Anrufen")
            }
        }
        if (activeNumber != null) OutlinedButton(onClick = { OwnSipEngine.hangUp() }) { Icon(Icons.Default.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("Auflegen") }
        message?.let { Text(it) }
        Text("Aufnahmen bleiben lokal auf diesem Gerät. Transkription ist noch nicht aktiviert.")
    }
}

