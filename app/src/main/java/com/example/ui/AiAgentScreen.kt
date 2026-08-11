package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Obsidian
import com.example.ui.theme.Emerald
import com.example.ui.theme.LocalThemeConfig
import com.example.ui.theme.Cyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.AiAgentClient
import kotlinx.coroutines.launch

data class ChatMessage(
    val sender: String, // "user" or "agent"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(
    modifier: Modifier = Modifier,
    viewModel: com.example.viewmodel.StromrufViewModel,
    context: android.content.Context = LocalContext.current
) {
    val prefs = remember { context.getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE) }
    val themeConfig = LocalThemeConfig.current

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // Default welcome message
    val defaultMessage = ChatMessage(
        sender = "agent",
        content = "🤖 STROMRUF KI-ASSISTENT AKTIVIERT\n\nHallo! Ich bin dein intelligenter Energie-Verkaufs-Assistent. Ich kann:\n• Kontakte verwalten\n• Wiedervorlagen organisieren\n• Anrufe analysieren\n• Statistiken generieren\n\nWie kann ich dir heute helfen?"
    )
    
    var messages by remember { mutableStateOf(listOf(defaultMessage)) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Suggestion Prompts
    val suggestions = listOf(
        "Alle Kontakte",
        "Wiedervorlagen",
        "Statistiken",
        "Neuer Kontakt"
    )

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Obsidian,
                        Obsidian
                    )
                )
            )
    ) {
        // ===== HEADER WITH 3D EFFECT =====
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(bottomEnd = 24.dp, bottomStart = 24.dp)
                ),
            shape = RoundedCornerShape(bottomEnd = 24.dp, bottomStart = 24.dp),
            color = themeConfig.cardBackground,
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Icon + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        themeConfig.primaryColor.copy(alpha = 0.3f),
                                        themeConfig.primaryColor.copy(alpha = 0.1f)
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = themeConfig.primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "STROMRUF",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "KI-Assistent",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                
                // Right: Clear Button
                if (messages.size > 1) {
                    IconButton(
                        onClick = { messages = listOf(defaultMessage) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Chat löschen",
                            tint = themeConfig.secondaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ===== CHAT MESSAGE AREA =====
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages) { message ->
                val isUser = message.sender == "user"
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .shadow(
                                elevation = if (isUser) 8.dp else 6.dp,
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (isUser) 20.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 20.dp
                                )
                            )
                            .testTag(if (isUser) "user_message" else "agent_message"),
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (isUser) 20.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 20.dp
                        ),
                        color = if (isUser) {
                            themeConfig.primaryColor.copy(alpha = 0.9f)
                        } else {
                            themeConfig.cardBackground
                        },
                        tonalElevation = 4.dp
                    ) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) {
                                Color.White
                            } else {
                                TextPrimary
                            },
                            modifier = Modifier.padding(14.dp, 12.dp),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Loading Indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Surface(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(16.dp))
                                .widthIn(max = 300.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = themeConfig.cardBackground
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(16.dp, 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = themeConfig.primaryColor
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "KI analysiert...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // ===== SUGGESTION CHIPS =====
        AnimatedVisibility(
            visible = !isLoading && inputText.isEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.95f),
            exit = fadeOut() + scaleOut(targetScale = 0.95f)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(suggestions) { suggestion ->
                    Surface(
                        modifier = Modifier
                            .height(40.dp)
                            .clickable { inputText = suggestion }
                            .shadow(4.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        color = themeConfig.metalAccent.copy(alpha = 0.15f),
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelMedium,
                                color = themeConfig.primaryColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // ===== MODERN INPUT AREA =====
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            color = themeConfig.cardBackground,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Input Field
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .shadow(3.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = themeConfig.baseBackground.copy(alpha = 0.5f),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            enabled = !isLoading,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        if (inputText.isEmpty()) {
                                            Text(
                                                "Frage stellen...",
                                                color = TextSecondary.copy(alpha = 0.6f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (inputText.isNotEmpty()) {
                                        IconButton(
                                            onClick = { inputText = "" },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Löschen",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                // Send Button - Premium 3D Look
                FloatingActionButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val userPrompt = inputText.trim()
                            inputText = ""
                            messages = messages + ChatMessage(sender = "user", content = userPrompt)
                            isLoading = true

                            scope.launch {
                                try {
                                    val geminiKey = AiAgentClient.getGeminiApiKey(context)
                                    val response = if (geminiKey.isNotBlank()) {
                                        AiAgentClient.executeGeminiInstruction(context, viewModel, userPrompt, geminiKey)
                                    } else {
                                        "⚠️ API-Key nicht konfiguriert. Bitte in Einstellungen eintragen."
                                    }
                                    messages = messages + ChatMessage(sender = "agent", content = response)
                                } catch (e: Exception) {
                                    messages = messages + ChatMessage(
                                        sender = "agent",
                                        content = "❌ Fehler: ${e.localizedMessage ?: "Verbindung fehlgeschlagen"}"
                                    )
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(8.dp, RoundedCornerShape(14.dp))
                        .testTag("send_chat_button"),
                    containerColor = themeConfig.primaryColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Senden",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
