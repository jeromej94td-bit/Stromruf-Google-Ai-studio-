package com.example.sip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import org.linphone.core.tools.AndroidPlatformHelper
import org.linphone.core.tools.service.CoreService

/** SDK discovers this CoreService subclass from the manifest. */
class SmartCallService : CoreService() {
    companion object {
        const val START_CALL = "com.example.smartcalls.START"
        private const val HANG_UP = "com.example.smartcalls.HANG_UP"
        private const val CHANNEL = "smart_calls"
        private const val NOTIFICATION = 7302
    }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == HANG_UP) {
            LinphoneSipClient.getInstance(this).hangUp()
            return START_NOT_STICKY
        }
        // Before INVITE: Android 14+ needs microphone foreground access already active.
        showForegroundServiceNotification(false)
        if (intent?.action == START_CALL) LinphoneSipClient.getInstance(this).startPendingCall()
        return START_NOT_STICKY
    }

    override fun showForegroundServiceNotification(isVideo: Boolean) {
        val manager = requireNotNull(getSystemService(NotificationManager::class.java))
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Smart Calls", NotificationManager.IMPORTANCE_LOW))
        }
        val hangUp = PendingIntent.getService(this, 0,
            Intent(this, SmartCallService::class.java).setAction(HANG_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("Stromruf – Smart Calls")
            .setContentText("Telefonie aktiv")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Auflegen", hangUp).build())
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            builder.setContentIntent(PendingIntent.getActivity(this, 1, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION, builder.build())
        }
        mIsInForegroundMode = true
        if (AndroidPlatformHelper.isReady()) {
            AndroidPlatformHelper.instance().setServiceRunningAsForeground(true)
        }
        if (wakeLock == null) {
            wakeLock = requireNotNull(getSystemService(PowerManager::class.java))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Stromruf:SmartCall")
                .apply { setReferenceCounted(false); acquire() }
        }
    }

    override fun hideForegroundServiceNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        hideForegroundServiceNotification()
        super.onDestroy()
        LinphoneSipClient.getInstance(this).serviceStoppedUnexpectedly()
    }
}
