package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun McpSettings() {
    val context = LocalContext.current
    var edgeFunctionName by remember { mutableStateOf("stromruf-mcp") }
    
    val baseUrl = "https://yepluyipizbbrgoffqdq.supabase.co"
    val functionsUrl = "$baseUrl/functions/v1"
    
    val authUrl = "$functionsUrl/$edgeFunctionName/auth"
    val tokenUrl = "$functionsUrl/$edgeFunctionName/token"
    val regUrl = "$functionsUrl/$edgeFunctionName/register"
    val resource = "urn:stromruf:mcp"
    
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    fun copyToClipboard(label: String, text: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, "$label kopiert", Toast.LENGTH_SHORT).show()
    }
    
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ChatGPT MCP Server (OAuth)", style = MaterialTheme.typography.titleLarge)
        Text("Kopiere diese URLs in die ChatGPT Plugin/MCP Einstellungen, um den AI Agent anzubinden.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        OutlinedTextField(
            value = edgeFunctionName,
            onValueChange = { edgeFunctionName = it },
            label = { Text("Edge Function Name (z.B. stromruf-mcp)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        McpUrlRow("Auth-URL", authUrl) { copyToClipboard("Auth-URL", authUrl) }
        McpUrlRow("Token-URL", tokenUrl) { copyToClipboard("Token-URL", tokenUrl) }
        McpUrlRow("Registrierungs-URL", regUrl) { copyToClipboard("Registrierungs-URL", regUrl) }
        McpUrlRow("Basis-URL des Autorisierungsservers", baseUrl) { copyToClipboard("Basis-URL", baseUrl) }
        McpUrlRow("Ressource", resource) { copyToClipboard("Ressource", resource) }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "💡 Tipp für ChatGPT Custom GPTs:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Falls ChatGPT meldet, dass 'Dynamic Client Registration (RFC 7591)' nicht unterstützt wird:\n\n" +
                           "Wähle bei den OAuth-Einstellungen in ChatGPT einfach die Option 'Statisch' / 'Manuelle Registrierung' anstatt 'Dynamisch'.\n\n" +
                           "Dort kannst du eine feste Client-ID und ein Client-Secret deiner Wahl eintragen, um die Verbindung manuell abzusichern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun McpUrlRow(label: String, url: String, onCopy: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    }
}
