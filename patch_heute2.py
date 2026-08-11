import sys
import re

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'r') as f:
    content = f.read()

# Replace the AnimatedVisibility block with nothing
content = re.sub(r'AnimatedVisibility\(visible = showQuickDial\) \{.*?\}\n        \}', '', content, flags=re.DOTALL)
content = re.sub(r'item \{\n            AnimatedVisibility.*?\}', '', content, flags=re.DOTALL)

# Insert DialerCard at the top inside LazyColumn
content = content.replace('// ---- KI-Empfehlung', 'item { DialerCard(viewModel) }\n        // ---- KI-Empfehlung')

# Fix compile errors from undefined variables
content = re.sub(r'var showQuickDial.*?$\n', '', content, flags=re.MULTILINE)
content = re.sub(r'var quickPhone.*?$\n', '', content, flags=re.MULTILINE)
content = re.sub(r'var quickName.*?$\n', '', content, flags=re.MULTILINE)

# Remove the Direktwahl button completely
content = re.sub(r'SecondaryButton\(\s*"Direktwahl", onClick = \{ showQuickDial = !showQuickDial \},\s*icon = Icons.Default.Dialpad, modifier = Modifier.weight\(1f\)\s*\)', '', content, flags=re.MULTILINE)

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'w') as f:
    f.write(content)
