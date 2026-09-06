package com.example.homesip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

data class HomeSipSettings(
    val user: String = "",
    val authUser: String = "",
    val password: String = "",
    val registrar: String = "secure.sip.easybell.de",
    val port: Int = 5061
)

enum class HomeSipStatus { OFFLINE, CONNECTING, READY, DIALING, RINGING, IN_CALL, ERROR }

data class HomeSipState(
    val status: HomeSipStatus = HomeSipStatus.OFFLINE,
    val message: String = "Nicht verbunden"
)

internal class HomeSipSettingsStore(context: Context) {
    private val prefs = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "home_sip_trunk",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("home_sip_trunk_fallback", Context.MODE_PRIVATE)
    }

    fun load() = HomeSipSettings(
        user = prefs.getString("user", "") ?: "",
        authUser = prefs.getString("auth_user", "") ?: "",
        password = prefs.getString("password", "") ?: "",
        registrar = prefs.getString("registrar", "secure.sip.easybell.de") ?: "secure.sip.easybell.de",
        port = prefs.getInt("port", 5061)
    )

    fun save(settings: HomeSipSettings) {
        prefs.edit()
            .putString("user", settings.user)
            .putString("auth_user", settings.authUser)
            .putString("password", settings.password)
            .putString("registrar", settings.registrar)
            .putInt("port", settings.port)
            .apply()
    }
}

/**
 * Gold-Master SIP implementation for the Home screen.
 * liblinphone owns SIP, TLS, SDP, RTP, SRTP, dialog routing and session refresh.
 */
class HomeSipTrunk private constructor(private val appContext: Context) {
    companion object {
        @Volatile private var instance: HomeSipTrunk? = null
        fun get(context: Context): HomeSipTrunk = instance ?: synchronized(this) {
            instance ?: HomeSipTrunk(context.applicationContext).also { instance = it }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(HomeSipState())
    val state: StateFlow<HomeSipState> = _state
    private val settingsStore = HomeSipSettingsStore(appContext)
    private var core: Core? = null
    private var account: Account? = null
    private var settings: HomeSipSettings? = null
    private var activeCall: Call? = null
    private var pendingNumber: String? = null

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core, account: Account, registrationState: RegistrationState, message: String
        ) {
            if (account != this@HomeSipTrunk.account) return
            // A REGISTER refresh must never overwrite an active call state with READY.
            if (activeCall != null || pendingNumber != null) return
            when (registrationState) {
                RegistrationState.Ok -> _state.value = HomeSipState(HomeSipStatus.READY, "SIP-Trunk verbunden")
                RegistrationState.Progress, RegistrationState.Refreshing ->
                    _state.value = HomeSipState(HomeSipStatus.CONNECTING, "Sichere SIP-Anmeldung läuft …")
                RegistrationState.Failed ->
                    _state.value = HomeSipState(HomeSipStatus.ERROR, readableRegistrationError(message))
                RegistrationState.Cleared, RegistrationState.None ->
                    _state.value = HomeSipState(HomeSipStatus.OFFLINE, "Nicht verbunden")
            }
        }

        override fun onCallStateChanged(core: Core, call: Call, callState: Call.State, message: String) {
            if (callState == Call.State.IncomingReceived) {
                call.decline(Reason.Busy)
                return
            }
            if (activeCall == null && callState == Call.State.OutgoingInit && pendingNumber != null) activeCall = call
            if (call != activeCall) return

            // Smart automation is a sidecar. A recording/DB exception must never break SIP state handling.
            runCatching { HomeSipSmartAutomation.onCallState(appContext, call, callState) }
                .onFailure { Log.w("HomeSipTrunk", "Smart-Call sidecar error in $callState", it) }

            when (callState) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress ->
                    _state.value = HomeSipState(HomeSipStatus.DIALING, "Anruf wird aufgebaut …")
                Call.State.OutgoingRinging, Call.State.OutgoingEarlyMedia ->
                    _state.value = HomeSipState(HomeSipStatus.RINGING, "Es klingelt …")
                Call.State.Connected, Call.State.StreamsRunning ->
                    _state.value = HomeSipState(HomeSipStatus.IN_CALL, "Im Gespräch · verschlüsselte Medien")
                Call.State.Error -> {
                    _state.value = HomeSipState(
                        HomeSipStatus.ERROR,
                        "Anruf fehlgeschlagen (SIP ${call.errorInfo.protocolCode})"
                    )
                    // Keep the foreground service and activeCall until Released. Linphone still owns
                    // native dialog/media cleanup at this point.
                }
                Call.State.End -> {
                    // IMPORTANT: End is not the final native lifecycle state. Do not null activeCall
                    // here. Otherwise Released is ignored, the recorder/session is left stale and the
                    // next call can fail immediately after answer.
                    _state.value = HomeSipState(HomeSipStatus.DIALING, "Gespräch wird beendet …")
                }
                Call.State.Released -> {
                    activeCall = null
                    pendingNumber = null
                    _state.value = if (account?.state == RegistrationState.Ok) {
                        HomeSipState(HomeSipStatus.READY, "SIP-Trunk verbunden")
                    } else {
                        HomeSipState(HomeSipStatus.OFFLINE, "Gespräch beendet")
                    }
                    stopCallService()
                }
                else -> Unit
            }
        }
    }

    private fun core(): Core = core ?: Factory.instance().run {
        val config = createConfigFromString("[sip]\nstore_auth_info=0\n")
        createCoreWithConfig(config, appContext).also { created ->
            core = created
            created.addListener(listener)
            created.setAutoIterateEnabled(true)
            created.setKeepAliveEnabled(true)
            created.verifyServerCertificates(true)
            created.verifyServerCn(true)
            created.setVideoCaptureEnabled(false)
            created.setVideoDisplayEnabled(false)
            created.inCallTimeout = 0
            check(created.start() == 0) { "SIP-Kern konnte nicht gestartet werden" }
        }
    }

    fun savedSettings(): HomeSipSettings = settingsStore.load()
    fun saveSettings(value: HomeSipSettings) = settingsStore.save(value)

    fun connect(newSettings: HomeSipSettings) = onMain {
        if (activeCall != null || pendingNumber != null) return@onMain
        try {
            require(newSettings.user.isNotBlank()) { "SIP-Benutzer fehlt" }
            require(newSettings.password.isNotBlank()) { "SIP-Passwort fehlt" }
            require(newSettings.registrar.isNotBlank()) { "Registrar fehlt" }
            require(newSettings.port in 1..65535) { "Ungültiger Port" }
            val cleanHost = newSettings.registrar.trim().removePrefix("sips:").removePrefix("sip:")
                .substringBefore('/').substringBefore(':').lowercase()
            require(cleanHost.isNotBlank()) { "Registrar fehlt" }
            val clean = newSettings.copy(
                user = newSettings.user.trim(),
                authUser = newSettings.authUser.trim(),
                password = newSettings.password.trim(),
                registrar = cleanHost
            )
            val engine = core()
            engine.clearAccounts()
            engine.clearAllAuthInfo()
            account = null
            settings = clean
            val factory = Factory.instance()
            val server = requireNotNull(factory.createAddress("sips:${clean.registrar}:${clean.port};transport=tls"))
            server.transport = TransportType.Tls
            val identity = requireNotNull(factory.createAddress("sip:${clean.user}@${clean.registrar}"))
            engine.addAuthInfo(factory.createAuthInfo(
                clean.user,
                clean.authUser.ifBlank { clean.user },
                clean.password,
                null,
                null,
                clean.registrar
            ))
            val params = engine.createAccountParams()
            params.identityAddress = identity
            params.serverAddress = server
            params.setRegisterEnabled(true)
            params.setOutboundProxyEnabled(true)
            val createdAccount = engine.createAccount(params)
            account = createdAccount
            _state.value = HomeSipState(HomeSipStatus.CONNECTING, "Sichere SIP-Anmeldung läuft …")
            check(engine.addAccount(createdAccount) == 0) { "SIP-Konto konnte nicht gesetzt werden" }
            engine.defaultAccount = createdAccount
        } catch (error: Exception) {
            _state.value = HomeSipState(HomeSipStatus.ERROR, error.message ?: "SIP-Einrichtung fehlgeschlagen")
        }
    }

    fun startCall(number: String) = onMain {
        if (_state.value.status != HomeSipStatus.READY || activeCall != null || pendingNumber != null) return@onMain
        val normalized = number.filter { it.isDigit() || it == '+' }
        if (normalized.length < 3) {
            _state.value = HomeSipState(HomeSipStatus.ERROR, "Bitte eine gültige Zielrufnummer eingeben")
            return@onMain
        }
        pendingNumber = normalized
        _state.value = HomeSipState(HomeSipStatus.DIALING, "Anruf wird vorbereitet …")
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, HomeSipCallService::class.java).setAction(HomeSipCallService.ACTION_START_CALL)
        )
    }

    internal fun beginPendingCall() = onMain {
        val number = pendingNumber ?: return@onMain
        if (activeCall != null) return@onMain
        val engine = core ?: return@onMain
        val config = settings ?: return@onMain
        try {
            val destination = requireNotNull(Factory.instance().createAddress("sip:$number@${config.registrar}"))
            val params = requireNotNull(engine.createCallParams(null))
            params.mediaEncryption = MediaEncryption.SRTP
            engine.setMediaEncryptionMandatory(true)
            HomeSipSmartAutomation.prepareCall(appContext, number, params)
            _state.value = HomeSipState(HomeSipStatus.DIALING, "Anruf wird aufgebaut …")
            activeCall = requireNotNull(engine.inviteAddressWithParams(destination, params)) {
                "SIP-Anruf konnte nicht initialisiert werden"
            }
        } catch (error: Exception) {
            pendingNumber = null
            HomeSipSmartAutomation.cancelPrepared()
            _state.value = HomeSipState(HomeSipStatus.ERROR, error.message ?: "Anruf konnte nicht gestartet werden")
            stopCallService()
        }
    }

    fun hangUp() = onMain {
        pendingNumber = null
        val call = activeCall
        if (call != null) {
            call.terminate()
        } else {
            stopCallService()
            _state.value = if (account?.state == RegistrationState.Ok) {
                HomeSipState(HomeSipStatus.READY, "SIP-Trunk verbunden")
            } else {
                HomeSipState(HomeSipStatus.OFFLINE, "Gespräch beendet")
            }
        }
    }

    private fun stopCallService() {
        appContext.stopService(Intent(appContext, HomeSipCallService::class.java))
    }

    private fun readableRegistrationError(message: String): String = when {
        message.contains("401") || message.contains("403") -> "SIP-Anmeldung abgelehnt – Benutzer oder Passwort prüfen"
        message.contains("certificate", ignoreCase = true) || message.contains("TLS", ignoreCase = true) ->
            "TLS-Zertifikat konnte nicht geprüft werden"
        message.contains("timeout", ignoreCase = true) || message.contains("unreachable", ignoreCase = true) ->
            "SIP-Server nicht erreichbar"
        else -> "SIP-Anmeldung fehlgeschlagen"
    }
}

class HomeSipCallService : Service() {
    companion object {
        const val ACTION_START_CALL = "com.example.homesip.START_CALL"
        private const val CHANNEL_ID = "home_sip_active_call"
        private const val NOTIFICATION_ID = 7321
    }

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("SIP-Anruf läuft")
            .setContentText("Stromruf hält die sichere Telefonieverbindung aktiv")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (wakeLock == null) {
            val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Stromruf:HomeSipCall").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (intent?.action == ACTION_START_CALL) HomeSipTrunk.get(this).beginPendingCall()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Aktive SIP-Anrufe", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
