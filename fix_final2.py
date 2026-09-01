import sys, re

file_path = "app/src/main/java/com/example/MainActivity.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Fix AddNeukundeDialog dummy call
content = content.replace(
    'onConfirm("KD-123", "0123", "Name", "Firma", "email", "Address", "Meter", "2000", "Strom", false)',
    'onConfirm("KD-123", "0123", "Name", "Firma", "email", "Address", "Meter", 2000L, "Strom", "Nein")'
)

# Fix RingtonePickerDialog dummy call
content = content.replace(
    'onConfirm(null, "")',
    'onConfirm(android.net.Uri.EMPTY, "")'
)

# Remove duplicate sendAnnahmeNotification
duplicate_str = """fun sendAnnahmeNotification(
    context: android.content.Context,
    type: String,
    customerType: String,
    consumption: Long,
    customerNumber: String
) {}
"""
# If there are two identical ones next to each other, remove one.
content = content.replace(duplicate_str + duplicate_str, duplicate_str)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Fixed final issues.")
