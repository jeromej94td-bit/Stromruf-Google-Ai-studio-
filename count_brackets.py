import sys

file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

open_b = content.count("{")
close_b = content.count("}")

print(f"Open: {open_b}, Close: {close_b}")
