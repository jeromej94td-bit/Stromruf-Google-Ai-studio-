import sys

with open('/tmp/bad_service.kt', 'r') as f:
    lines = f.readlines()

def remove_block(start_line):
    idx = -1
    for i, l in enumerate(lines):
        if start_line in l:
            idx = i
            break
    if idx == -1: return False
    
    braces = 0
    end_idx = -1
    for i in range(idx, len(lines)):
        braces += lines[i].count('{')
        braces -= lines[i].count('}')
        if braces == 0:
            end_idx = i
            break
    if end_idx != -1:
        del lines[idx:end_idx+1]
        return True
    return False

# Remove the SECOND instances of functions
# Actually, the first instances are from my recent injection. Let's remove the FIRST ones or SECOND ones. 
# It's safer to remove the first ones if they are isolated. 
# Wait, let's just wipe the whole file and put it back from a backup? I don't have a backup.
# Let's remove the duplicates by matching exact strings.

def remove_function(func_sig):
    # we will keep the last one
    found = []
    for i, l in enumerate(lines):
        if func_sig in l:
            found.append(i)
    
    while len(found) > 1:
        idx = found.pop(0) # remove the first one
        braces = 0
        end_idx = -1
        for i in range(idx, len(lines)):
            braces += lines[i].count('{')
            braces -= lines[i].count('}')
            if braces == 0:
                end_idx = i
                break
        if end_idx != -1:
            for i in range(idx, end_idx+1):
                lines[i] = "\n" # Blank them out to preserve indices
        # Recalculate
        found = []
        for i, l in enumerate(lines):
            if func_sig in l:
                found.append(i)

remove_function("private fun updateBubble()")
remove_function("private fun showBubble()")
remove_function("private fun removeBubble()")
remove_function("override fun onCreate()")
remove_function("override fun onDestroy()")

# Also `isAppInForeground` which is in companion object
# The error was "Conflicting declarations: var isAppInForeground: Boolean"
# Let's just remove all lines that declare `isAppInForeground` except the last one
found_var = []
for i, l in enumerate(lines):
    if "var isAppInForeground" in l:
        found_var.append(i)

while len(found_var) > 1:
    idx = found_var.pop(0)
    # The setter is multi-line
    end_idx = idx
    braces = 0
    if "{" in lines[idx]:
        braces += 1
    for i in range(idx+1, len(lines)):
        if "{" in lines[i] or "}" in lines[i]:
            braces += lines[i].count('{')
            braces -= lines[i].count('}')
            if braces == 0 and "}" in lines[i]:
                end_idx = i
                break
    for i in range(idx, end_idx+1):
        lines[i] = "\n"
    found_var = []
    for i, l in enumerate(lines):
        if "var isAppInForeground" in l:
            found_var.append(i)

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.writelines(lines)
