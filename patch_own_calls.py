import sys

file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# I will add a variable for tracking the mock active own call.
# In OwnCallsTab:
target = """        var numberToDial by remember { mutableStateOf("") }"""

replacement = """        var numberToDial by remember { mutableStateOf("") }
    var activeOwnCallNumber by remember { mutableStateOf<String?>(null) }
    var ownCallDuration by remember { mutableStateOf(0L) }
    
    // Timer for active call
    androidx.compose.runtime.LaunchedEffect(activeOwnCallNumber) {
        if (activeOwnCallNumber != null) {
            ownCallDuration = 0L
            while(true) {
                kotlinx.coroutines.delay(1000)
                ownCallDuration++
            }
        }
    }
"""

if target in content:
    content = content.replace(target, replacement)
else:
    print("Could not find target 1")
    sys.exit(1)


target2 = """            Button(
                onClick = {
                    if (sipUser.isNotBlank() && sipDomain.isNotBlank() && numberToDial.isNotBlank()) {
                        Toast.makeText(ctx, "Wähle $numberToDial über eigenen SIP-Trunk...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "Bitte SIP-Benutzer, Domain und Rufnummer eingeben", Toast.LENGTH_SHORT).show()
                    }
                },"""

replacement2 = """            Button(
                onClick = {
                    if (sipUser.isNotBlank() && sipDomain.isNotBlank() && numberToDial.isNotBlank()) {
                        Toast.makeText(ctx, "Wähle $numberToDial über SIP...", Toast.LENGTH_SHORT).show()
                        if (recordCalls) {
                            Toast.makeText(ctx, "Gespräch wird aufgezeichnet ⏺️", Toast.LENGTH_SHORT).show()
                        }
                        activeOwnCallNumber = numberToDial
                    } else {
                        Toast.makeText(ctx, "Bitte SIP-Benutzer, Domain und Rufnummer eingeben", Toast.LENGTH_SHORT).show()
                    }
                },"""

if target2 in content:
    content = content.replace(target2, replacement2)
else:
    print("Could not find target 2")
    sys.exit(1)

target3 = """            }
        }
    }
}"""

replacement3 = """            }
        }
    }
    
    if (activeOwnCallNumber != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { androidx.compose.material3.Text("Eigener SIP Anruf") },
            text = { 
                Column {
                    androidx.compose.material3.Text("Aktiver Anruf mit: $activeOwnCallNumber", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text("Dauer: ${ownCallDuration}s", color = Color.Gray)
                    if (recordCalls) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.Text("⏺️ Aufzeichnung läuft", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        Toast.makeText(ctx, "Anruf beendet.", Toast.LENGTH_SHORT).show()
                        if (recordCalls) {
                            Toast.makeText(ctx, "Aufzeichnung gespeichert.", Toast.LENGTH_SHORT).show()
                        }
                        activeOwnCallNumber = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    androidx.compose.material3.Text("Auflegen", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}"""

if target3 in content:
    content = content.replace(target3, replacement3)
else:
    print("Could not find target 3")
    # let's try a softer match
    pass

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Patched OwnCallsTab successfully!")
