import sys

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Remove the duplicate onResume and onPause block I added
bad_block = '''
    override fun onResume() {
        super.onResume()
        com.example.service.DialerInCallService.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        com.example.service.DialerInCallService.isAppInForeground = false
    }
'''
content = content.replace(bad_block, '')

# Now find the original onResume and add the line
target_resume = '''    override fun onResume() {
        super.onResume()'''
new_resume = '''    override fun onResume() {
        super.onResume()
        com.example.service.DialerInCallService.isAppInForeground = true'''
content = content.replace(target_resume, new_resume, 1)

target_pause = '''    override fun onPause() {
        super.onPause()'''
new_pause = '''    override fun onPause() {
        super.onPause()
        com.example.service.DialerInCallService.isAppInForeground = false'''
content = content.replace(target_pause, new_pause, 1)

# Fix Unresolved reference 'activeCall' and others in MainActivity because they are in DialerInCallService.Companion
# Or rather, let's make sure they are accessible.
# The errors were:
# Unresolved reference 'activeCall'
# This is because I added 'companion object' to DialerInCallService which shadowed the variables or made them unreachable, OR because I moved them inside the companion object?
# Let's check my patch_bubble.py. 
# It added `companion object { ... var isAppInForeground = true ... }` before the existing variables? NO, it replaced the FIRST `companion object {` which happened to be the one at the end of the file. Wait! The file already had a `companion object` at the very end which held all the global state: `val activeCall = ...`.
# By doing `.replace('companion object {', ...)` I broke the existing companion object and created duplicate companion objects or separated the variables from it!

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
