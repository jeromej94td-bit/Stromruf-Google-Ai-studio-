import sys

file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

bad_block = """            }
        }
    }
    
    if (activeOwnCallNumber != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { androidx.compose.material3.Text("Eigener SIP Anruf", color = Color.White) },
            text = { 
                Column {
                    androidx.compose.material3.Text("Aktiver Anruf mit: $activeOwnCallNumber", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text("Dauer: ${ownCallDuration}s", color = Color.Gray)
                    if (recordCalls) {
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.Text("⏺️ Aufzeichnung läuft", color = Color.Red, androidx.compose.ui.text.font.FontWeight.Bold)
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

original_block = """            }
        }
    }
}"""

# Revert all occurrences
content = content.replace(bad_block, original_block)

# Now, we manually insert the dialog ONLY at the end of OwnCallsTab
# We find the end of OwnCallsTab by looking for "// TAB 1: LIVE"
if "// TAB 1: LIVE" in content:
    # OwnCallsTab ends just before this.
    # It looks like:
    #             }
    #         }
    #     }
    # }
    # 
    # // TAB 1: LIVE
    
    parts = content.split("// TAB 1: LIVE")
    own_calls_part = parts[0]
    
    # Replace the LAST occurrence of original_block in own_calls_part
    last_idx = own_calls_part.rfind(original_block)
    if last_idx != -1:
        own_calls_part = own_calls_part[:last_idx] + bad_block + own_calls_part[last_idx + len(original_block):]
    
    content = own_calls_part + "// TAB 1: LIVE" + parts[1]

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Fixed AgentCallScreen")
