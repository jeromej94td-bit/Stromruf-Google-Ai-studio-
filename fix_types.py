import sys, re

file_path = "app/src/main/java/com/example/MainActivity.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Replace AddNeukundeDialog signature
content = content.replace(
    "onConfirm: (String, String, String, String, String, String, String, String, String, Boolean) -> Unit",
    "onConfirm: (String, String, String, String, String, String, String, Long?, String, String) -> Unit"
)

# Replace OngoingCallDialog signature
content = content.replace("onHangUp: (Int) -> Unit = {},", "onHangUp: (Long) -> Unit = {},")
content = content.replace("onHangUpAndPause: (Int) -> Unit = {},", "onHangUpAndPause: (Long) -> Unit = {},")
content = content.replace("onToggleOffset: (Any?) -> Unit = {},", "onToggleOffset: (String) -> Unit = {},")
content = content.replace("onAddToHotbox: (Boolean, Any?) -> Unit", "onAddToHotbox: (String, String) -> Unit")
content = content.replace("onHangUp(0)", "onHangUp(0L)")

# Replace RingtonePickerDialog signature
content = content.replace(
    "onConfirm: (android.net.Uri?, String) -> Unit",
    "onConfirm: (android.net.Uri, String) -> Unit"
)

# Replace SettingsDialog onSignOut (if it is nullable, let's just make it nullable?)
# The error was "Argument type mismatch: actual type is 'Function0<Unit>?', but 'Function0<Unit>' was expected."
# This means the caller passed `onSignOut = onSignOut` where `onSignOut` in the caller is `() -> Unit ?`
content = content.replace(
    "onSignOut: () -> Unit = {}",
    "onSignOut: (() -> Unit)? = null"
)

# Replace sendAnnahmeNotification
content = content.replace(
    "consumption: String,\n    customerNumber: Long",
    "consumption: Long,\n    customerNumber: String"
)
content = content.replace(
    "consumption: String,\n    customerNumber: String",
    "consumption: Long,\n    customerNumber: String"
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Fixed types")
