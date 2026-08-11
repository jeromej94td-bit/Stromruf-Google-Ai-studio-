package com.example.receiver

import android.app.NotificationChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class FollowUpAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.action
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // Handle Direct Stop Action from notification action buttons
        if (action == "ACTION_STOP_RINGING") {
            AlarmSoundPlayer.stopRinging()
            val notifId = intent.getIntExtra("NOTIFICATION_ID", 1001)
            notificationManager?.cancel(notifId)
            return
        }

        if (action == "ACTION_SNOOZE_10_MIN") {
            AlarmSoundPlayer.stopRinging()
            val notifId = intent.getIntExtra("NOTIFICATION_ID", 1001)
            notificationManager?.cancel(notifId)

            val id = intent.getStringExtra("FOLLOWUP_ID") ?: ""
            val name = intent.getStringExtra("CONTACT_NAME") ?: "Unbekannt"
            val phone = intent.getStringExtra("CONTACT_PHONE") ?: ""

            if (id.isNotEmpty()) {
                val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000 // 10 minutes from now

                // Update due time in database with clash resolution
                val db = com.example.database.AppDatabase.getDatabase(context)
                val dao = db.stromrufDao()
                val repository = com.example.repository.StromrufRepository(context, dao)
                val pendingResult = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val followup = repository.getFollowUpById(id)
                        if (followup != null) {
                            val updated = followup.copy(dueAt = snoozeTime)
                            val saved = repository.insertFollowUp(updated)
                            
                            // Reschedule alarm with final conflict-resolved time
                            FollowUpAlarmScheduler.scheduleAlarm(context, id, name, phone, saved.dueAt)
                        } else {
                            // Fallback if not found in db
                            FollowUpAlarmScheduler.scheduleAlarm(context, id, name, phone, snoozeTime)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Fallback on error
                        FollowUpAlarmScheduler.scheduleAlarm(context, id, name, phone, snoozeTime)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            return
        }

        val id = intent.getStringExtra("FOLLOWUP_ID") ?: ""
        val name = intent.getStringExtra("CONTACT_NAME") ?: "Unbekannt"
        val phone = intent.getStringExtra("CONTACT_PHONE") ?: ""
        val dueAt = intent.getLongExtra("DUE_AT", 0)

        val notificationId = id.hashCode()

        // 1. Trigger the loud sound if enabled in settings
        val prefs = context.getSharedPreferences("stromruf_prefs", Context.MODE_PRIVATE)
        val alarmEnabled = prefs.getBoolean("alarm_enabled", true)
        if (alarmEnabled) {
            AlarmSoundPlayer.startRinging(context)
        }

        // 2. Setup Notification Channel
        val channelId = "stromruf_alarms_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nameChannel = "Stromruf Alarme"
            val descChannel = "Klingelzeichen für wichtige Kunden-Rückrufe"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, nameChannel, importance).apply {
                description = descChannel
                enableVibration(true)
                setBypassDnd(true)
                val alarmSound = AlarmSoundPlayer.getSelectedRingtoneUri(context)
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager?.createNotificationChannel(channel)
        }

        // 3. Setup Intent for Main Dialog / Dialer redirect
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SHOW_INCOMING_ALERT", true)
            putExtra("FOLLOWUP_ID", id)
            putExtra("CONTACT_NAME", name)
            putExtra("CONTACT_PHONE", phone)
        }
        val pContentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3b. Send broadcast to active MainActivity if in foreground
        val dynamicIntent = Intent("com.example.ACTION_FOLLOW_UP_ALERT").apply {
            setPackage(context.packageName)
            putExtra("FOLLOWUP_ID", id)
            putExtra("CONTACT_NAME", name)
            putExtra("CONTACT_PHONE", phone)
        }
        context.sendBroadcast(dynamicIntent)

        // 4. Setup "Stopp" broadcast intent
        val stopIntent = Intent(context, FollowUpAlarmReceiver::class.java).apply {
            this.action = "ACTION_STOP_RINGING"
            putExtra("NOTIFICATION_ID", notificationId)
        }
        val pStopIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4b. Setup "Snooze 10 Min" broadcast intent
        val snoozeIntent = Intent(context, FollowUpAlarmReceiver::class.java).apply {
            this.action = "ACTION_SNOOZE_10_MIN"
            putExtra("NOTIFICATION_ID", notificationId)
            putExtra("FOLLOWUP_ID", id)
            putExtra("CONTACT_NAME", name)
            putExtra("CONTACT_PHONE", phone)
        }
        val pSnoozeIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Build high priority notification cards
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Wiedervorlage fällig!")
            .setContentText("Kunde anrufen: $name ($phone)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pContentIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_call, "Jetzt anrufen", pContentIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "In 10 Min nochmal", pSnoozeIntent)
            .addAction(android.R.drawable.ic_delete, "Stopp", pStopIntent)

        // 6. Notify System
        notificationManager?.notify(notificationId, builder.build())
    }
}
