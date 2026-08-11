import sys

with open('/tmp/bad_service.kt', 'r') as f:
    lines = f.readlines()

def get_block(start_match, lines_list, start_idx=0):
    idx = -1
    for i in range(start_idx, len(lines_list)):
        if start_match in lines_list[i]:
            idx = i
            break
    if idx == -1: return -1, -1
    braces = 0
    if '{' not in lines_list[idx]:
        for i in range(idx+1, len(lines_list)):
            if '{' in lines_list[i]:
                braces += lines_list[i].count('{')
                braces -= lines_list[i].count('}')
                if braces == 0: return idx, i
                break
    
    for i in range(idx, len(lines_list)):
        braces += lines_list[i].count('{')
        braces -= lines_list[i].count('}')
        if braces == 0:
            return idx, i
    return -1, -1

def blank_lines(start, end):
    for i in range(start, end+1):
        lines[i] = "\n"

# Remove first `updateBubble`
s, e = get_block("private fun updateBubble()", lines)
if s != -1: blank_lines(s, e)

# Remove first `showBubble`
s, e = get_block("private fun showBubble()", lines)
if s != -1: blank_lines(s, e)

# Remove first `removeBubble`
s, e = get_block("private fun removeBubble()", lines)
if s != -1: blank_lines(s, e)

# Remove first `onCreate`
s, e = get_block("override fun onCreate()", lines)
if s != -1: blank_lines(s, e)

# Remove first `onDestroy`
s, e = get_block("override fun onDestroy()", lines)
if s != -1: blank_lines(s, e)

# Remove first `isAppInForeground`
s, e = get_block("var isAppInForeground", lines)
if s != -1: blank_lines(s, e)

# Remove first `instance` inside companion
# It's `private var instance: DialerInCallService? = null`
s = -1
for i, l in enumerate(lines):
    if "private var instance: DialerInCallService? = null" in l:
        s = i
        break
if s != -1: lines[s] = "\n"

# Remove `updateBubbleVisibility`
s, e = get_block("private fun updateBubbleVisibility()", lines)
if s != -1: blank_lines(s, e)

# Also there's `private var windowManager` and `floatingView` which I injected twice maybe?
# I'll just leave them, they are top-level class members, duplicate properties might exist?
s1 = -1
s2 = -1
for i, l in enumerate(lines):
    if "private var windowManager" in l:
        if s1 == -1: s1 = i
        else: s2 = i
if s1 != -1 and s2 != -1:
    lines[s1] = "\n"

s1 = -1
s2 = -1
for i, l in enumerate(lines):
    if "private var floatingView" in l:
        if s1 == -1: s1 = i
        else: s2 = i
if s1 != -1 and s2 != -1:
    lines[s1] = "\n"

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.writelines(lines)
