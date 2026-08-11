package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SupabaseAuthClient {
    private const val SUPABASE_URL = "https://yepluyipizbbrgoffqdq.supabase.co"
    private const val SUPABASE_PUBLIC_KEY = "sb_publishable_lat183ycL-tC_3NDwzCHOw_GKmcNWqM"
    
    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun sessionPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encrypted = EncryptedSharedPreferences.create(
            appContext,
            "supabase_session_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // One-time migration from older app versions that used plain preferences.
        val legacy = appContext.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
        if (legacy.contains("access_token") || legacy.contains("refresh_token") || legacy.contains("user_email")) {
            encrypted.edit()
                .putString("access_token", legacy.getString("access_token", null))
                .putString("refresh_token", legacy.getString("refresh_token", null))
                .putString("user_email", legacy.getString("user_email", null))
                .commit()
            legacy.edit().clear().commit()
        }
        return encrypted
    }

    sealed class AuthResult {
        data class Success(val email: String, val token: String, val refreshToken: String = "") : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    suspend fun signUp(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/auth/v1/signup"
            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_PUBLIC_KEY)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonObj = JSONObject(bodyStr)
                    val user = jsonObj.optJSONObject("user")
                    val session = jsonObj.optJSONObject("session")
                    val token = session?.optString("access_token") ?: ""
                    val refreshToken = session?.optString("refresh_token") ?: ""
                    val returnedEmail = user?.optString("email") ?: email
                    AuthResult.Success(returnedEmail, token, refreshToken)
                } else {
                    val jsonObj = JSONObject(bodyStr)
                    val errorMsg = jsonObj.optString("msg", jsonObj.optString("error_description", "Registrierung fehlgeschlagen"))
                    AuthResult.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Verbindungsfehler")
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/auth/v1/token?grant_type=password"
            val jsonBody = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_PUBLIC_KEY)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonObj = JSONObject(bodyStr)
                    val user = jsonObj.optJSONObject("user")
                    val token = jsonObj.optString("access_token", "")
                    val refreshToken = jsonObj.optString("refresh_token", "")
                    val returnedEmail = user?.optString("email") ?: email
                    AuthResult.Success(returnedEmail, token, refreshToken)
                } else {
                    val jsonObj = JSONObject(bodyStr)
                    val errorMsg = jsonObj.optString("error_description", jsonObj.optString("msg", "Anmeldung fehlgeschlagen"))
                    AuthResult.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Verbindungsfehler")
        }
    }

    fun getSessionToken(context: Context): String? {
        return sessionPrefs(context).getString("access_token", null)
    }

    fun getRefreshToken(context: Context): String? {
        return sessionPrefs(context).getString("refresh_token", null)
    }

    fun getSessionEmail(context: Context): String? {
        return sessionPrefs(context).getString("user_email", null)
    }

    fun saveSession(context: Context, token: String, email: String) {
        sessionPrefs(context).edit()
            .putString("access_token", token)
            .putString("user_email", email)
            .apply()
    }

    fun saveSession(context: Context, token: String, refreshToken: String, email: String) {
        sessionPrefs(context).edit()
            .putString("access_token", token)
            .putString("refresh_token", refreshToken)
            .putString("user_email", email)
            .apply()
    }

    fun clearSession(context: Context) {
        sessionPrefs(context).edit().clear().apply()
    }

    fun getTokenClaim(token: String, claim: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null

            val decodedBytes = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or
                    android.util.Base64.NO_PADDING or
                    android.util.Base64.NO_WRAP
            )

            org.json.JSONObject(
                String(decodedBytes, Charsets.UTF_8)
            ).optString(claim, null)
        } catch (e: Exception) {
            android.util.Log.e(
                "SupabaseAuthClient",
                "Could not decode JWT claim $claim",
                e
            )
            null
        }
    }

    fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadB64 = parts[1]
                val decodedBytes = android.util.Base64.decode(
                    payloadB64,
                    android.util.Base64.URL_SAFE or
                        android.util.Base64.NO_PADDING or
                        android.util.Base64.NO_WRAP
                )
                val payloadStr = String(decodedBytes, Charsets.UTF_8)
                val jsonObj = JSONObject(payloadStr)
                val exp = jsonObj.optLong("exp", 0)
                val currentTimeSeconds = System.currentTimeMillis() / 1000
                currentTimeSeconds >= (exp - 60)
            } else true
        } catch (e: Exception) {
            true
        }
    }

    suspend fun refreshSession(context: Context): String? = withContext(Dispatchers.IO) {
        val refreshToken = getRefreshToken(context) ?: return@withContext null
        try {
            val url = "$SUPABASE_URL/auth/v1/token?grant_type=refresh_token"
            val jsonBody = JSONObject().apply {
                put("refresh_token", refreshToken)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_PUBLIC_KEY)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonObj = JSONObject(bodyStr)
                    val user = jsonObj.optJSONObject("user")
                    val newToken = jsonObj.optString("access_token", "")
                    val newRefreshToken = jsonObj.optString("refresh_token", "")
                    val email = user?.optString("email") ?: getSessionEmail(context) ?: ""
                    saveSession(context, newToken, newRefreshToken, email)
                    Log.d("SupabaseAuthClient", "Session successfully refreshed!")
                    newToken
                } else {
                    Log.e("SupabaseAuthClient", "Failed to refresh token: $bodyStr")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuthClient", "Error refreshing session", e)
            null
        }
    }
}
