import sys

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

lifecycle_code = '''
    override fun onResume() {
        super.onResume()
        com.example.service.DialerInCallService.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        com.example.service.DialerInCallService.isAppInForeground = false
    }
'''

content = content.replace('override fun onCreate(savedInstanceState: Bundle?) {', lifecycle_code + '\n    override fun onCreate(savedInstanceState: Bundle?) {')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
