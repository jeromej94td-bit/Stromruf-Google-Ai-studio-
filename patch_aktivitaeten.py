import sys

with open('./app/src/main/java/com/example/ui/screens/AktivitaetenScreen.kt', 'r') as f:
    content = f.read()

# Change the SegmentedControl options
content = content.replace('listOf("Geplant", "Verlauf")', 'listOf("Verlauf", "Geplant")')
content = content.replace('if (mode == 0) plannedSection(followUps, viewModel)\n        else historySection(callLogs)', 'if (mode == 1) plannedSection(followUps, viewModel)\n        else historySection(callLogs)')

with open('./app/src/main/java/com/example/ui/screens/AktivitaetenScreen.kt', 'w') as f:
    f.write(content)
