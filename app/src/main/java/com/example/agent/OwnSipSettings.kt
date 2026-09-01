package com.example.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class OwnSipSettings(context: Context) {
    private val appContext = context.applicationContext
    private val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        appContext, "stromruf_smart_calls", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load() = OwnSipConfig(
        displayName = prefs.getString("display_name", "Easybell") ?: "Easybell",
        username = prefs.getString("username", "") ?: "",
        password = prefs.getString("password", "") ?: "",
        registrar = prefs.getString("registrar", "voip.easybell.de") ?: "voip.easybell.de",
        proxy = prefs.getString("proxy", "voip.easybell.de") ?: "voip.easybell.de",
        port = prefs.getInt("port", 5060),
        transport = prefs.getString("transport", "UDP") ?: "UDP",
        callerId = prefs.getString("caller_id", "") ?: "",
        recordingEnabled = prefs.getBoolean("recording_enabled", true)
    )

    fun save(config: OwnSipConfig) {
        prefs.edit().putString("display_name", config.displayName.trim())
            .putString("username", config.username.trim()).putString("password", config.password)
            .putString("registrar", config.registrar.trim()).putString("proxy", config.proxy.trim())
            .putInt("port", config.port).putString("transport", config.transport)
            .putString("caller_id", config.callerId.trim()).putBoolean("recording_enabled", config.recordingEnabled)
            .apply()
    }
}

