import re

# 1. Fix SmartCallsTab.kt
with open("app/src/main/java/com/example/ui/screens/SmartCallsTab.kt", "r") as f:
    sct = f.read()

sct = sct.replace("currentProfile!!.build(targetNumber, sipRegistrar)", "SipProfile.Builder(targetNumber, sipRegistrar).build()")
with open("app/src/main/java/com/example/ui/screens/SmartCallsTab.kt", "w") as f:
    f.write(sct)


# 2. Fix MainActivity.kt Stubs
# I'll just write a new set of stubs and replace the old ones.
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    ma = f.read()

# Remove the old stubs
ma = ma.split("@Composable\nfun AddNeukundeDialog")[0]

new_stubs = """
@Composable
fun AddNeukundeDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String, String, String, String, String, String, Long?) -> Unit) { }

@Composable
fun IncomingCallBottomOverlay(contactName: String, contactPhone: String, contactCompany: String, contactReason: String, onAnswer: () -> Unit, onDecline: () -> Unit, onClick: () -> Unit) { }

@Composable
fun IncomingCallScreen(contactName: String, contactPhone: String, contactCompany: String, contactReason: String, contactNotes: String, onAnswer: () -> Unit, onDecline: () -> Unit, onMinimize: () -> Unit) { }

@Composable
fun OngoingCallDialog(contactName: String, contactPhone: String, onHangUp: (Long) -> Unit, isAutoCallActive: Boolean, onHangUpAndPause: (Long) -> Unit, wrapUpData: com.example.viewmodel.WrapUpData, onNoteChange: (String) -> Unit, onCallReasonChange: (String) -> Unit, onToggleOffset: (String) -> Unit, onOutcomeChange: (String) -> Unit, contact: com.example.database.Contact?, recentCallLogs: List<com.example.database.CallLogEntity>, onForceClose: () -> Unit, onMinimize: () -> Unit, onAddToHotbox: (String, String) -> Unit) { }

@Composable
fun AddFollowUpDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, Long, String) -> Unit, contacts: List<com.example.database.Contact>, initialDueAt: Long? = null, initialPhone: String = "") { }

@Composable
fun RingtonePickerDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) { }

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    appTheme: String = "",
    onThemeChange: (String) -> Unit = {},
    bgStyle: String = "",
    onBgStyleChange: (String) -> Unit = {},
    screenBrightness: Float = 1f,
    onBrightnessChange: (Float) -> Unit = {},
    alarmEnabled: Boolean = false,
    onAlarmToggle: (Boolean) -> Unit = {},
    currentRingDuration: Int = 0,
    onRingDurationChange: (Int) -> Unit = {},
    currentRingtone: String = "",
    selectedRingtoneTitle: String = "",
    onSelectRingtoneClick: () -> Unit = {},
    autoCallDelaySeconds: Int = 0,
    onAutoCallDelaySecondsChange: (Int) -> Unit = {},
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
    onRequestCallPermission: () -> Unit = {},
    onNotificationToggle: (Boolean) -> Unit = {}
) { }

@Composable
fun ProximityScreenShield(isCallActive: Boolean) { }

fun sendAnnahmeNotification(context: android.content.Context, type: String, customerType: String, consumption: String, customerNumber: String) { }
"""

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(ma + new_stubs)

print("Fixed!")
