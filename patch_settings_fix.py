import sys

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace('val context = androidx.compose.ui.platform.LocalContext.current as android.app.Activity', 'val context = androidx.compose.ui.platform.LocalContext.current')
content = content.replace('context.startActivityForResult(intent, 1)', 'context.startActivity(intent)')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
