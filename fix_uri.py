with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    ma = f.read()

ma = ma.replace(
    "fun RingtonePickerDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) { }",
    "fun RingtonePickerDialog(onDismiss: () -> Unit, onConfirm: (android.net.Uri, String) -> Unit) { }"
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(ma)
print("done")
