import sys
import re

with open('./app/src/main/java/com/example/ui/screens/StromrufShell.kt', 'r') as f:
    content = f.read()

# Replace the AnimatedContent block
old_block = '''        Box(Modifier.weight(1f)) {
            AnimatedContent('''
new_block = '''        Box(Modifier.weight(1f)) {
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
            AnimatedContent('''

content = content.replace(old_block, new_block)

# Add closing brace for the `else`
content = content.replace('            }\n        }\n\n        // Navigationsleiste', '            }\n        }\n        }\n\n        // Navigationsleiste')

# Remove "kontakte" -> KontakteScreen(...) block entirely
content = re.sub(r'\s*"kontakte" -> KontakteScreen\([\s\S]*?onRequestFollowUp = onAddFollowUpFor\n\s*\)', '', content)

# Add "statistiken" -> StatistikenScreen(...) block
content = content.replace('''"aktivitaeten" -> AktivitaetenScreen(
                        viewModel = viewModel,
                        onAddFollowUp = onAddFollowUp
                    )''', '''"aktivitaeten" -> AktivitaetenScreen(
                        viewModel = viewModel,
                        onAddFollowUp = onAddFollowUp
                    )
                    "statistiken" -> StatistikenScreen(
                        viewModel = viewModel
                    )''')

# Update hideNav logic
content = content.replace('val hideNav = area == "kontakte" && selectedContact != null', 'val hideNav = selectedContact != null')

# Add import for BarChart
if 'import androidx.compose.material.icons.filled.BarChart' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.Add', 'import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.filled.BarChart')

with open('./app/src/main/java/com/example/ui/screens/StromrufShell.kt', 'w') as f:
    f.write(content)
