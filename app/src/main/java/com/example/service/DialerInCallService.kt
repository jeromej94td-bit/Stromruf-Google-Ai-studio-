package com.example.service

import android.telecom.Call
import android.telecom.InCallService
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.example.util.ContactsUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class DialerInCallService : InCallService() {
    private var windowManager: android.view.WindowManager? = null
    private var floatingView: android.view.View? = null
    private var proximityWakeLock: android.os.PowerManager.WakeLock? = null
    private var callWakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireProximityWakeLock() {
        try {
            if (proximityWakeLock == null) {
                val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                if (pm != null && pm.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    proximityWakeLock = pm.newWakeLock(
                        android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "Stromruf:InCallProximity"
                    )
                }
            }
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire()
                Log.d("DialerInCallService", "Proximity screen-off wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e("DialerInCallService", "Error acquiring proximity wake lock: ${e.localizedMessage}")
        }
    }

    private fun releaseProximityWakeLock() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
                Log.d("DialerInCallService", "Proximity screen-off wake lock released")
            }
        } catch (e: Exception) {
            Log.e("DialerInCallService", "Error releasing proximity wake lock: ${e.localizedMessage}")
        }
    }

    private fun updateProximitySensorState() {
        val state = activeCallState.value
        val isCallActive = (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_RINGING)
        
        val currentRoute = try {
            getCallAudioState()?.route
        } catch (e: Exception) {
            null
        } ?: currentAudioState.value?.route
        
        val isEarpiece = (currentRoute == null || currentRoute == android.telecom.CallAudioState.ROUTE_EARPIECE)
        
        if (isCallActive && isEarpiece) {
            acquireProximityWakeLock()
        } else {
            releaseProximityWakeLock()
        }
    }

    private fun acquireCallWakeLock() {
        try {
            if (callWakeLock == null) {
                val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                callWakeLock = pm?.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "Stromruf:InCallCpuLock"
                )
            }
            if (callWakeLock?.isHeld == false) {
                callWakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours max
                Log.d("DialerInCallService", "Call CPU wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e("DialerInCallService", "Error acquiring call wake lock: ${e.localizedMessage}")
        }
    }

    private fun releaseCallWakeLock() {
        try {
            if (callWakeLock?.isHeld == true) {
                callWakeLock?.release()
                Log.d("DialerInCallService", "Call CPU wake lock released")
            }
        } catch (e: Exception) {}
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        Log.d("DialerInCallService", "Audio state changed: $audioState")
        currentAudioState.value = audioState
        updateProximitySensorState()
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            Log.d("DialerInCallService", "Call state changed to: $state")
            activeCallState.value = state
            if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_RINGING || state == Call.STATE_ACTIVE) {
                applyPreferredAudioRoute()
            }
            if (state == Call.STATE_RINGING) {
                wasRinging = true
            }
            if (state == Call.STATE_ACTIVE) {
                wasRinging = false
                acquireCallWakeLock()
                optimizeAudioForCall(true)
                startSpeechToText()
                // Apply the preferred audio route once on transition to active state.
                // Avoid delayed looped overrides to prevent audio routing fight loops with PC Smartphone-Link or Bluetooth.
                applyPreferredAudioRoute()
            }
            updateProximitySensorState()
            if (state == Call.STATE_DISCONNECTED) {
                val cause = call?.details?.disconnectCause
                Log.d("DialerInCallService", "Call disconnected: reason=${cause?.reason}, description=${cause?.description}, code=${cause?.code}")
                releaseProximityWakeLock()
                releaseCallWakeLock()
                cancelOngoingCallNotification()
                if (wasRinging && !userDeclined) {
                    showMissedCallNotification(activeCallNumber.value, activeCallName.value)
                }
                resetCallState()
            } else {
                if (state != Call.STATE_RINGING) {
                    showOngoingCallNotification(activeCallNumber.value, activeCallName.value, state)
                } else {
                    cancelOngoingCallNotification()
                }
            }
        }
    }

    fun setAudioRouteCompat(route: Int) {
        try {
            Log.d("DialerInCallService", "setAudioRouteCompat called with route: $route")
            
            val currentRoute = try {
                getCallAudioState()?.route
            } catch (e: Exception) {
                null
            } ?: currentAudioState.value?.route
            
            if (currentRoute == route) {
                Log.d("DialerInCallService", "Route is already set to $route, skipping setAudioRoute to prevent Bluetooth/Windows Link loop.")
                return
            }
            
            setAudioRoute(route)
            
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                when (route) {
                    android.telecom.CallAudioState.ROUTE_SPEAKER -> {
                        audioManager.isSpeakerphoneOn = true
                        try {
                            if (audioManager.isBluetoothScoOn) {
                                audioManager.isBluetoothScoOn = false
                                audioManager.stopBluetoothSco()
                            }
                        } catch (e: Exception) {}
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            val speakerDevice = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                            if (speakerDevice != null) {
                                val result = audioManager.setCommunicationDevice(speakerDevice)
                                Log.d("DialerInCallService", "Set communication device to speaker result: $result")
                            }
                        }
                    }
                    android.telecom.CallAudioState.ROUTE_BLUETOOTH -> {
                        audioManager.isSpeakerphoneOn = false
                        try {
                            audioManager.startBluetoothSco()
                            audioManager.isBluetoothScoOn = true
                        } catch (e: Exception) {}
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            val bluetoothDevice = devices.firstOrNull { 
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            }
                            if (bluetoothDevice != null) {
                                val result = audioManager.setCommunicationDevice(bluetoothDevice)
                                Log.d("DialerInCallService", "Set communication device to bluetooth result: $result")
                            }
                        }
                    }
                    android.telecom.CallAudioState.ROUTE_EARPIECE -> {
                        audioManager.isSpeakerphoneOn = false
                        try {
                            if (audioManager.isBluetoothScoOn) {
                                audioManager.isBluetoothScoOn = false
                                audioManager.stopBluetoothSco()
                            }
                        } catch (e: Exception) {}
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            audioManager.clearCommunicationDevice()
                        }
                    }
                    android.telecom.CallAudioState.ROUTE_WIRED_HEADSET -> {
                        audioManager.isSpeakerphoneOn = false
                        try {
                            if (audioManager.isBluetoothScoOn) {
                                audioManager.isBluetoothScoOn = false
                                audioManager.stopBluetoothSco()
                            }
                        } catch (e: Exception) {}
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                            val headsetDevice = devices.firstOrNull { 
                                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                            }
                            if (headsetDevice != null) {
                                audioManager.setCommunicationDevice(headsetDevice)
                            } else {
                                audioManager.clearCommunicationDevice()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DialerInCallService", "Error in setAudioRouteCompat: ${e.localizedMessage}")
        }
    }

    fun applyPreferredAudioRoute() {
        try {
            val prefs = getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE)
            val preferred = prefs.getString("preferred_audio_device", "earpiece") ?: "earpiece"
            Log.d("DialerInCallService", "Applying preferred audio route: $preferred")
            val route = when (preferred) {
                "earpiece" -> android.telecom.CallAudioState.ROUTE_EARPIECE
                "speaker" -> android.telecom.CallAudioState.ROUTE_SPEAKER
                "bluetooth" -> android.telecom.CallAudioState.ROUTE_BLUETOOTH
                else -> null
            }
            if (route != null) {
                setAudioRouteCompat(route)
                Log.d("DialerInCallService", "Successfully called setAudioRouteCompat for: $preferred ($route)")
            }
        } catch (e: Exception) {
            Log.e("DialerInCallService", "Failed to apply preferred audio route: ${e.localizedMessage}")
        }
    }

    private fun optimizeAudioForCall(active: Boolean) {
        try {
            val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            if (audioManager != null) {
                if (active) {
                    // For cellular voice calls, do NOT manually set audio mode to MODE_IN_COMMUNICATION (which is for VoIP), 
                    // as it will break the phone's hardware cellular voice path and cause absolute silence.
                    // Instead, let the Telecom framework handle the mode.
                    Log.d("DialerInCallService", "Not modifying audio mode manually for cellular call active status.")
                } else {
                    Log.d("DialerInCallService", "Not modifying audio mode manually for cellular call ended status.")
                }
            }
        } catch (e: Exception) {
            Log.e("DialerInCallService", "Failed to optimize audio: ${e.localizedMessage}")
        }
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        Log.d("DialerInCallService", "Call added: ${call?.details}")
        call?.registerCallback(callCallback)
        // Apply the preferred audio route once when the call is added.
        // Avoid repeated delayed overrides to prevent audio routing fight loops with PC Smartphone-Link or Bluetooth.
        applyPreferredAudioRoute()

        val handle = call?.details?.handle
        val number = handle?.schemeSpecificPart ?: ""
        Log.d("DialerInCallService", "Retrieved phone number: $number")

        // Set static state
        activeCall.value = call
        updateBubble()
        activeCallNumber.value = number
        activeCallName.value = "Unbekannt" // default before async query
        activeCallCompany.value = ""
        activeCallReason.value = ""
        activeCallNotes.value = ""
        activeCallTranscript.value = "" // Reset transcript for the new call!

        // Run contact lookup on IO thread to prevent main thread lag
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val normalized = number.replace("[^\\d+]".toRegex(), "")
            val db = com.example.database.AppDatabase.getDatabase(this@DialerInCallService)
            val dao = db.stromrufDao()
            
            // Try local contact lookup first
            var localContact = dao.getContactByPhone(normalized)
            if (localContact == null) {
                if (normalized.startsWith("+49")) {
                    // Try with 0 instead of +49
                    val alternate = "0" + normalized.substring(3)
                    localContact = dao.getContactByPhone(alternate)
                } else if (normalized.startsWith("0")) {
                    // Try with +49 instead of 0
                    val alternate = "+49" + normalized.substring(1)
                    localContact = dao.getContactByPhone(alternate)
                }
            }
            if (localContact == null) {
                // Fallback: search all local contacts using robust matching
                try {
                    val allLocal = dao.getAllContactsList()
                    localContact = allLocal.firstOrNull {
                        ContactsUtil.arePhoneNumbersMatching(it.phone, number)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (localContact != null) {
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    activeCallName.value = localContact.name
                    activeCallCompany.value = localContact.company ?: ""
                    activeCallReason.value = localContact.callReason ?: ""
                    // If there's a last outcome or notes, we can set them
                    activeCallNotes.value = localContact.email?.let { "E-Mail: $it" } ?: ""
                    val currentState = activeCallState.value
                    if (currentState != Call.STATE_RINGING) {
                        showOngoingCallNotification(number, localContact.name, currentState)
                    }
                }
            } else {
                // Try system contact lookup
                val name = ContactsUtil.lookupContactName(this@DialerInCallService, number)
                if (name != null) {
                    val systemContacts = ContactsUtil.searchSystemContacts(this@DialerInCallService, name)
                    val matchedSys = systemContacts.firstOrNull { 
                        it.name == name || ContactsUtil.arePhoneNumbersMatching(it.phone, number)
                    }
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        activeCallName.value = name
                        activeCallReason.value = matchedSys?.tag ?: ""
                        val currentState = activeCallState.value
                        if (currentState != Call.STATE_RINGING) {
                            showOngoingCallNotification(number, name, currentState)
                        }
                    }
                }
            }
        }
        
        val state = call?.state ?: Call.STATE_NEW
        activeCallState.value = state
        if (state == Call.STATE_RINGING) {
            wasRinging = true
            // Wake the screen up using a PowerManager WakeLock
            try {
                val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                if (powerManager != null) {
                    val isScreenOn = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
                        powerManager.isInteractive
                    } else {
                        @Suppress("DEPRECATION")
                        powerManager.isScreenOn
                    }
                    if (!isScreenOn) {
                        @Suppress("DEPRECATION")
                        val wakeLock = powerManager.newWakeLock(
                            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "Stromruf:WakeLockIncoming"
                        )
                        wakeLock.acquire(3000)
                    }
                }
            } catch (e: Exception) {
                Log.e("DialerInCallService", "Failed to acquire WakeLock: ${e.localizedMessage}")
            }

            // Launch MainActivity to pop up full screen even over lockscreen
            try {
                val intent = android.content.Intent(this, Class.forName("com.example.MainActivity")).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("DialerInCallService", "Failed to launch MainActivity for incoming call: ${e.localizedMessage}")
            }
        } else if (state == Call.STATE_ACTIVE) {
            optimizeAudioForCall(true)
            startSpeechToText()
        }

        if (state != Call.STATE_RINGING) {
            showOngoingCallNotification(activeCallNumber.value, activeCallName.value, state)
        }

        acquireCallWakeLock()
        updateProximitySensorState()

        // Start duration timer
        startTimer()
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        Log.d("DialerInCallService", "Call removed")
        releaseProximityWakeLock()
        releaseCallWakeLock()
        cancelOngoingCallNotification()
        if (wasRinging && !userDeclined) {
            showMissedCallNotification(activeCallNumber.value, activeCallName.value)
        }
        call?.unregisterCallback(callCallback)
        resetCallState()
    }

    private fun resetCallState() {
        releaseProximityWakeLock()
        releaseCallWakeLock()
        saveTranscriptToDb(this)
        stopSpeechToText()
        activeCall.value = null
        updateBubble()
        activeCallNumber.value = ""
        activeCallName.value = ""
        activeCallCompany.value = ""
        activeCallReason.value = ""
        activeCallNotes.value = ""
        activeCallState.value = Call.STATE_NEW
        wasRinging = false
        userDeclined = false
        optimizeAudioForCall(false)
        stopTimer()
        cancelOngoingCallNotification()
    }

    private fun showOngoingCallNotification(number: String, name: String, state: Int) {
        val context = this
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        
        val channelId = "ongoing_calls_channel_v2"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelName = "Aktive Anrufe"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(channelId, channelName, importance).apply {
                description = "Benachrichtigungen über laufende Anrufe"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SHOW_ACTIVE_CALL_MASK", true)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            100,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val hangUpIntent = android.content.Intent("com.example.ACTION_HANG_UP").apply {
            setClass(context, com.example.receiver.HangUpReceiver::class.java)
        }
        val hangUpPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            101,
            hangUpIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val stateText = when (state) {
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "Wählt..."
            Call.STATE_RINGING -> "Klingelt..."
            Call.STATE_ACTIVE -> "Gespräch aktiv"
            Call.STATE_HOLDING -> "Gehalten"
            else -> "Laufender Anruf"
        }
        
        val title = if (name.isNotEmpty() && name != "Unbekannt") name else number
        val text = "$stateText | Tippen, um Notizen zu öffnen"
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Auflegen",
                hangUpPendingIntent
            )
            
        notificationManager.notify(ONGOING_CALL_NOTIFICATION_ID, builder.build())
    }

    private fun cancelOngoingCallNotification() {
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.cancel(ONGOING_CALL_NOTIFICATION_ID)
    }

    private fun showMissedCallNotification(number: String, name: String) {
        val context = this
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        
        val channelId = "missed_calls_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelName = "Verpasste Anrufe"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel(channelId, channelName, importance).apply {
                description = "Benachrichtigungen über verpasste Anrufe"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // PendingIntent to open MainActivity
        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // Callback PendingIntent: "Zurückrufen" action
        val callbackIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_IMMEDIATELY", number)
            putExtra("CALL_IMMEDIATELY_NAME", name)
        }
        val callbackPendingIntent = android.app.PendingIntent.getActivity(
            context,
            1,
            callbackIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = "Verpasster Anruf"
        val text = if (name.isNotEmpty() && name != "Unbekannt") {
            "$name ($number)"
        } else {
            number.ifEmpty { "Unbekannter Anrufer" }
        }
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_missed_call)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.ic_menu_call,
                "Zurückrufen",
                callbackPendingIntent
            )
            
        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), builder.build())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximityWakeLock()
        releaseCallWakeLock()
        instance = null
        removeBubble()
    }

    private fun updateBubble() {
        if (!isAppInForeground && Companion.activeCall.value != null && android.provider.Settings.canDrawOverlays(this)) {
            showBubble()
        } else {
            removeBubble()
        }
    }

    private fun showBubble() {
        if (floatingView != null) return

        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        params.x = 0
        params.y = 100

        val view = android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val button = android.widget.Button(this@DialerInCallService).apply {
                text = "☎ Stromruf"
                setBackgroundColor(android.graphics.Color.parseColor("#00FF87"))
                setTextColor(android.graphics.Color.BLACK)
                setOnClickListener {
                    // Open App
                    val intent = android.content.Intent(this@DialerInCallService, com.example.MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                }
            }
            addView(button)
        }

        floatingView = view
        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeBubble() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {}
            floatingView = null
        }
    }

    companion object {
        var isAppInForeground = true
            set(value) {
                field = value
                instance?.updateBubble()
            }
            
        const val ONGOING_CALL_NOTIFICATION_ID = 123456
        var instance: DialerInCallService? = null
        val currentAudioState = mutableStateOf<android.telecom.CallAudioState?>(null)

        val activeCallTranscript = mutableStateOf("")
        var speechRecognizer: android.speech.SpeechRecognizer? = null
        val isRecognizerListening = mutableStateOf(false)

        fun saveTranscriptToDb(context: android.content.Context) {
            val phone = activeCallNumber.value
            val name = activeCallName.value.ifBlank { "Unbekannter Partner" }
            val transcriptText = activeCallTranscript.value
            if (transcriptText.isNotBlank()) {
                val db = com.example.database.AppDatabase.getDatabase(context)
                val dao = db.stromrufDao()
                val repository = com.example.repository.StromrufRepository(context, dao)
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val entity = com.example.database.AiCallEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        phone = phone.ifBlank { "Unbekannt" },
                        contactName = name,
                        timestamp = System.currentTimeMillis(),
                        audioFilePath = null,
                        transcript = transcriptText,
                        durationSeconds = callDurationSeconds.value,
                        notes = "Automatische Anrufs-Notiz"
                    )
                    repository.insertAiCall(entity)
                    Log.d("DialerInCallService", "Automatically saved AI Call Note with transcript: $transcriptText")
                }
            }
        }

        fun startSpeechToText() {
            val ctx = instance ?: return

            // WICHTIG: Der SpeechRecognizer belegt während eines Anrufs das Mikrofon
            // und den Audio-Fokus. Auf vielen Geräten bricht dadurch der Sprachweg der
            // Mobilfunkverbindung zusammen -> „kein Ton". Deshalb standardmäßig AUS.
            val prefs = ctx.getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE)
            val sttEnabled = prefs.getBoolean("stt_during_call_enabled", false)
            if (!sttEnabled) {
                Log.d("DialerInCallService", "STT during call is disabled (protects call audio). Skipping.")
                return
            }

            // Post to main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    if (speechRecognizer != null) {
                        stopSpeechToText()
                    }
                    
                    val recognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(ctx)
                    speechRecognizer = recognizer
                    
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().language)
                        putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                    
                    recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                        override fun onReadyForSpeech(params: android.os.Bundle?) {
                            isRecognizerListening.value = true
                            Log.d("DialerInCallService", "STT onReadyForSpeech")
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        
                        override fun onError(error: Int) {
                            Log.e("DialerInCallService", "STT onError: $error")
                            isRecognizerListening.value = false
                            // Automatically restart if call is still active
                            if (activeCall.value != null) {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (activeCall.value != null) {
                                        startSpeechToText()
                                    }
                                }, 1000)
                            }
                        }
                        
                        override fun onResults(results: android.os.Bundle?) {
                            isRecognizerListening.value = false
                            val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                val current = activeCallTranscript.value
                                activeCallTranscript.value = if (current.isBlank()) text else "$current $text"
                            }
                            Log.d("DialerInCallService", "STT onResults: $text")
                            // Restart for continuous typing during call
                            if (activeCall.value != null) {
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (activeCall.value != null) {
                                        startSpeechToText()
                                    }
                                }, 400)
                            }
                        }
                        
                        override fun onPartialResults(partialResults: android.os.Bundle?) {
                            val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                Log.d("DialerInCallService", "STT onPartialResults: $text")
                            }
                        }
                        
                        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                    })
                    
                    recognizer.startListening(intent)
                    Log.d("DialerInCallService", "SpeechRecognizer started listening in InCallService")
                } catch (e: Exception) {
                    Log.e("DialerInCallService", "Error starting SpeechRecognizer: ${e.localizedMessage}")
                }
            }
        }
        
        fun stopSpeechToText() {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    isRecognizerListening.value = false
                    Log.d("DialerInCallService", "SpeechRecognizer stopped & destroyed in InCallService")
                } catch (e: Exception) {
                    Log.e("DialerInCallService", "Error stopping SpeechRecognizer: ${e.localizedMessage}")
                }
            }
        }

        val activeCall = mutableStateOf<Call?>(null)
        val activeCallState = mutableStateOf<Int>(Call.STATE_NEW)
        val activeCallNumber = mutableStateOf("")
        val activeCallName = mutableStateOf("")
        val activeCallCompany = mutableStateOf("")
        val activeCallReason = mutableStateOf("")
        val activeCallNotes = mutableStateOf("")
        val callDurationSeconds = mutableStateOf(0L)

        private var toneGenerator: android.media.ToneGenerator? = null

        fun playDtmf(digit: Char) {
            try {
                if (toneGenerator == null) {
                    toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_DTMF, 80)
                }
                val toneType = when (digit) {
                    '1' -> android.media.ToneGenerator.TONE_DTMF_1
                    '2' -> android.media.ToneGenerator.TONE_DTMF_2
                    '3' -> android.media.ToneGenerator.TONE_DTMF_3
                    '4' -> android.media.ToneGenerator.TONE_DTMF_4
                    '5' -> android.media.ToneGenerator.TONE_DTMF_5
                    '6' -> android.media.ToneGenerator.TONE_DTMF_6
                    '7' -> android.media.ToneGenerator.TONE_DTMF_7
                    '8' -> android.media.ToneGenerator.TONE_DTMF_8
                    '9' -> android.media.ToneGenerator.TONE_DTMF_9
                    '0' -> android.media.ToneGenerator.TONE_DTMF_0
                    '*' -> android.media.ToneGenerator.TONE_DTMF_S
                    '#' -> android.media.ToneGenerator.TONE_DTMF_P
                    else -> -1
                }
                if (toneType != -1) {
                    toneGenerator?.startTone(toneType, 120)
                }
            } catch (e: Exception) {
                Log.e("DialerInCallService", "Failed to play local DTMF tone: ${e.localizedMessage}")
            }

            activeCall.value?.let {
                try {
                    it.playDtmfTone(digit)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            it.stopDtmfTone()
                        } catch (e: Exception) {
                            Log.e("DialerInCallService", "Failed to stop DTMF tone: ${e.localizedMessage}")
                        }
                    }, 150)
                } catch (e: Exception) {
                    Log.e("DialerInCallService", "Failed to play telecom DTMF tone: ${e.localizedMessage}")
                }
            }
        }

        private var wasRinging = false
        private var userDeclined = false

        private var timerJob: kotlinx.coroutines.Job? = null
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

        fun answerCall() {
            activeCall.value?.let {
                try {
                    it.answer(0)
                } catch (e: Exception) {
                    Log.e("DialerInCallService", "Failed to answer call: ${e.localizedMessage}")
                }
            }
        }

        fun declineCall() {
            userDeclined = true
            activeCall.value?.let {
                try {
                    it.disconnect()
                } catch (e: Exception) {
                    Log.e("DialerInCallService", "Failed to decline call: ${e.localizedMessage}")
                }
            }
            activeCall.value = null
            instance?.updateBubble()
            activeCallNumber.value = ""
            activeCallName.value = ""
            activeCallCompany.value = ""
            activeCallReason.value = ""
            activeCallNotes.value = ""
            activeCallState.value = Call.STATE_NEW
            wasRinging = false
            stopTimer()
        }

        fun hangUp() {
            // Disconnect the active call reference
            activeCall.value?.let {
                try {
                    it.disconnect()
                } catch (e: Exception) {
                    Log.e("DialerInCallService", "Failed to disconnect tracked activeCall: ${e.localizedMessage}")
                }
            }
            // ALSO proactively iterate and disconnect all other calls registered in InCallService to guarantee hanging up
            try {
                instance?.calls?.forEach { call ->
                    Log.d("DialerInCallService", "Disconnecting call from list: ${call.details}")
                    call.disconnect()
                }
            } catch (e: java.lang.Exception) {
                Log.e("DialerInCallService", "Failed to disconnect calls in list: ${e.localizedMessage}")
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val tm = instance?.getSystemService(android.telecom.TelecomManager::class.java)
                    tm?.endCall()
                }
            } catch (e: Throwable) {
                Log.e("DialerInCallService", "TelecomManager endCall fallback: ${e.localizedMessage}")
            }
            activeCall.value = null
            instance?.updateBubble()
            activeCallNumber.value = ""
            activeCallName.value = ""
            activeCallCompany.value = ""
            activeCallReason.value = ""
            activeCallNotes.value = ""
            activeCallState.value = Call.STATE_DISCONNECTED
            wasRinging = false
            userDeclined = false
            stopTimer()
        }

        private fun startTimer() {
            callDurationSeconds.value = 0L
            timerJob?.cancel()
            timerJob = scope.launch {
                while (activeCall.value != null) {
                    kotlinx.coroutines.delay(1000)
                    callDurationSeconds.value += 1
                }
            }
        }

        private fun stopTimer() {
            timerJob?.cancel()
            timerJob = null
        }
    }
}
