with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    ma = f.read()

ma = ma.replace(
    "fun sendAnnahmeNotification(context: android.content.Context, type: String, customerType: String, consumption: String, customerNumber: String) { }",
    "fun sendAnnahmeNotification(context: android.content.Context, type: String = \"\", customerType: String = \"\", consumption: String = \"\", customerNumber: String = \"\", name: String = \"\", phone: String = \"\", reason: String = \"\") { }"
)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(ma)
print("done")
