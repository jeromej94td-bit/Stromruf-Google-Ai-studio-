package com.example.ui.screens

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.database.ContactEntity
import com.example.ui.design.AppCard
import com.example.ui.design.Dim
import com.example.ui.design.IconDisc
import com.example.ui.design.PrimaryButton
import com.example.ui.design.SecondaryButton
import com.example.ui.design.pulsingAura
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.LocalThemeConfig
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.StromrufViewModel
import java.io.File

/**
 * Gesprächsnotiz-Composer (Premium).
 *
 * Text- und Sprachnotizen werden beim Speichern automatisch im Hintergrund
 * an den verknüpften Telegram-Bot weitergeleitet (Text + Voice-Message)
 * und nach Supabase synchronisiert.
 */
@Composable
fun CallNoteComposer(
    viewModel: StromrufViewModel,
    contact: ContactEntity?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cfg = LocalThemeConfig.current
    val draft by viewModel.customerMessageDraft.collectAsState()
    var nextAppointment by remember { mutableStateOf("") }

    // Audio-Aufnahme
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    val telegramReady = remember { com.example.util.TelegramClient(context).isConfigured() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording(context, { rec, file ->
                mediaRecorder = rec
                audioFile = file
                isRecording = true
            }, {})
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = cfg.primaryColor,
        focusedLabelColor = cfg.primaryColor,
        cursorColor = cfg.primaryColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        unfocusedLabelColor = TextMuted
    )

    Surface(
        tonalElevation = 0.dp,
        color = cfg.cardBackground,
        shape = RoundedCornerShape(Dim.cardRadius)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Kopf
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconDisc(Icons.Default.GraphicEq, cfg.primaryColor, size = 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Gesprächsnotiz", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(
                        contact?.name ?: "Neue Notiz",
                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                    )
                }
            }

            OutlinedTextField(
                value = draft.rawNote,
                onValueChange = viewModel::setCustomerMessageNote,
                label = { Text("Was wurde besprochen?") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                colors = fieldColors
            )

            OutlinedTextField(
                value = nextAppointment,
                onValueChange = { nextAppointment = it },
                label = { Text("Nächster Termin oder Zusage") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            // Sprachnotiz-Aufnahme
            val recBg by animateColorAsState(
                if (isRecording) CriticalRed.copy(alpha = 0.16f) else cfg.primaryColor.copy(alpha = 0.10f),
                tween(200), label = "recBg"
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dim.controlRadius))
                    .background(recBg)
                    .border(
                        1.dp,
                        if (isRecording) CriticalRed.copy(alpha = 0.5f) else cfg.primaryColor.copy(alpha = 0.25f),
                        RoundedCornerShape(Dim.controlRadius)
                    )
                    .clickable {
                        if (isRecording) {
                            stopRecording(mediaRecorder)
                            isRecording = false
                            mediaRecorder = null
                            audioFile?.let {
                                viewModel.transcribeCustomerAudio(context, Uri.fromFile(it))
                            }
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .pulsingAura(CriticalRed, enabled = isRecording, maxRadiusFactor = 0.7f)
                        .clip(CircleShape)
                        .background(if (isRecording) CriticalRed else cfg.primaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        tint = cfg.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (isRecording) "Aufnahme läuft – tippen zum Stoppen"
                        else "Sprachnotiz aufnehmen",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isRecording) CriticalRed else TextPrimary
                    )
                    Text(
                        if (audioFile != null && !isRecording) "Aufnahme bereit – wird mitgesendet"
                        else "Wird transkribiert und als Voice-Message weitergeleitet",
                        style = MaterialTheme.typography.bodySmall, color = TextMuted
                    )
                }
            }

            if (draft.transcript.isNotBlank()) {
                OutlinedTextField(
                    value = draft.transcript,
                    onValueChange = {},
                    label = { Text("Transkript") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    colors = fieldColors
                )
            }

            SecondaryButton(
                text = if (draft.isLoading) "Wird erstellt…" else "Kundennachricht mit KI erstellen",
                icon = Icons.Default.AutoAwesome,
                onClick = {
                    if (!draft.isLoading) {
                        viewModel.generateCustomerMessage(
                            context = context,
                            contact = contact,
                            nextAppointment = nextAppointment.takeIf { it.isNotBlank() }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = draft.subject,
                onValueChange = viewModel::setGeneratedCustomerSubject,
                label = { Text("Betreff") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors
            )

            OutlinedTextField(
                value = draft.body,
                onValueChange = viewModel::setGeneratedCustomerBody,
                label = { Text("Nachricht an Kunde") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                colors = fieldColors
            )

            draft.error?.let {
                Text(it, color = CriticalRed, style = MaterialTheme.typography.bodySmall)
            }

            // Telegram-Hinweis
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.Send, null,
                    tint = if (telegramReady) SuccessGreen else TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (telegramReady)
                        "Wird beim Speichern automatisch an deinen Telegram-Bot weitergeleitet"
                    else
                        "Telegram nicht verbunden – Notiz wird nur lokal & in der Cloud gespeichert",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (telegramReady) SuccessGreen else TextMuted
                )
            }

            PrimaryButton(
                text = "Notiz speichern",
                icon = Icons.Default.Save,
                onClick = {
                    viewModel.saveCustomerMessageDraft(contact, audioFile)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.rawNote.isNotBlank() || draft.transcript.isNotBlank() || audioFile != null
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Gmail",
                    icon = Icons.Default.Email,
                    onClick = {
                        viewModel.sendCustomerMessage(context, contact, "gmail")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Outlook",
                    icon = Icons.Default.Email,
                    onClick = {
                        viewModel.sendCustomerMessage(context, contact, "outlook")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Schließen", color = TextSecondary)
            }
        }
    }
}

private fun startRecording(context: Context, onSuccess: (MediaRecorder, File) -> Unit, onError: () -> Unit) {
    try {
        val file = File(context.cacheDir, "stromruf_note_${System.currentTimeMillis()}.m4a")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setOutputFile(file.absolutePath)

        recorder.prepare()
        recorder.start()

        onSuccess(recorder, file)
    } catch (e: Exception) {
        e.printStackTrace()
        onError()
    }
}

private fun stopRecording(recorder: MediaRecorder?) {
    try {
        recorder?.stop()
        recorder?.release()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
