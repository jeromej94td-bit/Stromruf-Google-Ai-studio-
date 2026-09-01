import sys

file_path = "app/src/main/java/com/example/MainActivity.kt"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

split_str = "// ------------------------------------------------------------------\n// RECONSTRUCTED MISSING COMPONENTS"
if split_str not in content:
    print("Could not find split string")
    sys.exit(1)

parts = content.split(split_str)
base_content = parts[0]

reconstructed = """// ------------------------------------------------------------------
// RECONSTRUCTED MISSING COMPONENTS
// ------------------------------------------------------------------

@Composable
fun AddNeukundeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String, String, String, String, String, Boolean) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Neukunde") },
        confirmButton = {
            androidx.compose.material3.Button(onClick = {
                onConfirm("KD-123", "0123", "Name", "Firma", "email", "Address", "Meter", "2000", "Strom", false)
            }) { androidx.compose.material3.Text("OK") }
        }
    )
}

@Composable
fun IncomingCallBottomOverlay(
    contactName: String,
    contactPhone: String,
    contactCompany: String,
    contactReason: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onClick: () -> Unit = {},
    onExpand: () -> Unit = {}
) {
    androidx.compose.material3.Card {
        androidx.compose.material3.Text("Anruf: $contactName")
    }
}

@Composable
fun IncomingCallScreen(
    contactName: String,
    contactPhone: String,
    contactCompany: String,
    contactReason: String,
    contactNotes: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMinimize: () -> Unit = {}
) {
    androidx.compose.material3.Card {
        androidx.compose.material3.Text("Anruf Screen: $contactName")
    }
}

@Composable
fun OngoingCallDialog(
    contactName: String,
    contactPhone: String,
    isAutoCallActive: Boolean = false,
    onHangUp: (Int) -> Unit = {},
    onHangUpAndPause: (Int) -> Unit = {},
    wrapUpData: Any? = null,
    onNoteChange: (String) -> Unit = {},
    onCallReasonChange: (String) -> Unit = {},
    onToggleOffset: (Any) -> Unit = {},
    onOutcomeChange: (String) -> Unit = {},
    contact: Any? = null,
    recentCallLogs: Any? = null,
    onForceClose: () -> Unit = {},
    onMinimize: () -> Unit = {},
    onAddToHotbox: (Boolean, Any?) -> Unit = {_,_->}
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        confirmButton = { androidx.compose.material3.Button(onClick = { onHangUp(0) }) { androidx.compose.material3.Text("Auflegen") } },
        text = { androidx.compose.material3.Text("Aktiver Anruf: $contactName") }
    )
}

@Composable
fun AddFollowUpDialog(
    contacts: Any? = null,
    initialName: String = "",
    initialPhone: String = "",
    initialDueAt: Long? = null,
    onDismiss: () -> Unit = {},
    onConfirm: (String, String, String, Long, String) -> Unit = {_,_,_,_,_->}
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.Button(onClick = { onConfirm("","","",0L,"") }) { androidx.compose.material3.Text("OK") } }
    )
}

@Composable
fun RingtonePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (android.net.Uri?, String) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.Button(onClick = { onConfirm(null, "") }) { androidx.compose.material3.Text("OK") } }
    )
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    appTheme: String,
    onThemeChange: (String) -> Unit,
    bgStyle: String,
    onBgStyleChange: (String) -> Unit,
    screenBrightness: Float,
    onScreenBrightnessChange: (Float) -> Unit = {},
    onBrightnessChange: (Float) -> Unit = {},
    keepScreenOn: Boolean = false,
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    selectedRingtoneTitle: String,
    onSelectRingtoneClick: () -> Unit,
    autoCallDelaySeconds: Int,
    onAutoCallDelaySecondsChange: (Int) -> Unit,
    isSipActive: Boolean = false,
    onSipToggle: (Boolean) -> Unit = {},
    isDndActive: Boolean = false,
    onDndToggle: (Boolean) -> Unit = {},
    alarmEnabled: Boolean = false,
    onAlarmToggle: (Boolean) -> Unit = {},
    preferredAudioDevice: String = "",
    onPreferredAudioDeviceChange: (String) -> Unit = {},
    clipboardBubblePosition: String = "",
    onClipboardBubblePositionChange: (String) -> Unit = {},
    clipboardBubbleOnLocalCopy: Boolean = false,
    onClipboardBubbleOnLocalCopyChange: (Boolean) -> Unit = {},
    onSignOut: () -> Unit = {},
    isSimulationModeEnabled: Boolean = false,
    onSimulationModeToggle: (Boolean) -> Unit = {},
    isDefaultDialer: Boolean = false,
    isCallPermissionGranted: Boolean = false,
    onRequestDefaultDialer: () -> Unit = {},
    onRequestCallPermission: () -> Unit = {}
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { androidx.compose.material3.Button(onClick = onDismiss) { androidx.compose.material3.Text("OK") } }
    )
}

@Composable
fun ProximityScreenShield(isCallActive: Boolean) {}

fun sendAnnahmeNotification(
    context: android.content.Context,
    type: String,
    customerType: String,
    consumption: String,
    customerNumber: Long
) {}
fun sendAnnahmeNotification(
    context: android.content.Context,
    type: String,
    customerType: String,
    consumption: String,
    customerNumber: String
) {}
"""

with open(file_path, "w", encoding="utf-8") as f:
    f.write(base_content)
    f.write(reconstructed)

print("Updated reconstructed components.")
