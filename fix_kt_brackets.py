import sys

file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
depth = 0
removed = 0

for line in lines:
    # count { and } ignoring strings (simple heuristic, assuming no complex strings)
    # Actually, let's just count blindly
    o = line.count("{")
    c = line.count("}")
    
    if depth + o - c < 0:
        # this line has an extra closing bracket
        # Let's replace one } with nothing, or just comment the line if it's only }
        if line.strip() == "}":
            removed += 1
            continue
        elif line.strip() == "} // extra":
            continue
        else:
            # Maybe there are multiple brackets or code
            pass
    
    depth += o
    depth -= c
    new_lines.append(line)

print(f"Removed {removed} extra closing brackets.")
print(f"Final depth: {depth}")

with open(file_path, "w", encoding="utf-8") as f:
    f.writelines(new_lines)

