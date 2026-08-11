package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.util.MailAccountManager
import com.example.util.OpenAiClient
import com.example.util.SecureIntegrationSettings
import kotlinx.coroutines.launch

@Composable
fun KiVersandSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SecureIntegrationSettings(context) }
    val mailManager = remember { MailAccountManager(context) }

    var openAiKey by remember { mutableStateOf(settings.getOpenAiKey() ?: "") }
    var googleClientId by remember { mutableStateOf(settings.getGoogleClientId() ?: "") }
    var microsoftClientId by remember { mutableStateOf(settings.getMicrosoftClientId() ?: "") }
    var status by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("KI und Versand", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = openAiKey,
            onValueChange = { openAiKey = it },
            label = { Text("OpenAI API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                settings.saveOpenAiKey(openAiKey)
                status = "OpenAI Key gespeichert."
            }) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Speichern")
            }
            OutlinedButton(onClick = {
                scope.launch {
                    status = "Teste..."
                    status = if (OpenAiClient(context).testApiKey()) {
                        "OpenAI Verbindung funktioniert."
                    } else {
                        "OpenAI Verbindung fehlgeschlagen."
                    }
                }
            }) {
                Text("Testen")
            }
            OutlinedButton(onClick = {
                settings.clearOpenAiKey()
                openAiKey = ""
                status = "OpenAI getrennt."
            }) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Trennen")
            }
        }

        Divider()

        Text("E-Mail Konten konfigurieren", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = googleClientId,
            onValueChange = { googleClientId = it },
            label = { Text("Google Client ID (Gmail)") },
            placeholder = { Text("Eigene Google Client ID eingeben") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                settings.saveGoogleClientId(googleClientId)
                status = "Google Client ID gespeichert."
            }) {
                Text("Speichern")
            }
            OutlinedButton(onClick = {
                settings.clearGoogleClientId()
                googleClientId = ""
                status = "Google Client ID zurückgesetzt."
            }) {
                Text("Zurücksetzen")
            }
        }

        Button(onClick = {
            var activity: Activity? = null
            var currentContext = context
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is Activity) {
                    activity = currentContext
                    break
                }
                currentContext = currentContext.baseContext
            }
            settings.saveDefaultMailProvider("gmail")
            activity?.startActivity(mailManager.buildGmailLoginIntent())
        }) {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Gmail verbinden")
        }

        OutlinedButton(onClick = {
            mailManager.disconnectGmail()
            status = "Gmail getrennt."
        }) {
            Text("Gmail trennen")
        }

        Divider()

        OutlinedTextField(
            value = microsoftClientId,
            onValueChange = { microsoftClientId = it },
            label = { Text("Microsoft Client ID (Outlook)") },
            placeholder = { Text("Eigene Microsoft Client ID eingeben") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                settings.saveMicrosoftClientId(microsoftClientId)
                status = "Microsoft Client ID gespeichert."
            }) {
                Text("Speichern")
            }
            OutlinedButton(onClick = {
                settings.clearMicrosoftClientId()
                microsoftClientId = ""
                status = "Microsoft Client ID zurückgesetzt."
            }) {
                Text("Zurücksetzen")
            }
        }

        Button(onClick = {
            var activity: Activity? = null
            var currentContext = context
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is Activity) {
                    activity = currentContext
                    break
                }
                currentContext = currentContext.baseContext
            }
            settings.saveDefaultMailProvider("outlook")
            activity?.startActivity(mailManager.buildOutlookLoginIntent())
        }) {
            Icon(Icons.Default.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Outlook verbinden")
        }

        OutlinedButton(onClick = {
            mailManager.disconnectOutlook()
            status = "Outlook getrennt."
        }) {
            Text("Outlook trennen")
        }

        if (status.isNotBlank()) {
            Text(status)
        }
    }
}
