import sys
import re

with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    ma = f.read()

# I will find the OutlinedTextField for quickPhone
target = '''                            OutlinedTextField(
                                value = quickPhone,
                                onValueChange = onQuickPhoneChange,
                                label = { Text("Telefonnummer *") },
                                modifier = Modifier.weight(1f).androidx.compose.ui.focus.focusRequester(focusRequester),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),'''

replacement = '''                            val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                            OutlinedTextField(
                                value = quickPhone,
                                onValueChange = onQuickPhoneChange,
                                label = { Text("Telefonnummer *") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),'''

ma = ma.replace(target, replacement)

# Add import for focusRequester
imports = '''import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
'''
ma = ma.replace('import androidx.compose.ui.Modifier', imports + 'import androidx.compose.ui.Modifier')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(ma)
