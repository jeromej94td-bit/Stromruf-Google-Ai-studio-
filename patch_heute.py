import sys

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if "var showQuickDial" in line:
        continue
    if "var quickPhone" in line:
        continue
    if "var quickName" in line:
        continue
    if "val greeting = when" in line:
        skip = True
    if skip and "val timeFmt" in line:
        skip = False
        continue
    if skip:
        continue

    # Skip old Kopfbereich
    if "Row(" in line and "statusBarsPadding" in lines[i+2] and "fillMaxWidth" in lines[i+1]:
        skip = True
    if skip and "IconButton(onClick = onOpenSettings)" in line:
        skip = False
        new_lines.append("            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {\n")
        new_lines.append(line)
        continue

    # Replace old Dialer button
    if 'SecondaryButton(' in line and '"Direktwahl"' in line:
        new_lines.append('                // Direktwahl moved to top\n')
        continue
    if 'icon = Icons.Default.Dialpad, modifier = Modifier.weight(1f)' in line and 'Direktwahl moved' in new_lines[-1]:
        continue
    if ')' in line.strip() and 'Direktwahl moved' in new_lines[-2]:
        continue

    new_lines.append(line)

with open('./app/src/main/java/com/example/ui/screens/HeuteScreen.kt', 'w') as f:
    f.writelines(new_lines)
