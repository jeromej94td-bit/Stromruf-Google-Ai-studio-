package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureIntegrationSettings(context: Context) {
    private val prefs: SharedPreferences

    init {
        var p: SharedPreferences? = null
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            p = EncryptedSharedPreferences.create(
                context,
                "stromruf_secure_integrations",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SecureSettings", "Failed to create EncryptedSharedPreferences, attempting reset", e)
            try {
                // Delete old file and try to recreate
                context.deleteSharedPreferences("stromruf_secure_integrations")

                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                p = EncryptedSharedPreferences.create(
                    context,
                    "stromruf_secure_integrations",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                Log.e("SecureSettings", "Failed to recreate EncryptedSharedPreferences, falling back to standard SharedPreferences", e2)
                // Ultimate fallback to standard unencrypted shared preferences to prevent startup crash
                p = context.getSharedPreferences("stromruf_secure_integrations_fallback", Context.MODE_PRIVATE)
            }
        }
        prefs = p!!
    }

    fun saveGeminiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun getGeminiKey(): String? {
        val custom = prefs.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() }
        if (!custom.isNullOrBlank()) return custom
        // Fallback to BuildConfig injected from Secrets
        val buildKey = runCatching { com.example.BuildConfig.GEMINI_API_KEY }.getOrNull()
        return if (!buildKey.isNullOrBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else null
    }

    fun clearGeminiKey() {
        prefs.edit().remove("gemini_api_key").apply()
    }

    fun saveOpenAiKey(key: String) {
        prefs.edit().putString("openai_api_key", key.trim()).apply()
    }

    fun getOpenAiKey(): String? {
        return prefs.getString("openai_api_key", null)
            ?.takeIf { it.startsWith("sk-") || it.startsWith("sk-proj-") }
    }

    fun clearOpenAiKey() {
        prefs.edit().remove("openai_api_key").apply()
    }

    fun saveGroqKey(key: String) {
        prefs.edit().putString("groq_api_key", key.trim()).apply()
    }

    fun getGroqKey(): String? {
        val custom = prefs.getString("groq_api_key", null)?.takeIf { it.isNotBlank() }
        if (!custom.isNullOrBlank()) return custom
        val buildKey = runCatching { com.example.BuildConfig.GROQ_API_KEY }.getOrNull()
        return if (!buildKey.isNullOrBlank() && buildKey != "MY_GROQ_API_KEY") buildKey else null
    }

    fun clearGroqKey() {
        prefs.edit().remove("groq_api_key").apply()
    }

    fun saveDefaultMailProvider(provider: String) {
        prefs.edit().putString("default_mail_provider", provider).apply()
    }

    fun getDefaultMailProvider(): String {
        return prefs.getString("default_mail_provider", "ask") ?: "ask"
    }

    fun saveGoogleTokens(accessToken: String, refreshToken: String?) {
        prefs.edit()
            .putString("gmail_access_token", accessToken)
            .putString("gmail_refresh_token", refreshToken)
            .apply()
    }

    fun getGoogleAccessToken(): String? = prefs.getString("gmail_access_token", null)
    fun getGoogleRefreshToken(): String? = prefs.getString("gmail_refresh_token", null)
    fun clearGoogleTokens() {
        prefs.edit().remove("gmail_access_token").remove("gmail_refresh_token").apply()
    }

    fun saveMicrosoftTokens(accessToken: String, refreshToken: String?) {
        prefs.edit()
            .putString("outlook_access_token", accessToken)
            .putString("outlook_refresh_token", refreshToken)
            .apply()
    }

    fun getMicrosoftAccessToken(): String? = prefs.getString("outlook_access_token", null)
    fun getMicrosoftRefreshToken(): String? = prefs.getString("outlook_refresh_token", null)
    fun clearMicrosoftTokens() {
        prefs.edit().remove("outlook_access_token").remove("outlook_refresh_token").apply()
    }

    // ---------- Custom OAuth Client IDs ----------
    fun saveGoogleClientId(clientId: String) {
        prefs.edit().putString("gmail_client_id", clientId.trim()).apply()
    }

    fun getGoogleClientId(): String? {
        return prefs.getString("gmail_client_id", null)?.takeIf { it.isNotBlank() }
    }

    fun clearGoogleClientId() {
        prefs.edit().remove("gmail_client_id").apply()
    }

    fun saveMicrosoftClientId(clientId: String) {
        prefs.edit().putString("outlook_client_id", clientId.trim()).apply()
    }

    fun getMicrosoftClientId(): String? {
        return prefs.getString("outlook_client_id", null)?.takeIf { it.isNotBlank() }
    }

    fun clearMicrosoftClientId() {
        prefs.edit().remove("outlook_client_id").apply()
    }

    // ---------- Telegram ----------
    fun saveTelegramBotToken(token: String) {
        prefs.edit().putString("telegram_bot_token", token.trim()).apply()
    }

    fun getTelegramBotToken(): String? =
        prefs.getString("telegram_bot_token", null)?.takeIf { it.isNotBlank() }

    fun saveTelegramChatId(chatId: String) {
        prefs.edit().putString("telegram_chat_id", chatId.trim()).apply()
    }

    fun getTelegramChatId(): String? =
        prefs.getString("telegram_chat_id", null)?.takeIf { it.isNotBlank() }

    fun clearTelegram() {
        prefs.edit().remove("telegram_bot_token").remove("telegram_chat_id").apply()
    }

    /** Automatische Hintergrund-Weiterleitung von Notizen an Telegram (Standard: an). */
    fun setTelegramAutoForward(enabled: Boolean) {
        prefs.edit().putBoolean("telegram_auto_forward", enabled).apply()
    }

    fun isTelegramAutoForward(): Boolean =
        prefs.getBoolean("telegram_auto_forward", true)
}
