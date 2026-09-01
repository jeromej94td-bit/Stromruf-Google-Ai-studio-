import sys, re

file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    text = f.read()

# The file contains "// TAB:" or "// TAB " comments.
# Let's split it by "// TAB" to separate the composables.
# Wait, let's just split by "@Composable\nprivate fun" to get each function!
# And then for each function, count '{' and '}'. If it's missing '}', add it. If it has too many, remove them!

parts = re.split(r'(?=@Composable\s*\n\s*private fun)', text)

new_text = []

for i, part in enumerate(parts):
    if i == 0:
        # The first part is everything up to the first private fun (which is OwnCallsTab or AgentenTab?)
        # Let's just balance it.
        pass
        
    # For every part, balance brackets:
    open_c = part.count("{")
    close_c = part.count("}")
    
    if open_c > close_c:
        # Missing closing brackets
        part += "\n" + "}" * (open_c - close_c) + "\n"
    elif close_c > open_c:
        # Too many closing brackets!
        # We need to remove from the end!
        diff = close_c - open_c
        # Find the last `diff` closing brackets and remove them.
        for _ in range(diff):
            part = "".join(part.rsplit("}", 1))
            
    new_text.append(part)

with open(file_path, "w", encoding="utf-8") as f:
    f.write("".join(new_text))

print("Tabs balanced independently!")
