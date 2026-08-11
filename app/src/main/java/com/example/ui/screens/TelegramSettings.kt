package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.util.SecureIntegrationSettings
import com.example.util.TelegramClient
import kotlinx.coroutines.launch

/**
 * Telegram-Verknüpfung in den Einstellungen.
 *
 * Ablauf:
 *  1. Bot-Token (von @BotFather) eintragen und speichern.
 *  2. "Mit Telegram verbinden" tippen:
 *     - Die App leitet dich zu Telegram weiter (App, oder Telegram-Website
 *       mit Login per Telefonnummer, falls keine App installiert ist).
 *     - Dort im Bot-Chat auf "Start" tippen – DEIN Account schickt dem Bot
 *       die Nachricht.
 *     - Die App erkennt das im Hintergrund automatisch und speichert deine
 *       Chat-ID.
 *  3. Fertig – Notizen landen ab jetzt in deinem Chat mit dem Bot.
 */
@Composable
fun TelegramSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SecureIntegrationSettings(context) }
    val telegram = remember { TelegramClient(context) }

    var botToken by remember { mutableStateOf(settings.getTelegramBotToken() ?: "") }
    var chatId by remember { mutableStateOf(settings.getTelegramChatId() ?: "") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var waitingForConnect by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Telegram-Verknüpfung", style = MaterialTheme.typography.titleLarge)

        Text(
            "1. Bot-Token von @BotFather eintragen und speichern.\n" +
                "2. \"Mit Telegram verbinden\" tippen – du wirst zu Telegram " +
                "weitergeleitet (App oder Website mit Telefonnummer-Login).\n" +
                "3. Dort im Bot-Chat auf \"Start\" tippen. Die App erkennt die " +
                "Verbindung automatisch.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = botToken,
            onValueChange = { botToken = it },
            label = { Text("Telegram Bot-Token") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && botToken.isNotBlank(),
                onClick = {
                    settings.saveTelegramBotToken(botToken)
                    status = "Bot-Token gespeichert."
                }
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Speichern")
            }

            Button(
                enabled = !busy && botToken.isNotBlank(),
                onClick = {
                    settings.saveTelegramBotToken(botToken)
                    scope.launch {
                        busy = true
                        waitingForConnect = true
                        status = "Prüfe Bot…"
                        val botInfo = telegram.getBotUsernameDetailed()
                        if (!botInfo.ok || botInfo.detail.isBlank()) {
                            status = "Bot nicht erreichbar: ${botInfo.detail}"
                            busy = false
                            waitingForConnect = false
                            return@launch
                        }
                        // Weiterleitung zu Telegram: öffnet die App, oder die
                        // Telegram-Website (Login per Telefonnummer), falls
                        // keine App installiert ist.
                        val link = telegram.buildBotLink(botInfo.detail)
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            status = "Telegram konnte nicht geöffnet werden."
                            busy = false
                            waitingForConnect = false
                            return@launch
                        }
                        status = "Warte auf deine \"Start\"-Nachricht an @${botInfo.detail}…"
                        // Im Hintergrund auf die Nachricht des Nutzers warten
                        val result = telegram.waitForPrivateChatDetailed(timeoutMs = 90_000)
                        if (result.ok) {
                            chatId = result.detail
                            status = "✓ Verbunden! Chat-ID: ${result.detail}"
                            telegram.sendMessageDetailed(
                                "✅ STROMRUF ist jetzt mit deinem Telegram verbunden."
                            )
                        } else {
                            status = result.detail
                        }
                        busy = false
                        waitingForConnect = false
                    }
                }
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Mit Telegram verbinden")
            }
        }

        if (waitingForConnect) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Tippe in Telegram auf \"Start\" – ich warte hier…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = chatId,
            onValueChange = {
                chatId = it
                if (it.isNotBlank()) settings.saveTelegramChatId(it)
            },
            label = { Text("Chat-ID (wird automatisch gefüllt)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // ---- Automatische Hintergrund-Weiterleitung ----
        var autoForward by remember { mutableStateOf(settings.isTelegramAutoForward()) }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Notizen automatisch weiterleiten", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Jede Gesprächs- und Sprachnotiz wird im Hintergrund an deinen Bot gesendet",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = autoForward,
                onCheckedChange = {
                    autoForward = it
                    settings.setTelegramAutoForward(it)
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !busy && botToken.isNotBlank() && chatId.isNotBlank(),
                onClick = {
                    settings.saveTelegramBotToken(botToken)
                    settings.saveTelegramChatId(chatId)
                    scope.launch {
                        busy = true
                        status = "Sende Testnachricht…"
                        val result = telegram.sendMessageDetailed("✅ Testnachricht aus STROMRUF.")
                        status = if (result.ok) "Testnachricht gesendet – schau in Telegram!"
                        else "Senden fehlgeschlagen: ${result.detail}"
                        busy = false
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Test senden")
            }

            TextButton(
                enabled = !busy,
                onClick = {
                    settings.clearTelegram()
                    botToken = ""
                    chatId = ""
                    status = "Telegram-Verknüpfung entfernt."
                }
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Entfernen")
            }
        }

        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
