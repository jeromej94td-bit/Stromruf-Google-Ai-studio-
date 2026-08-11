import sys

# DesignSystem.kt
with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'r') as f:
    ds = f.read()

ds = ds.replace('import androidx.compose.ui.graphics.graphicsLayer', 'import androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.animation.core.animateFloat\nimport androidx.compose.animation.core.tween\nimport androidx.compose.animation.core.FastOutSlowInEasing\nimport androidx.compose.animation.core.RepeatMode')
# the issue was `infiniteTransition.animateFloat` doesn't work if `animateFloat` isn't imported from `androidx.compose.animation.core`.
# But wait, it's `androidx.compose.animation.core.animateFloat(infiniteTransition, ...)`
# I used: `val alpha by infiniteTransition.animateFloat(...)` 
# I will rewrite `pulsatingAura` fully.
new_pulsating = '''@Composable
fun Modifier.pulsatingAura(color: androidx.compose.ui.graphics.Color): Modifier {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label="aura")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scale"
    )
    return this.androidx.compose.ui.graphics.graphicsLayer(
        scaleX = scale,
        scaleY = scale
    ).drawBehind {
        drawRoundRect(
            color = color.copy(alpha = alpha),
            size = androidx.compose.ui.geometry.Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
    }
}'''

# Replace old pulsatingAura
import re
ds = re.sub(r'@Composable\nfun Modifier\.pulsatingAura.*?\}\n\}', new_pulsating, ds, flags=re.DOTALL)

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'w') as f:
    f.write(ds)

# MainActivity.kt missing variables
with open('./app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    ma = f.read()
# Replace `activeCall` with `com.example.service.DialerInCallService.activeCall` in MainActivity
ma = ma.replace('activeCall.value', 'com.example.service.DialerInCallService.activeCall.value')
ma = ma.replace('activeCallNumber.value', 'com.example.service.DialerInCallService.activeCallNumber.value')
ma = ma.replace('activeCallName.value', 'com.example.service.DialerInCallService.activeCallName.value')
ma = ma.replace('activeCallCompany.value', 'com.example.service.DialerInCallService.activeCallCompany.value')
ma = ma.replace('activeCallReason.value', 'com.example.service.DialerInCallService.activeCallReason.value')
ma = ma.replace('activeCallNotes.value', 'com.example.service.DialerInCallService.activeCallNotes.value')
ma = ma.replace('callDurationSeconds.value', 'com.example.service.DialerInCallService.callDurationSeconds.value')
ma = ma.replace('activeCallTranscript.value', 'com.example.service.DialerInCallService.activeCallTranscript.value')
ma = ma.replace('currentAudioState.value', 'com.example.service.DialerInCallService.currentAudioState.value')
ma = ma.replace('isRecognizerListening.value', 'com.example.service.DialerInCallService.isRecognizerListening.value')

# answerCall, declineCall, hangUp
ma = ma.replace('answerCall()', 'com.example.service.DialerInCallService.instance?.answerCall()')
ma = ma.replace('declineCall()', 'com.example.service.DialerInCallService.instance?.declineCall()')
ma = ma.replace('hangUp()', 'com.example.service.DialerInCallService.instance?.hangUp()')
ma = ma.replace('playDtmf(', 'com.example.service.DialerInCallService.instance?.playDtmf(')
ma = ma.replace('startSpeechToText()', 'com.example.service.DialerInCallService.instance?.startSpeechToText()')
ma = ma.replace('stopSpeechToText()', 'com.example.service.DialerInCallService.instance?.stopSpeechToText()')

with open('./app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(ma)
