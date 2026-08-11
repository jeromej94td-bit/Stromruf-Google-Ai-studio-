import sys
import re

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'r') as f:
    content = f.read()

# Let's wrap the variables at the bottom of DialerInCallService back in a companion object!
# The hanging variables start with `var isAppInForeground = true` maybe?
# Let's find: `var isAppInForeground = true`
# Wait, I removed the `companion object` declarations. 
# Let's just find `var isAppInForeground = true` and put `companion object {` before it, and `}` at the end of the file.
lines = content.split('\n')
for i, l in enumerate(lines):
    if 'var isAppInForeground' in l:
        lines.insert(i, '    companion object {')
        # if the last line is not `}`, we add it
        if lines[-1] != '}':
            # wait, the class itself needs a closing `}`!
            # so we insert `}` before the very last `}`
            lines.insert(len(lines)-1, '    }')
        break

content = '\n'.join(lines)
with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.write(content)

