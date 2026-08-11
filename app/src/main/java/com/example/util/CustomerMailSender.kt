package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64

class CustomerMailSender(private val context: Context) {
    private val settings = SecureIntegrationSettings(context)
    private val client = OkHttpClient()

    suspend fun sendViaGmail(to: String, subject: String, body: String): Boolean = withContext(Dispatchers.IO) {
        val token = settings.getGoogleAccessToken() ?: error("Gmail ist nicht verbunden.")
        val rawMail = """
            To: $to
            Subject: $subject
            Content-Type: text/plain; charset=utf-8

            $body
        """.trimIndent()

        val encoded = Base64.encodeToString(
            rawMail.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

        val json = JSONObject().put("raw", encoded)
        val request = Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(response.body?.string().orEmpty())
            true
        }
    }

    suspend fun sendViaOutlook(to: String, subject: String, body: String): Boolean = withContext(Dispatchers.IO) {
        val token = settings.getMicrosoftAccessToken() ?: error("Outlook ist nicht verbunden.")
        val json = JSONObject()
            .put(
                "message",
                JSONObject()
                    .put("subject", subject)
                    .put(
                        "body",
                        JSONObject()
                            .put("contentType", "Text")
                            .put("content", body)
                    )
                    .put(
                        "toRecipients",
                        org.json.JSONArray().put(
                            JSONObject().put(
                                "emailAddress",
                                JSONObject().put("address", to)
                            )
                        )
                    )
            )
            .put("saveToSentItems", true)

        val request = Request.Builder()
            .url("https://graph.microsoft.com/v1.0/me/sendMail")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(response.body?.string().orEmpty())
            true
        }
    }
}
