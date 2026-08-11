import sys

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'r') as f:
    lines = f.readlines()

# We want to find ALL "companion object {" lines.
# Keep the LAST one. For all others, we will remove "companion object {" and its closing "}", effectively putting their contents into the class? NO! We want ALL variables in ONE companion object.
# Let's find where they are.
comp_indices = []
for i, line in enumerate(lines):
    if 'companion object {' in line:
        comp_indices.append(i)

if len(comp_indices) == 2:
    idx1 = comp_indices[0]
    idx2 = comp_indices[1]
    
    # What's in idx1?
    # Probably `var isAppInForeground = true` etc.
    # Let's extract the body of idx1
    end_idx1 = -1
    braces = 0
    for i in range(idx1, len(lines)):
        braces += lines[i].count('{')
        braces -= lines[i].count('}')
        if braces == 0:
            end_idx1 = i
            break
            
    body_1 = lines[idx1+1:end_idx1]
    
    # We remove idx1 to end_idx1
    lines = lines[:idx1] + lines[end_idx1+1:]
    
    # Now find the new index of the remaining companion object
    new_idx2 = -1
    for i, line in enumerate(lines):
        if 'companion object {' in line:
            new_idx2 = i
            break
            
    # Insert body_1 into new_idx2
    lines = lines[:new_idx2+1] + body_1 + lines[new_idx2+1:]

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.writelines(lines)

