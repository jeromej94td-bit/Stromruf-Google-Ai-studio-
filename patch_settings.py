import sys

with open("app/src/main/java/com/example/ui/screens/KiVersandSettings.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val activity = context as? Activity",
    "var activity: Activity? = null\n            var currentContext = context\n            while (currentContext is android.content.ContextWrapper) {\n                if (currentContext is Activity) {\n                    activity = currentContext\n                    break\n                }\n                currentContext = currentContext.baseContext\n            }"
)

with open("app/src/main/java/com/example/ui/screens/KiVersandSettings.kt", "w") as f:
    f.write(content)
