import sys

with open('/tmp/bad_service.kt', 'r') as f:
    lines = f.readlines()

def safe_remove_first(start_str):
    idx = -1
    for i, l in enumerate(lines):
        if start_str in l:
            idx = i
            break
    if idx == -1: return
    # Find matching brace
    braces = 0
    started = False
    end_idx = idx
    for i in range(idx, len(lines)):
        if '{' in lines[i]:
            started = True
            braces += lines[i].count('{')
        if '}' in lines[i]:
            braces -= lines[i].count('}')
        if started and braces == 0:
            end_idx = i
            break
    for i in range(idx, end_idx+1):
        lines[i] = "\n"

safe_remove_first("private fun updateBubble()")
safe_remove_first("private fun showBubble()")
safe_remove_first("private fun removeBubble()")
safe_remove_first("override fun onCreate()")
safe_remove_first("override fun onDestroy()")
safe_remove_first("var isAppInForeground")
safe_remove_first("private fun updateBubbleVisibility()")

# For instance, just remove the line
for i, l in enumerate(lines):
    if "private var instance: DialerInCallService? = null" in l:
        lines[i] = "\n"
        break
        
# For windowManager and floatingView
for i, l in enumerate(lines):
    if "private var windowManager" in l:
        lines[i] = "\n"
        break
for i, l in enumerate(lines):
    if "private var floatingView" in l:
        lines[i] = "\n"
        break

# Write to file
with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.writelines(lines)
