package com.example.receiver

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object FollowUpAlarmScheduler {

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleAlarm(context: Context, id: String, contactName: String, contactPhone: String, dueAt: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, FollowUpAlarmReceiver::class.java).apply {
            putExtra("FOLLOWUP_ID", id)
            putExtra("CONTACT_NAME", contactName)
            putExtra("CONTACT_PHONE", contactPhone)
            putExtra("DUE_AT", dueAt)
        }

        // Use a unique request code by hashing the ID string
        val requestCode = id.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
            }
        } catch (e: Exception) {
            // Fallback if permission rules change unexpectedly
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, dueAt, pendingIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun cancelAlarm(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FollowUpAlarmReceiver::class.java)
        val requestCode = id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
