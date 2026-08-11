package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.service.DialerInCallService

class HangUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_HANG_UP") {
            Log.d("HangUpReceiver", "Hang up action received from Broadcast!")
            DialerInCallService.hangUp()
        }
    }
}
