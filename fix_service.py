import sys

with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'r') as f:
    content = f.read()

# Remove the first companion object block and onCreate / onDestroy I added.
# It starts at `    private var windowManager: android.view.WindowManager? = null`
# and ends at `    private fun removeBubble() { ... }` (line 416 to 499)

bad_code_start = '''    private var windowManager: android.view.WindowManager? = null
    private var floatingView: android.view.View? = null'''

# We will remove from `bad_code_start` up to the second `companion object {`
start_idx = content.find(bad_code_start)
end_idx = content.find('    companion object {', start_idx + 100)

if start_idx != -1 and end_idx != -1:
    content = content[:start_idx] + content[end_idx:]
else:
    print("Could not find blocks to remove")

# Now inject the bubble logic properly into the class (not duplicate onCreate etc)
# We will inject the fields and bubble methods at the top of the class.

class_start = 'class DialerInCallService : InCallService() {'
bubble_fields_and_methods = '''
    private var windowManager: android.view.WindowManager? = null
    private var floatingView: android.view.View? = null

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

content = content.replace(class_start, class_start + bubble_fields_and_methods)

# Now inject into existing onCreate and onDestroy
on_create_target = '''    override fun onCreate() {
        super.onCreate()
        instance = this'''
on_create_replacement = '''    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager'''
content = content.replace(on_create_target, on_create_replacement)

on_destroy_target = '''    override fun onDestroy() {
        super.onDestroy()
        instance = null'''
on_destroy_replacement = '''    override fun onDestroy() {
        super.onDestroy()
        instance = null
        removeBubble()'''
content = content.replace(on_destroy_target, on_destroy_replacement)

# Finally add isAppInForeground and updateBubbleVisibility to the existing companion object
comp_target = '''    companion object {
        const val ONGOING_CALL_NOTIFICATION_ID = 123456
        var instance: DialerInCallService? = null'''
comp_replacement = '''    companion object {
        var isAppInForeground = true
            set(value) {
                field = value
                instance?.updateBubble()
            }
            
        const val ONGOING_CALL_NOTIFICATION_ID = 123456
        var instance: DialerInCallService? = null'''
content = content.replace(comp_target, comp_replacement)


with open('./app/src/main/java/com/example/service/DialerInCallService.kt', 'w') as f:
    f.write(content)
