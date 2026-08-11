package com.example.receiver

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

object AlarmSoundPlayer {
    private var ringtone: Ringtone? = null
    private var testRingtone: Ringtone? = null

    private const val PREFS_NAME = "stromruf_prefs"
    private const val KEY_RINGTONE_URI = "selected_ringtone_uri"
    private const val KEY_RINGTONE_TITLE = "selected_ringtone_title"

    fun getSelectedRingtoneUri(context: Context): Uri {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUriString = prefs.getString(KEY_RINGTONE_URI, null)
        return if (!savedUriString.isNullOrBlank()) {
            Uri.parse(savedUriString)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    fun getSelectedRingtoneTitle(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_RINGTONE_TITLE, "Standard-Weckton") ?: "Standard-Weckton"
    }

    fun saveSelectedRingtone(context: Context, uri: Uri, title: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_RINGTONE_URI, uri.toString())
            .putString(KEY_RINGTONE_TITLE, title)
            .apply()
    }

    fun startRinging(context: Context) {
        try {
            stopRinging()
            val alarmUri = getSelectedRingtoneUri(context)

            ringtone = RingtoneManager.getRingtone(context.applicationContext, alarmUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRinging() {
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            ringtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopTestRingtone()
    }

    fun playTestSound(context: Context, uri: Uri) {
        try {
            stopTestRingtone()
            testRingtone = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTestRingtone() {
        try {
            testRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
            testRingtone = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
