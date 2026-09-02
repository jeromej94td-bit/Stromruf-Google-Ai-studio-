with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    ma = f.read()

ma = ma.replace(
    "fun AddFollowUpDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, Long, String) -> Unit, contacts: List<com.example.database.Contact>, initialDueAt: Long? = null, initialPhone: String = \"\") { }",
    "fun AddFollowUpDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, Long, String) -> Unit, contacts: List<com.example.database.Contact> = emptyList(), initialDueAt: Long? = null, initialPhone: String = \"\", initialName: String = \"\", contactPhone: String = \"\") { }"
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(ma)
print("done")
