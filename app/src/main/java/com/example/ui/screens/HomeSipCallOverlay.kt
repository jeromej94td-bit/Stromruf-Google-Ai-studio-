package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.homesip.HomeSipCallUiState
import com.example.homesip.HomeSipStatus
import com.example.homesip.HomeSipTrunk

/**
 * Full-screen UI only. It observes the Gold-Master SIP state and can request hangup,
 * but it never changes registration, media, TLS/SRTP or the Linphone call lifecycle.
 */
@Composable
fun HomeSipCallOverlay() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val trunk = androidx.compose.runtime.remember(context) { HomeSipTrunk.get(context) }
    val state by trunk.state.collectAsState()
    val info by HomeSipCallUiState.info.collectAsState()

    if (state.status !in setOf(
            HomeSipStatus.DIALING,
            HomeSipStatus.RINGING,
            HomeSipStatus.IN_CALL
        )
    ) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08111F))
            .padding(horizontal = 28.dp, vertical = 42.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (info.smartCall) "SMART CALL" else "SIP-ANRUF",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF69D2FF)
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF132338), CircleShape)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = info.contactName ?: info.phone.ifBlank { "SIP-Anruf" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (info.contactName != null && info.phone.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(info.phone, color = Color(0xFFAFC1D4))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = when (state.status) {
                        HomeSipStatus.DIALING -> "Anruf wird aufgebaut …"
                        HomeSipStatus.RINGING -> "Es klingelt …"
                        HomeSipStatus.IN_CALL -> "Im Gespräch · TLS + SRTP"
                        else -> state.message
                    },
                    color = Color(0xFFAFC1D4),
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = { trunk.hangUp() },
                    modifier = Modifier
                        .background(Color(0xFFEF4444), CircleShape)
                        .padding(14.dp)
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = "Auflegen",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Auflegen", color = Color.White)
            }
        }
    }
}
