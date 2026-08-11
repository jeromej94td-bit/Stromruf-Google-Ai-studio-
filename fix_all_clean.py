import sys
import re

# 1. DialerInCallService.kt
with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'r') as f:
    dialer = f.read()

# Add windowManager and floatingView back if they are missing
if 'private var windowManager: android.view.WindowManager? = null' not in dialer:
    dialer = dialer.replace('class DialerInCallService : InCallService() {', 'class DialerInCallService : InCallService() {\n    private var windowManager: android.view.WindowManager? = null\n    private var floatingView: android.view.View? = null\n')

# Add updateBubble inside the class
if 'private fun updateBubble()' not in dialer:
    bubble_logic = '''
    private fun updateBubble() {
        if (!isAppInForeground && activeCall.value != null && android.provider.Settings.canDrawOverlays(this)) {
            showBubble()
        } else {
            removeBubble()
        }
    }

    private fun showBubble() {
        if (floatingView != null) return

        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        params.x = 0
        params.y = 100

        val view = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val button = android.widget.Button(this@DialerInCallService).apply {
                text = "☎ Stromruf"
                setBackgroundColor(android.graphics.Color.parseColor("#00FF87"))
                setTextColor(android.graphics.Color.BLACK)
                setOnClickListener {
                    val intent = android.content.Intent(this@DialerInCallService, com.example.MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                }
            }
            addView(button)
        }

        floatingView = view
        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeBubble() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
            floatingView = null
        }
    }
'''
    dialer = dialer.replace('class DialerInCallService : InCallService() {\n    private var windowManager: android.view.WindowManager? = null\n    private var floatingView: android.view.View? = null\n', 'class DialerInCallService : InCallService() {\n    private var windowManager: android.view.WindowManager? = null\n    private var floatingView: android.view.View? = null\n' + bubble_logic)

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.write(dialer)


# 2. DesignSystem.kt
with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'r') as f:
    ds = f.read()

# Fix pulsatingAura
new_aura = '''@Composable
fun Modifier.pulsatingAura(color: androidx.compose.ui.graphics.Color): Modifier {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val scale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    return this.androidx.compose.ui.graphics.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.drawBehind {
        drawRoundRect(
            color = color.copy(alpha = alpha.value),
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
    }
}'''

ds = re.sub(r'@Composable\nfun Modifier\.pulsatingAura.*?\}\n\}', new_aura, ds, flags=re.DOTALL)

with open('./app/src/main/java/com/example/ui/design/DesignSystem.kt', 'w') as f:
    f.write(ds)


# 3. LeadsScreen.kt
with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'r') as f:
    ls = f.read()
ls_imports = '''import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
'''
ls = ls.replace('import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.unit.sp\nimport androidx.compose.foundation.background\nimport androidx.compose.foundation.shape.RoundedCornerShape', ls_imports)
if 'import androidx.compose.ui.graphics.Color' not in ls:
    ls = ls.replace('import androidx.compose.ui.Modifier', ls_imports + 'import androidx.compose.ui.Modifier')
else:
    # Just to be sure, find and replace the imports nicely
    pass

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'w') as f:
    f.write(ls)

