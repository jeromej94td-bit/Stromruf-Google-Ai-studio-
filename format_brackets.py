import sys

file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    text = f.read()

# We will parse character by character. If we see '{', depth++. If '}', depth--.
# If depth goes < 0, we drop the '}'.
new_text = []
depth = 0
for char in text:
    if char == '{':
        depth += 1
        new_text.append(char)
    elif char == '}':
        if depth > 0:
            depth -= 1
            new_text.append(char)
        else:
            # Skip this bracket
            pass
    else:
        new_text.append(char)

# If there are open brackets left at the end, append closing brackets
while depth > 0:
    new_text.append('\n}')
    depth -= 1

with open(file_path, "w", encoding="utf-8") as f:
    f.write("".join(new_text))

print("Fixed brackets at char level!")
