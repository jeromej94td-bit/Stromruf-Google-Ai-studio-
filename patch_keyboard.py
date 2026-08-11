import sys
import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Find the start of the "CallScreen" or "Direktwahl Card"
# We need to add FocusRequester in CallScreen signature or inside
target = '''fun CallScreen(
    quickName: String,
    onQuickNameChange: (String) -> Unit,
    quickPhone: String,
    onQuickPhoneChange: (String) -> Unit,
    onCall: () -> Unit,
    context: Context,
    viewModel: com.example.viewmodel.StromrufViewModel
) {
    val contacts by viewModel.contacts.collectAsState()'''

replacement = '''fun CallScreen(
    quickName: String,
    onQuickNameChange: (String) -> Unit,
    quickPhone: String,
    onQuickPhoneChange: (String) -> Unit,
    onCall: () -> Unit,
    context: Context,
    viewModel: com.example.viewmodel.StromrufViewModel
) {
    val contacts by viewModel.contacts.collectAsState()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // Automatically request focus when this screen appears
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }'''
content = content.replace(target, replacement)

# Now attach focusRequester to the OutlinedTextField for Telefonnummer *
text_field_target = '''                            OutlinedTextField(
                                value = quickPhone,
                                onValueChange = onQuickPhoneChange,
                                label = { Text("Telefonnummer *") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,'''
text_field_replacement = '''                            OutlinedTextField(
                                value = quickPhone,
                                onValueChange = onQuickPhoneChange,
                                label = { Text("Telefonnummer *") },
                                modifier = Modifier.weight(1f).androidx.compose.ui.focus.focusRequester(focusRequester),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),'''
content = content.replace(text_field_target, text_field_replacement)

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
