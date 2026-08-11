package com.example.util

object MailOAuthConfig {
    const val GOOGLE_CLIENT_ID = "HIER_GOOGLE_ANDROID_CLIENT_ID_EINTRAGEN"
    const val MICROSOFT_CLIENT_ID = "HIER_MICROSOFT_CLIENT_ID_EINTRAGEN"
    const val REDIRECT_URI = "com.aistudio.stromruf.gkrfws:/oauth2redirect"

    val GOOGLE_SCOPES = listOf(
        "openid",
        "email",
        "https://www.googleapis.com/auth/gmail.send"
    )

    val MICROSOFT_SCOPES = listOf(
        "openid",
        "offline_access",
        "User.Read",
        "Mail.Send"
    )
}
