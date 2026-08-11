import sys
with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# 1. Add mailAccountManager
if "private lateinit var mailAccountManager" not in content:
    content = content.replace(
        "private val viewModel: StromrufViewModel",
        "private lateinit var mailAccountManager: com.example.util.MailAccountManager\n    private val viewModel: StromrufViewModel"
    )

# 2. Add mailAccountManager = ... and handleOAuthIntent to onCreate
if "mailAccountManager = com.example.util.MailAccountManager(this)" not in content:
    content = content.replace(
        "super.onCreate(savedInstanceState)",
        "super.onCreate(savedInstanceState)\n        mailAccountManager = com.example.util.MailAccountManager(this)\n        handleOAuthIntent(intent)"
    )

# 3. Add handleOAuthIntent and onNewIntent
new_methods = """
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
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
                Toast.makeText(
                    this,
                    if (success) "Mailkonto verbunden." else "Mailkonto konnte nicht verbunden werden: ${error ?: "Unbekannter Fehler"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
"""
if "handleOAuthIntent" not in content.split("private fun handleOAuthIntent")[0]:
    content = content.replace(
        "private fun checkCallNotificationIntent",
        new_methods + "\n    private fun checkCallNotificationIntent"
    )

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
print("Added OAuth handling to MainActivity")
