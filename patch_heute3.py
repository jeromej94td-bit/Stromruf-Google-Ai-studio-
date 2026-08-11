import sys

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if 'Text(greeting' in line or 'Text(dateLabel' in line:
        continue
    new_lines.append(line)

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'w') as f:
    f.writelines(new_lines)
