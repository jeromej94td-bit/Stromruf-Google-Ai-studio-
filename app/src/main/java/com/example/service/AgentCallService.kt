package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AgentCallService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "agentcall_live"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(channelId, "KI-Gespräche", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(4711, NotificationCompat.Builder(this, channelId)
            .setContentTitle("KI-Agent aktiv")
            .setContentText("Ein Agent führt gerade ein Gespräch")
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setOngoing(true)
            .build())
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
