with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'r') as f:
    content = f.read()

import re
content = re.sub(r'\s*item \{\s*\}\s*\}', '\n    }\n}', content)
# Just to be safe, find if there's any orphaned braces

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'w') as f:
    f.write(content)
