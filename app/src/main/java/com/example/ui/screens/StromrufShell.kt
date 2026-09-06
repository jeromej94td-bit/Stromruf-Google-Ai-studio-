package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.database.ContactEntity
import com.example.ui.design.NavArea
import com.example.ui.design.StromrufNavBar
import com.example.viewmodel.StromrufViewModel

/**
 * Zentrale Navigations-Shell. Vier Hauptbereiche + kontextbezogene Detailtiefe.
 * Ersetzt die alte 5-Tab-Struktur (Anrufen/Wiedervorlage/Kalender/Historie/Hotbox).
 */
@Composable
fun StromrufShell(
    viewModel: StromrufViewModel,
    modifier: Modifier = Modifier,
    onAddContact: () -> Unit,
    onAddFollowUp: () -> Unit,
    onAddFollowUpFor: (ContactEntity) -> Unit,
    onImportContacts: () -> Unit,
    onEditContact: (ContactEntity) -> Unit,
    onAddNeukunde: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiChat: () -> Unit
) {
    var area by rememberSaveable { mutableStateOf("heute") }
    var selectedContact by remember { mutableStateOf<ContactEntity?>(null) }

    androidx.activity.compose.BackHandler(enabled = area != "heute" || selectedContact != null) {
        if (selectedContact != null) {
            selectedContact = null
        } else {
            area = "heute"
        }
    }

    val followUps by viewModel.activeFollowUps.collectAsState()
    val now = System.currentTimeMillis()
    val dueCount = followUps.count { it.dueAt <= now }

    val areas = listOf(
        NavArea("heute", "Heute", Icons.Default.Today),
        NavArea("leads", "Leads", Icons.Default.TrendingUp),
        NavArea("aktivitaeten", "Aktivität", Icons.AutoMirrored.Filled.List),
        NavArea("statistiken", "Statistik", Icons.Default.BarChart),
        NavArea("agents", "Agents", Icons.Default.SupportAgent)
    )

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (selectedContact != null) {
                KontakteScreen(
                    viewModel = viewModel,
                    selectedContact = selectedContact,
                    onSelectContact = { selectedContact = it },
                    onAddContact = onAddContact,
                    onImportContacts = onImportContacts,
                    onEditContact = onEditContact,
                    onRequestFollowUp = onAddFollowUpFor
                )
            } else {
                val navOrder = listOf("heute", "leads", "aktivitaeten", "statistiken", "agents")
                AnimatedContent(
                    targetState = area,
                    transitionSpec = {
                        val dir = navOrder.indexOf(targetState) - navOrder.indexOf(initialState)
                        val slide = if (dir >= 0) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(340, easing = FastOutSlowInEasing)
                        ) { it / 5 * slide } + fadeIn(tween(280)) + scaleIn(
                            initialScale = 0.96f, animationSpec = tween(340, easing = FastOutSlowInEasing)
                        )) togetherWith (slideOutHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) { -it / 6 * slide } + fadeOut(tween(200)))
                    },
                    label = "areaSwitch"
                ) { current ->
                    when (current) {
                        "heute" -> HeuteScreen(
                            viewModel = viewModel,
                            onNavigate = { area = it },
                            onAddContact = onAddContact,
                            onAddFollowUp = onAddFollowUp,
                            onOpenSettings = onOpenSettings,
                            onOpenAiChat = onOpenAiChat
                        )
                        "leads" -> LeadsScreen(
                            viewModel = viewModel,
                            onOpenContact = { selectedContact = it },
                            onAddNeukunde = onAddNeukunde
                        )
                        "aktivitaeten" -> AktivitaetenCalendarHost(
                            viewModel = viewModel,
                            onAddFollowUp = onAddFollowUp
                        )
                        "statistiken" -> StatistikenScreen(
                            viewModel = viewModel
                        )
                        "agents" -> AgentsScreen(
                            viewModel = viewModel
                        )
                    }
                }
            }

            HomeSipCallOverlay()
        }

        // Navigationsleiste ausblenden, solange ein Kontakt im Fokus ist (mehr Fläche).
        val hideNav = selectedContact != null
        if (!hideNav) {
            StromrufNavBar(
                areas = areas,
                activeKey = area,
                onSelect = { area = it },
                badgeCounts = mapOf("aktivitaeten" to dueCount)
            )
        }
    }
}
