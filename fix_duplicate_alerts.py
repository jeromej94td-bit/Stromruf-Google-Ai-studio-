import sys

file_path = "app/src/main/java/com/example/ui/screens/AgentCallScreen.kt"
with open(file_path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# We know the first occurrence is inside OwnCallsTab (around line 187).
# Any occurrence after line 300 should be removed. The block spans from `if (activeOwnCallNumber != null) {` to the matching `}`.
# Looking at the code, the block is exactly 27 lines long.

new_lines = []
skip = 0

for i, line in enumerate(lines):
    if skip > 0:
        skip -= 1
        continue
    
    if i > 250 and "if (activeOwnCallNumber != null) {" in line:
        # We need to skip this line and the next 26 lines
        skip = 26
        # Wait, also we might want to check the line before to remove whitespace?
        continue
    
    new_lines.append(line)

with open(file_path, "w", encoding="utf-8") as f:
    f.writelines(new_lines)
    
print("Removed duplicate dialogs.")
