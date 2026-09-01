package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.viewmodel.StromrufViewModel

/**
 * Agents-Hauptbereich mit Agenten-Profilen, Live-Telefonie, KI-Assistent,
 * Trainingslabor und Konfiguration.
 */
@Composable
fun AgentsScreen(
    modifier: Modifier = Modifier,
    viewModel: StromrufViewModel? = null
) {
    AgentCallScreen(
        modifier = modifier.fillMaxSize(),
        viewModel = viewModel
    )
}
