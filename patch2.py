import sys

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

new_methods = """
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
        // Also keep the existing onNewIntent logic if any, but we will just add this here.
        // Wait, did it already have onNewIntent?
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "com.aistudio.stromruf.gkrfws") return

        val provider = when {
            data.toString().contains("gmail", ignoreCase = true) -> "gmail"
            data.toString().contains("google", ignoreCase = true) -> "gmail"
            data.toString().contains("outlook", ignoreCase = true) -> "outlook"
            data.toString().contains("microsoft", ignoreCase = true) -> "outlook"
            else -> com.example.util.SecureIntegrationSettings(this).getDefaultMailProvider()
        }

        mailAccountManager.handleAuthorizationResponse(intent, provider) { success, error ->
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    if (success) "Mailkonto verbunden." else "Mailkonto konnte nicht verbunden werden: ${error ?: "Unbekannter Fehler"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
"""

# wait, there's already an onNewIntent!
if "override fun onNewIntent" in content:
    content = content.replace("override fun onNewIntent(intent: Intent) {", "override fun onNewIntent(intent: Intent) {\n        handleOAuthIntent(intent)")
    content = content.replace("private fun checkCallNotificationIntent(intent: Intent) {", new_methods.replace("override fun onNewIntent(intent: Intent) {\n        super.onNewIntent(intent)\n        setIntent(intent)\n        handleOAuthIntent(intent)\n        // Also keep the existing onNewIntent logic if any, but we will just add this here.\n        // Wait, did it already have onNewIntent?\n    }", "") + "\n    private fun checkCallNotificationIntent(intent: Intent) {")
else:
    content = content.replace("private fun checkCallNotificationIntent(intent: Intent) {", new_methods + "\n    private fun checkCallNotificationIntent(intent: Intent) {")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
