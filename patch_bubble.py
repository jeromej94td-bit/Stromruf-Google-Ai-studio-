import sys
import re

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'r') as f:
    content = f.read()

# Add Bubble Overlay logic
bubble_code = '''
    private var windowManager: android.view.WindowManager? = null
    private var floatingView: android.view.View? = null

    companion object {
        var isAppInForeground = true
            set(value) {
                field = value
                updateBubbleVisibility()
            }

        private var instance: DialerInCallService? = null

        private fun updateBubbleVisibility() {
            instance?.updateBubble()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeBubble()
    }

    private fun updateBubble() {
        if (!isAppInForeground && Companion.activeCall.value != null && android.provider.Settings.canDrawOverlays(this)) {
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
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                    // Open App
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

# Find a good place to insert the bubble code, inside DialerInCallService class before companion object if there is one.
# There is a companion object at the end.
content = content.replace('companion object {', bubble_code + '\n    companion object {', 1)

# Modify Call state updates to trigger updateBubble()
content = content.replace('activeCall.value = null', 'activeCall.value = null\n            updateBubble()')
content = content.replace('activeCall.value = call', 'activeCall.value = call\n        updateBubble()')

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.write(content)
