package com.example.sip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.recording.RecordingStorageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.linphone.core.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-scoped adapter. Only liblinphone implements SIP, SDP, RTP and SRTP.
 * All SDK access is on its creation thread (Android main); no activity owns a call.
 */
class LinphoneSipClient private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: LinphoneSipClient? = null
        fun getInstance(context: Context): LinphoneSipClient =
            instance ?: synchronized(this) {
                instance ?: LinphoneSipClient(context.applicationContext).also { instance = it }
            }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(SipState.DISCONNECTED)
    val state: StateFlow<SipState> = _state
    private val _statusText = MutableStateFlow("Nicht verbunden")
    val statusText: StateFlow<String> = _statusText
    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _lastRecordingFile = MutableStateFlow<File?>(null)
    val lastRecordingFile: StateFlow<File?> = _lastRecordingFile
    var muted = false
        private set
    var speakerOn = true
        private set

    private var core: Core? = null
    private var account: Account? = null
    private var config: SipAccountConfig? = null
    private var activeCall: Call? = null
    private var pendingNumber: String? = null
    private var recordingFile: File? = null
    private var recordingStarted = false
    private var durationJob: Job? = null
    private var disconnectAfterCall = false
    private var callFailed = false

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post { block() }
    }

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core, account: Account, state: RegistrationState, message: String
        ) {
            if (account != this@LinphoneSipClient.account || activeCall != null || pendingNumber != null) return
            when (state) {
                RegistrationState.Ok -> {
                    _state.value = SipState.REGISTERED
                    _statusText.value = "Registriert – Linphone / ${config?.protocol?.name}"
                }
                RegistrationState.Progress, RegistrationState.Refreshing -> {
                    _state.value = SipState.CONNECTING
                    _statusText.value = "SIP-Anmeldung läuft …"
                }
                RegistrationState.Failed -> fail("SIP-Anmeldung fehlgeschlagen")
                RegistrationState.Cleared, RegistrationState.None -> {
                    _state.value = SipState.DISCONNECTED
                    _statusText.value = "Nicht verbunden"
                }
            }
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            // This screen supports one outgoing call. Do not let another dialog replace it.
            if (state == Call.State.IncomingReceived) {
                call.decline(Reason.Busy)
                return
            }
            if (activeCall == null && state == Call.State.OutgoingInit && pendingNumber != null) activeCall = call
            if (call != activeCall) return
            // No SIP headers, credentials, SDP keys or phone numbers in diagnostic logs.
            Log.i("SmartCalls", "Linphone state=$state duration=${call.duration}s code=${call.errorInfo.protocolCode}")
            when (state) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> {
                    _state.value = SipState.DIALING
                    _statusText.value = "Anruf wird aufgebaut …"
                }
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia -> {
                    _state.value = SipState.RINGING
                    _statusText.value = "Es klingelt …"
                }
                Call.State.Connected, Call.State.StreamsRunning -> {
                    _state.value = SipState.IN_CALL
                    _statusText.value = "Im Gespräch – Linphone"
                    core.setMicEnabled(!muted)
                    applyAudioRoute()
                    if (durationJob == null) durationJob = scope.launch {
                        while (isActive) {
                            _callDurationSeconds.value = call.duration
                            delay(1000)
                        }
                    }
                    if (state == Call.State.StreamsRunning && !recordingStarted) {
                        call.startRecording()
                        recordingStarted = call.isRecording
                        _isRecording.value = recordingStarted
                        if (!recordingStarted) _statusText.value = "Im Gespräch – Aufnahme konnte nicht starten"
                    }
                }
                Call.State.Error -> {
                    callFailed = true
                    fail("Anruf beendet: ${call.reason} (SIP ${call.errorInfo.protocolCode})")
                    finishMedia(call)
                }
                Call.State.End -> {
                    finishMedia(call)
                    _state.value = if (account?.state == RegistrationState.Ok) SipState.REGISTERED else SipState.DISCONNECTED
                    _statusText.value = "Gespräch beendet"
                }
                Call.State.Released -> {
                    finishMedia(call)
                    activeCall = null
                    pendingNumber = null
                    recordingFile?.takeIf { it.exists() && it.length() > 44 }?.let { file ->
                        // Released means the native recorder has closed/finalized the WAV.
                        _lastRecordingFile.value = file
                        val finishedDuration = _callDurationSeconds.value
                        scope.launch(Dispatchers.IO) {
                            if (finishedDuration > 60) {
                                runCatching {
                                    com.example.transcription.offline.LocalTranscripts.request(context, file)
                                }.onFailure { Log.w("SmartCalls", "Lokale Transkription konnte nicht eingeplant werden", it) }
                            }
                            val storage = RecordingStorageManager(context)
                            if (storage.isAutoExportEnabled() && storage.getCustomFolderUri() != null) {
                                storage.saveFileToCustomFolder(file)
                            }
                        }
                    }
                    recordingFile = null
                    if (!callFailed && _state.value == SipState.IN_CALL) {
                        _state.value = SipState.DISCONNECTED
                        _statusText.value = "Gespräch beendet"
                    }
                    context.stopService(Intent(context, SmartCallService::class.java))
                    if (disconnectAfterCall) disconnect()
                }
                else -> Unit // SDK owns hold, re-INVITE and session refresh.
            }
        }
    }

    private fun getCore(): Core = core ?: Factory.instance().run {
        // Memory-only SDK config: credentials stay in the app's encrypted preferences.
        val sdkConfig = createConfigFromString("[sip]\nstore_auth_info=0\n")
        createCoreWithConfig(sdkConfig, context).also { engine ->
            core = engine
            engine.addListener(listener)
            engine.setAutoIterateEnabled(true)
            engine.setKeepAliveEnabled(true)
            engine.verifyServerCertificates(true)
            engine.verifyServerCn(true)
            engine.setVideoCaptureEnabled(false)
            engine.setVideoDisplayEnabled(false)
            engine.setAudioPayloadTypes(engine.audioPayloadTypes.filter {
                it.mimeType.equals("PCMA", true) || it.mimeType.equals("PCMU", true)
            }.toTypedArray())
            engine.setUserAgent("Stromruf", "2.0")
            engine.inCallTimeout = 0 // No application-imposed maximum connected duration.
            check(engine.start() == 0) { "Linphone konnte nicht starten" }
        }
    }

    fun register(settings: SipAccountConfig) = onMain {
        if (activeCall != null || pendingNumber != null) return@onMain
        try {
            require(settings.port in 1..65535)
            val engine = getCore()
            account = null
            engine.clearAccounts()
            engine.clearAllAuthInfo()
            config = settings
            val transport = when (settings.protocol) {
                SipTransportProtocol.TLS -> TransportType.Tls
                SipTransportProtocol.TCP -> TransportType.Tcp
                SipTransportProtocol.UDP -> TransportType.Udp
            }
            val factory = Factory.instance()
            val server = requireNotNull(factory.createAddress("sip:${settings.sipRegistrar}:${settings.port}"))
            server.transport = transport
            val identity = requireNotNull(factory.createAddress("sip:${settings.sipUser}@${settings.sipRegistrar}"))
            identity.displayName = settings.displayName
            engine.addAuthInfo(factory.createAuthInfo(
                settings.sipUser, settings.authUser.ifBlank { settings.sipUser },
                settings.sipPassword, null, null, settings.sipRegistrar
            ))
            val params = engine.createAccountParams()
            params.identityAddress = identity
            params.serverAddress = server
            params.setRegisterEnabled(true)
            params.setOutboundProxyEnabled(true)
            val newAccount = engine.createAccount(params)
            account = newAccount
            _state.value = SipState.CONNECTING
            _statusText.value = "SIP-Anmeldung läuft …"
            check(engine.addAccount(newAccount) == 0)
            engine.defaultAccount = newAccount
        } catch (e: Exception) {
            fail("SIP-Einrichtung fehlgeschlagen (${e.javaClass.simpleName})")
        }
    }

    fun makeCall(number: String) = onMain {
        if (_state.value != SipState.REGISTERED || activeCall != null || pendingNumber != null) return@onMain
        val normalized = number.filterNot { it.isWhitespace() || it in "()-" }
        if (!normalized.matches(Regex("[+]?[0-9*#]+"))) {
            _statusText.value = "Bitte eine gültige Zielrufnummer eingeben"
            return@onMain
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _statusText.value = "Bitte Mikrofonzugriff erlauben"
            return@onMain
        }
        pendingNumber = normalized
        _state.value = SipState.DIALING
        _statusText.value = "Telefonie wird gestartet …"
        try {
            val intent = Intent(context, SmartCallService::class.java).setAction(SmartCallService.START_CALL)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        } catch (e: Exception) {
            pendingNumber = null
            fail("Telefoniedienst konnte nicht starten (${e.javaClass.simpleName})")
        }
    }

    /** Called only after the service has entered foreground with microphone access. */
    internal fun startPendingCall() = onMain {
        val number = pendingNumber ?: return@onMain
        if (activeCall != null) return@onMain
        try {
            val engine = getCore()
            val settings = requireNotNull(config)
            val target = requireNotNull(Factory.instance().createAddress("sip:$number@${settings.sipRegistrar}"))
            val params = requireNotNull(engine.createCallParams(null))
            params.setVideoEnabled(false)
            params.mediaEncryption = if (settings.protocol == SipTransportProtocol.TLS) MediaEncryption.SRTP else MediaEncryption.None
            engine.setMediaEncryptionMandatory(settings.protocol == SipTransportProtocol.TLS)
            val dir = File(context.filesDir, "smart_calls_recordings")
            check(dir.isDirectory || dir.mkdirs())
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            recordingFile = File(dir, "Call_${number}_$timestamp.wav")
            params.recordFile = recordingFile!!.absolutePath
            recordingStarted = false
            callFailed = false
            disconnectAfterCall = false
            _lastRecordingFile.value = null
            _callDurationSeconds.value = 0
            _isRecording.value = false
            activeCall = requireNotNull(engine.inviteAddressWithParams(target, params))
            pendingNumber = null
        } catch (e: Exception) {
            pendingNumber = null
            fail("Anrufstart fehlgeschlagen (${e.javaClass.simpleName})")
            context.stopService(Intent(context, SmartCallService::class.java))
        }
    }

    private fun finishMedia(call: Call) {
        durationJob?.cancel()
        durationJob = null
        _callDurationSeconds.value = maxOf(_callDurationSeconds.value, call.duration)
        if (recordingStarted) call.stopRecording()
        recordingStarted = false
        _isRecording.value = false
    }

    fun hangUp() = onMain {
        pendingNumber = null
        val call = activeCall
        if (call != null) {
            call.terminate()
        } else {
            _state.value = if (account?.state == RegistrationState.Ok) SipState.REGISTERED else SipState.DISCONNECTED
            _statusText.value = "Anruf beendet"
            context.stopService(Intent(context, SmartCallService::class.java))
        }
    }

    fun disconnect() = onMain {
        pendingNumber = null
        if (activeCall != null) {
            disconnectAfterCall = true
            activeCall?.terminate()
            return@onMain
        }
        disconnectAfterCall = false
        account = null
        core?.clearAccounts()
        core?.clearAllAuthInfo()
        config = null
        _state.value = SipState.DISCONNECTED
        _statusText.value = "Nicht verbunden"
        context.stopService(Intent(context, SmartCallService::class.java))
    }

    fun setMuted(value: Boolean) = onMain {
        muted = value
        core?.setMicEnabled(!value)
    }

    fun setSpeakerphoneOn(value: Boolean) = onMain {
        speakerOn = value
        applyAudioRoute()
    }

    private fun applyAudioRoute() {
        val engine = core ?: return
        val type = if (speakerOn) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        engine.audioDevices.firstOrNull { it.type == type }?.let { engine.outputAudioDevice = it }
    }

    private fun fail(message: String) {
        _state.value = SipState.ERROR
        _statusText.value = message
    }

    internal fun serviceStoppedUnexpectedly() = onMain {
        if (_state.value in setOf(SipState.DIALING, SipState.RINGING, SipState.IN_CALL)) {
            hangUp()
            fail("Android hat den Telefoniedienst beendet")
        }
    }
}
