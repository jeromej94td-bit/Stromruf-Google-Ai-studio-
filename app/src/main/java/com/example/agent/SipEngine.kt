package com.example.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class SipCall(
    val id: String = UUID.randomUUID().toString(),
    val remoteNumber: String = ""
) {
    var recordingStarted = false

    fun startRecording() {
        recordingStarted = true
    }
    fun stopRecording() {
        recordingStarted = false
    }
    fun terminate() {
        recordingStarted = false
    }
}

/** Ein registrierter SIP-Trunk, beliebig viele parallele Calls darüber. */
object SipEngine {
    private val _status = MutableStateFlow("Nicht verbunden")
    val status: StateFlow<String> = _status

    var onIncomingCall: ((SipCall) -> Unit)? = null
    var onCallEnded: ((SipCall) -> Unit)? = null

    private val activeCallsList = mutableListOf<SipCall>()

    fun init(context: Context) {
        // Initialization
    }

    fun register(c: RuntimeConfig) {
        if (c.sipUser.isNotBlank() && c.sipDomain.isNotBlank()) {
            _status.value = "Registriert"
        } else {
            _status.value = "Nicht verbunden"
        }
    }

    fun invite(nummer: String, recordFile: String?): SipCall? {
        if (_status.value != "Registriert") return null
        val call = SipCall(remoteNumber = nummer)
        activeCallsList.add(call)
        return call
    }

    fun accept(call: SipCall, recordFile: String?) {
        if (!activeCallsList.contains(call)) {
            activeCallsList.add(call)
        }
    }

    fun aktiveCalls(): Int = activeCallsList.size

    fun beenden(call: SipCall) {
        call.terminate()
        activeCallsList.remove(call)
        onCallEnded?.invoke(call)
    }
}
