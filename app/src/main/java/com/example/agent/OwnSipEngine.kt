package com.example.agent

import android.content.Context
import android.media.MediaRecorder
import android.net.sip.SipAudioCall
import android.net.sip.SipManager
import android.net.sip.SipProfile
import android.net.sip.SipRegistrationListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OwnSipEngine {
    private lateinit var context: Context
    private var manager: SipManager? = null
    private var profile: SipProfile? = null
    private var activeCall: SipAudioCall? = null
    private var recorder: MediaRecorder? = null
    private val _status = MutableStateFlow("Nicht verbunden")
    val status: StateFlow<String> = _status
    private val _activeNumber = MutableStateFlow<String?>(null)
    val activeNumber: StateFlow<String?> = _activeNumber

    fun init(context: Context) { this.context = context.applicationContext; manager = SipManager.newInstance(this.context) }

    fun register(config: OwnSipConfig, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (!config.isComplete()) { _status.value = "Zugangsdaten fehlen"; onResult(false, "Benutzername, Passwort und Registrar eintragen."); return }
        runCatching {
            profile?.let { manager?.close(it.uriString) }
            val newProfile = SipProfile.Builder(config.username, config.registrar).setAuthUserName(config.username)
                .setPassword(config.password).setPort(config.port).setProtocol(config.transport)
                .apply { if (config.proxy.isNotBlank()) setOutboundProxy(config.proxy) }.build()
            val sipManager = requireNotNull(manager) { "SIP ist auf diesem Gerät nicht verfügbar." }
            sipManager.open(newProfile)
            profile = newProfile
            sipManager.register(newProfile, 300, object : SipRegistrationListener {
                override fun onRegistering(localProfileUri: String?) { _status.value = "Registriere..." }
                override fun onRegistrationDone(localProfileUri: String?, expiryTime: Long) {
                    _status.value = "Registriert"
                    onResult(true, "Easybell ist registriert.")
                }
                override fun onRegistrationFailed(localProfileUri: String?, errorCode: Int, errorMessage: String?) {
                    _status.value = "Registrierung fehlgeschlagen"
                    onResult(false, errorMessage ?: "SIP-Fehler ($errorCode).")
                }
            })
        }.onFailure { _status.value = "Registrierungsfehler"; onResult(false, it.message ?: "SIP-Registrierung fehlgeschlagen.") }
    }

    fun call(number: String, config: OwnSipConfig, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val local = profile ?: run { onResult(false, "Erst Easybell verbinden."); return }
        val target = number.trim().ifBlank { onResult(false, "Telefonnummer eintragen."); return }
        runCatching {
            val uri = if (target.startsWith("sip:")) target else "sip:$target@${config.registrar}"
            activeCall = manager?.makeAudioCall(local.uriString, uri, object : SipAudioCall.Listener() {
                override fun onRinging(call: SipAudioCall?, response: SipProfile?) { _status.value = "Klingelt" }
                override fun onCallEstablished(call: SipAudioCall?, remote: SipProfile?) {
                    activeCall = call; _activeNumber.value = target; _status.value = "Im Gespräch"; call?.startAudio(); startRecording(target); onResult(true, "Anruf verbunden.")
                }
                override fun onCallEnded(call: SipAudioCall?) { finishCall() }
                override fun onError(call: SipAudioCall?, code: Int, message: String?) { finishCall(); onResult(false, message ?: "SIP-Fehler ($code).") }
            }, 30)
            _activeNumber.value = target; _status.value = "Wählt..."
        }.onFailure { onResult(false, it.message ?: "Anruf konnte nicht gestartet werden.") }
    }

    fun hangUp() { runCatching { activeCall?.endCall() }; finishCall() }

    private fun startRecording(number: String) {
        val dir = File(context.filesDir, "smart_calls").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "call_${stamp}_${number.replace(Regex("[^0-9+]"), "_")}.m4a")
        runCatching {
            recorder = MediaRecorder().apply { setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.absolutePath); prepare(); start() }
        }.onFailure { Log.e("OwnSipEngine", "Aufnahme konnte nicht gestartet werden", it); recorder = null }
    }

    private fun finishCall() { runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null; activeCall = null; _activeNumber.value = null; if (_status.value != "Registrierung fehlgeschlagen") _status.value = "Registriert" }
}

