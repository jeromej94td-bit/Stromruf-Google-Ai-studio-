package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.util.SecureIntegrationSettings

private val BgDark = Color(0xFF0D1117)
private val SurfaceDark = Color(0xFF161B22)
private val CardBorderDark = Color(0xFF30363D)
private val GeminiPurple = Color(0xFF8B5CF6)
private val TextMuted = Color(0xFF8B949E)

@Composable
fun GeminiApiKeyDialog(
    onDismiss: () -> Unit,
    onKeySaved: (String) -> Unit
) {
    val context = LocalContext.current
    val secureSettings = remember { SecureIntegrationSettings(context) }
    var keyInput by remember { mutableStateOf(secureSettings.getGeminiKey() ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = BgDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GeminiPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Google Gemini API-Schlüssel",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Für blitzschnelle Audio-Transkription & Notizen",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Text(
                    text = "Mit Gemini Flash werden Telefonate in Sekunden wortgetreu transkribiert und in übersichtliche Notizen mit To-Dos umgewandelt (im Free-Tier komplett kostenlos!).",
                    fontSize = 12.sp,
                    color = Color(0xFFC9D1D9),
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API Key (AIzaSy...)") },
                    placeholder = { Text("AIzaSy...") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = GeminiPurple) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GeminiPurple,
                        unfocusedBorderColor = CardBorderDark,
                        focusedLabelColor = GeminiPurple,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                // Get free key link
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = GeminiPurple)
                    Spacer(Modifier.width(6.dp))
                    Text("Kostenlosen Gemini API-Key in Google AI Studio holen", fontSize = 11.sp, color = GeminiPurple)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                    ) {
                        Text("Abbrechen", color = TextMuted)
                    }

                    Button(
                        onClick = {
                            if (keyInput.isNotBlank()) {
                                secureSettings.saveGeminiKey(keyInput)
                                Toast.makeText(context, "Gemini API-Schlüssel gespeichert! 💾", Toast.LENGTH_SHORT).show()
                                onKeySaved(keyInput)
                            } else {
                                Toast.makeText(context, "Bitte einen Schlüssel eingeben", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = keyInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GeminiPurple)
                    ) {
                        Text("Speichern", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
