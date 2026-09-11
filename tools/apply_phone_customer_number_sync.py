from pathlib import Path

path = Path("app/src/main/java/com/example/MainActivity.kt")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 occurrence, found {count}")
    text = text.replace(old, new, 1)


replace_once(
    """            AddNeukundeDialog(
                onDismiss = { showAddNeukundeDialog = false },""",
    """            AddNeukundeDialog(
                contacts = contacts,
                onDismiss = { showAddNeukundeDialog = false },""",
    "pass contacts into AddNeukundeDialog",
)

replace_once(
    """fun AddNeukundeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?, String?, String?, String?, Long?, String?, String) -> Unit
) {""",
    """fun AddNeukundeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?, String?, String?, String?, Long?, String?, String) -> Unit,
    contacts: List<ContactEntity> = emptyList()
) {""",
    "AddNeukundeDialog contacts parameter",
)

replace_once(
    """            phone.isBlank() && parsed.phone != null -> {
                phone = parsed.phone; autoFilled = \"Telefonnummer\"
            }""",
    """            phone.isBlank() && parsed.phone != null -> {
                val newPhone = parsed.phone
                phone = newPhone
                customerNumber = contacts
                    .firstOrNull { arePhoneNumbersMatching(it.phone, newPhone) }
                    ?.customerNumber
                    .orEmpty()
                autoFilled = \"Telefonnummer\"
            }""",
    "clipboard phone/customer-number sync",
)

replace_once(
    """                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(\"Telefonnummer\") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = { if (autoFilled == \"Telefonnummer\") Text(\"Aus Zwischenablage übernommen\") },""",
    """                OutlinedTextField(
                    value = phone,
                    onValueChange = { newPhone ->
                        phone = newPhone
                        customerNumber = contacts
                            .firstOrNull { arePhoneNumbersMatching(it.phone, newPhone) }
                            ?.customerNumber
                            .orEmpty()
                        validation = null
                    },
                    label = { Text(\"Telefonnummer\") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    supportingText = { if (autoFilled == \"Telefonnummer\") Text(\"Aus Zwischenablage übernommen\") },""",
    "full Neukunde phone/customer-number sync",
)

replace_once(
    """                                onClick = {
                                    dialogPhone = quickPhone
                                    dialogCustomerNumber = \"KD-${(10000..99999).random()}\"
                                    showAddNeukundeDialog = true
                                },""",
    """                                onClick = {
                                    dialogPhone = quickPhone
                                    dialogCustomerNumber = contacts
                                        .firstOrNull { arePhoneNumbersMatching(it.phone, quickPhone) }
                                        ?.customerNumber
                                        .orEmpty()
                                    showAddNeukundeDialog = true
                                },""",
    "remove random customer number prefill",
)

replace_once(
    """                                        OutlinedTextField(
                                            value = dialogPhone,
                                            onValueChange = { dialogPhone = it },
                                            label = { Text(\"Telefonnummer\") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),""",
    """                                        OutlinedTextField(
                                            value = dialogPhone,
                                            onValueChange = { newPhone ->
                                                dialogPhone = newPhone
                                                dialogCustomerNumber = contacts
                                                    .firstOrNull { arePhoneNumbersMatching(it.phone, newPhone) }
                                                    ?.customerNumber
                                                    .orEmpty()
                                            },
                                            label = { Text(\"Telefonnummer\") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),""",
    "quick Neukunde phone/customer-number sync",
)

path.write_text(text, encoding="utf-8")
print("Phone/customer-number sync patch applied")
