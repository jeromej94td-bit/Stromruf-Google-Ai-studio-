package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.homesip.HomeSipStatus
import com.example.homesip.HomeSipTrunk

/** Pure UI overlay. It does not change SIP, media, recording, TLS or SRTP. */
@Composable
fun HomeSipCallOverlay() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val trunk = remember(context) { HomeSipTrunk.get(context) }
    val state by trunk.state.collectAsState()

    if (state.status !in setOf(HomeSipStatus.DIALING, HomeSipStatus.RINGING, HomeSipStatus.IN_CALL)) return

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF08111F))
            .padding(32.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SMART CALL", color = Color(0xFF69D2FF), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(36.dp))
                Box(
                    modifier = Modifier.background(Color(0xFF132338), CircleShape).padding(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhoneInTalk, null, tint = Color.White)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    when (state.status) {
                        HomeSipStatus.DIALING -> "Anruf wird aufgebaut …"
                        HomeSipStatus.RINGING -> "Es klingelt …"
                        HomeSipStatus.IN_CALL -> "Im Gespräch"
                        else -> state.message
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text("Easybell · TLS + SRTP", color = Color(0xFFAFC1D4))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { trunk.hangUp() },
                    modifier = Modifier.background(Color(0xFFEF4444), CircleShape).padding(14.dp)
                ) {
                    Icon(Icons.Default.CallEnd, "Auflegen", tint = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Text("Auflegen", color = Color.White)
            }
        }
    }
}
