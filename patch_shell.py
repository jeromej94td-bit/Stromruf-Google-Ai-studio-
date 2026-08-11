import sys

with open('./app/src/main/java/com/example/ui/screens/StromrufShell.kt', 'r') as f:
    content = f.read()

content = content.replace('NavArea("kontakte", "Kontakte", Icons.Default.Groups)', 'NavArea("statistiken", "Statistik", androidx.compose.material.icons.Icons.Default.BarChart)')

content = content.replace('''                    "kontakte" -> KontakteScreen(
                        viewModel = viewModel,
                        selectedContact = selectedContact,
                        onSelectContact = { selectedContact = it },
                        onAddContact = onAddContact,
                        onEditContact = onEditContact
                    )''', '''                    "statistiken" -> StatistikenScreen(
                        viewModel = viewModel
                    )''')

content = content.replace('''onOpenContact = { selectedContact = it; area = "kontakte" }''', '''onOpenContact = { selectedContact = it }''')

with open('./app/src/main/java/com/example/ui/screens/StromrufShell.kt', 'w') as f:
    f.write(content)
