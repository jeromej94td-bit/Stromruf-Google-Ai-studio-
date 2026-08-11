import sys

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'r') as f:
    content = f.read()

# Add pulsating aura to the "Nächster Fokus-Anruf" card
# It starts with "AppCard(accent = Emerald) {" around line 159
content = content.replace('AppCard(accent = Emerald) {', 'AppCard(modifier = Modifier.pulsatingAura(EmeraldDim), accent = Emerald) {')

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'w') as f:
    f.write(content)
