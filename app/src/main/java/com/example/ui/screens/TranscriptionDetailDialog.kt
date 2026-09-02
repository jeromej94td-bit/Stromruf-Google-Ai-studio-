package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.transcription.TranscriptionResult
import java.text.SimpleDateFormat
import java.util.*

private val BgDark = Color(0xFF0D1117)
private val SurfaceDark = Color(0xFF161B22)
private val CardBorderDark = Color(0xFF30363D)
private val GeminiPurple = Color(0xFF8B5CF6)
private val GeminiBlue = Color(0xFF3B82F6)
private val NeonGreen = Color(0xFF10B981)
private val TextMuted = Color(0xFF8B949E)

@Composable
fun TranscriptionDetailDialog(
    result: TranscriptionResult,
    onDismiss: () -> Unit,
    onReTranscribe: () -> Unit,
    onSaveAsCallNote: ((summary: String, fullTranscript: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Zusammenfassung, 1 = Volltext-Transkript
    val scrollState = rememberScrollState()

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label in Zwischenablage kopiert! 📋", Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Transkript & Notiz teilen"))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(20.dp)),
            color = BgDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar with Gemini Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(GeminiPurple.copy(alpha = 0.25f), GeminiBlue.copy(alpha = 0.25f), SurfaceDark)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GeminiPurple.copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = GeminiPurple,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "KI-Transkript & Gesprächsnotiz",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${result.fileName} • ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(result.timestamp))}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                        }
                    }
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SurfaceDark,
                    contentColor = GeminiPurple,
                    divider = { HorizontalDivider(color = CardBorderDark) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("📋 Zusammenfassung", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("🎙️ Volltext Transkript", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                }

                // Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (selectedTab == 0) {
                            // SUMMARY VIEW
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Notes, contentDescription = null, tint = GeminiPurple, modifier = Modifier.size(20.dp))
                                        Text("Strukturierte Gesprächsnotiz", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    }

                                    HorizontalDivider(color = CardBorderDark)

                                    // Formatted text
                                    Text(
                                        text = result.summary.ifBlank { result.rawText },
                                        color = Color(0xFFE6EDF3),
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        } else {
                            // FULL TRANSCRIPT VIEW
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.FormatQuote, contentDescription = null, tint = GeminiBlue, modifier = Modifier.size(20.dp))
                                        Text("Wortgetreues Gesprächsprotokoll", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    }

                                    HorizontalDivider(color = CardBorderDark)

                                    Text(
                                        text = result.fullTranscript.ifBlank { result.rawText },
                                        color = Color(0xFFE6EDF3),
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Copy Button
                        Button(
                            onClick = {
                                val textToCopy = if (selectedTab == 0) result.summary else result.fullTranscript
                                copyToClipboard(textToCopy, if (selectedTab == 0) "Gesprächsnotiz" else "Transkript")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GeminiPurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Kopieren", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share Button
                        OutlinedButton(
                            onClick = {
                                val shareContent = """
                                    📌 SmartCall Notiz & Transkript: ${result.fileName}
                                    
                                    ${result.summary}
                                    
                                    ---
                                    ${result.fullTranscript}
                                """.trimIndent()
                                shareText(shareContent, "SmartCall Notiz: ${result.fileName}")
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Teilen", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        // Re-transcribe Button
                        OutlinedButton(
                            onClick = onReTranscribe,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Neu auswerten", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
