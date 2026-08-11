package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.AuthScreen
import com.example.util.SupabaseAuthClient

/** Complete Agents tab with authentication, test laboratory and configuration. */
@Composable
fun AgentsScreen() {
    val context = LocalContext.current
    var authenticated by rememberSaveable {
        mutableStateOf(SupabaseAuthClient.getSessionToken(context) != null)
    }

    if (!authenticated) {
        AuthScreen { _, _ -> authenticated = true }
        return
    }

    var agentSubTab by rememberSaveable { mutableStateOf("training") }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = agentSubTab == "training",
                onClick = { agentSubTab = "training" },
                label = { Text("Trainingslabor") },
            )
            FilterChip(
                selected = agentSubTab == "settings",
                onClick = { agentSubTab = "settings" },
                label = { Text("So arbeitet der Agent") },
            )
        }

        when (agentSubTab) {
            "settings" -> AgentEinstellungenScreen()
            else -> AgentTrainingScreen()
        }
    }
}
