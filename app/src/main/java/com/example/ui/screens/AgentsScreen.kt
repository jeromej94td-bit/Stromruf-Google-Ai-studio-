package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.viewmodel.StromrufViewModel

/**
 * Sichtbarer Agents-Hub. Smart Calls ist bewusst der erste und standardmäßig
 * geöffnete Bereich; die bestehende Agenten-/Live-/KI-Oberfläche bleibt unverändert
 * unter "Agenten & KI" erhalten.
 */
@Composable
fun AgentsScreen(
    modifier: Modifier = Modifier,
    viewModel: StromrufViewModel? = null
) {
    var bereich by rememberSaveable { mutableStateOf(0) }
    val hintergrund = Color(0xFF0F172A)
    val karte = Color(0xFF1E293B)
    val akzent = Color(0xFF59C7F3)

    Column(modifier.fillMaxSize().background(hintergrund)) {
        ScrollableTabRow(
            selectedTabIndex = bereich,
            containerColor = karte,
            contentColor = akzent,
            edgePadding = androidx.compose.ui.unit.Dp(8f)
        ) {
            Tab(
                selected = bereich == 0,
                onClick = { bereich = 0 },
                text = { Text("Smart Calls", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = bereich == 1,
                onClick = { bereich = 1 },
                text = { Text("Agenten & KI", fontWeight = FontWeight.SemiBold) }
            )
        }

        when (bereich) {
            0 -> SmartGespracheTab(modifier = Modifier.fillMaxSize())
            else -> AgentCallScreen(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel
            )
        }
    }
}
