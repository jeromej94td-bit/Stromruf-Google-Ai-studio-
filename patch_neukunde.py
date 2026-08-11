import sys

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'r') as f:
    content = f.read()

target = '''            item { SectionHeader("NEUKUNDEN · ${filtered.size}") }'''
replacement = '''            item { 
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SectionHeader("NEUKUNDEN · ${filtered.size}") 
                    androidx.compose.material3.TextButton(onClick = onAddNeukunde) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Neuen Neukunden anlegen", tint = Cyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Neuer Lead", color = Cyan, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }'''
content = content.replace(target, replacement)

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'w') as f:
    f.write(content)
