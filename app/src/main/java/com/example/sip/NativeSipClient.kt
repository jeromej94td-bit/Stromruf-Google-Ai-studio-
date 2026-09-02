package com.example.sip

import android.content.Context
import com.example.recording.RecordingStorageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class SipTransportProtocol(val displayName: String, val defaultPort: Int) {
    UDP("UDP", 5060),
    TCP("TCP", 5060),
    TLS("TLS (Verschlüsselt)", 5061)
}

enum class SipState {
    DISCONNECTED,
    CONNECTING,
    REGISTERED,
    DIALING,
    RINGING,
    IN_CALL,
    ERROR
}

data class SipAccountConfig(
    val displayName: String = "",
    val sipUser: String = "",
    val authUser: String = "",
    val sipPassword: String = "",
    val sipRegistrar: String = "voip.easybell.de",
    val protocol: SipTransportProtocol = SipTransportProtocol.TLS,
    val port: Int = 5061
)

class NativeSipClient(private val context: Context) {

    private val tag = "NativeSipClient"

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

    private var currentConfig: SipAccountConfig? = null
    private var clientJob: Job? = null
    private var durationJob: Job? = null
    private var sipKeepAliveJob: Job? = null
    private var sessionRefreshJob: Job? = null
    private var sessionExpiresSeconds = DEFAULT_SESSION_EXPIRES_SECONDS
    private var sessionRefresher = "uac"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Network connection objects
    private var udpSocket: DatagramSocket? = null
    private var tcpSocket: Socket? = null
    private var sslSocket: SSLSocket? = null
    private var socketWriter: OutputStream? = null
    private var socketReader: BufferedReader? = null

    // SIP Session variables
    private var localIp = "127.0.0.1"
    private var localPort = 5060
    private var callId = UUID.randomUUID().toString()
    private var fromTag = generateRandomHex(8)
    private var toTag: String? = null
    private var cseq = 1
    private var activeCallTarget: String? = null
    private var inviteRequestUri: String? = null
    private var inviteViaBranch: String? = null
    private var remoteContactUri: String? = null
    private var remoteToHeader: String? = null
    private var routeSet: List<String> = emptyList()
    @Volatile private var lastUdpSender: InetSocketAddress? = null
    private var sipKeepAliveCallId = UUID.randomUUID().toString()
    private var sipKeepAliveCseq = 1

    // RTP media state. SIP only establishes the call; these sockets carry the audio.
    private enum class G711Codec { PCMA, PCMU }
    private data class RemoteRtpDescription(
        val address: InetAddress,
        val port: Int,
        val payloadType: Int,
        val codecs: Map<Int, G711Codec>
    )

    private var rtpSocket: DatagramSocket? = null
    private var rtpReceiveJob: Job? = null
    private var audioCaptureJob: Job? = null
    private var rtpKeepAliveJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    @Volatile private var remoteRtpAddress: InetSocketAddress? = null
    @Volatile private var negotiatedPayloadType = 8
    @Volatile private var negotiatedCodecs: Map<Int, G711Codec> =
        mapOf(8 to G711Codec.PCMA, 0 to G711Codec.PCMU)
    private var rtpSequence = SecureRandom().nextInt(65536)
    private var rtpTimestamp = SecureRandom().nextInt().toLong() and 0xffffffffL
    private val rtpSsrc = SecureRandom().nextInt()

    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    @Volatile private var muted = false
    @Volatile private var speakerphoneOn = true
    @Volatile private var lastRtpSentAt = 0L
    private var callWakeLock: PowerManager.WakeLock? = null
    private val sipSendLock = Any()
    private val rtpSendLock = Any()

    private companion object {
        const val RTP_SAMPLE_RATE = 8000
        const val RTP_FRAME_SAMPLES = 160 // 20 ms at 8 kHz
        const val RTP_PORT_MIN = 20000
        const val RTP_PORT_MAX = 50000
        // Keep the SIP signaling flow active below Easybell/mobile NAT idle limits.
        const val SIP_KEEPALIVE_INTERVAL_MS = 10_000L
        // If AudioRecord stalls, keep sending real RTP silence instead of going silent.
        const val RTP_KEEPALIVE_INTERVAL_MS = 1_000L
        const val DEFAULT_SESSION_EXPIRES_SECONDS = 1800
        const val MIN_SESSION_EXPIRES_SECONDS = 20
    }

    // Digest Authentication
    private var lastRealm = ""
    private var lastNonce = ""
    private var lastQop: String? = null
    private var lastOpaque: String? = null

    // Audio & Recording. The call audio is recorded locally as WAV only.
    private var callWavRecorder: PcmCallRecorder? = null
    private var currentRecordFile: File? = null

    init {
        findLocalIp()
    }

    private fun findLocalIp() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        localIp = addr.hostAddress ?: "127.0.0.1"
                        return
                    }
                }
            }
        } catch (e: Exception) {
            localIp = "127.0.0.1"
        }
    }

    fun register(config: SipAccountConfig) {
        disconnect()
        currentConfig = config
        sipKeepAliveCallId = UUID.randomUUID().toString()
        sipKeepAliveCseq = 1
        _state.value = SipState.CONNECTING
        _statusText.value = "Verbinde über ${config.protocol.name} zu ${config.sipRegistrar}:${config.port}..."

        clientJob = scope.launch {
            try {
                findLocalIp()
                connectSocket(config)
                sendRegister(config, null)
                startSipKeepAlive(config)
                listenForMessages(config)
            } catch (e: Exception) {
                Log.e(tag, "Connection error", e)
                _state.value = SipState.ERROR
                _statusText.value = "Fehler: ${e.localizedMessage ?: "Verbindung fehlgeschlagen"}"
            }
        }
    }

    private fun connectSocket(config: SipAccountConfig) {
        when (config.protocol) {
            SipTransportProtocol.UDP -> {
                udpSocket = DatagramSocket().apply {
                    soTimeout = 15000
                }
                localPort = udpSocket?.localPort ?: 5060
            }
            SipTransportProtocol.TCP -> {
                val socket = Socket()
                socket.connect(InetSocketAddress(config.sipRegistrar, config.port), 10000)
                // Keep the TCP/TLS signaling transport alive for the entire call.
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = 0
                tcpSocket = socket
                socketWriter = socket.getOutputStream()
                socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
                localPort = socket.localPort
            }
            SipTransportProtocol.TLS -> {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, null, SecureRandom())
                val factory = sslContext.socketFactory
                val socket = factory.createSocket(config.sipRegistrar, config.port) as SSLSocket
                // Keep the TCP/TLS signaling transport alive for the entire call.
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = 0
                socket.startHandshake()
                sslSocket = socket
                socketWriter = socket.getOutputStream()
                socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
                localPort = socket.localPort
            }
        }
    }

    private fun sendSipMessage(message: String, config: SipAccountConfig) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        synchronized(sipSendLock) {
            when (config.protocol) {
                SipTransportProtocol.UDP -> {
                val destination = lastUdpSender
                    ?: InetSocketAddress(config.sipRegistrar, config.port)
                val packet = DatagramPacket(
                    bytes,
                    bytes.size,
                    destination.address,
                    destination.port
                )
                udpSocket?.send(packet)
            }
            SipTransportProtocol.TCP, SipTransportProtocol.TLS -> {
                socketWriter?.apply {
                    write(bytes)
                    flush()
                }
                }
            }
        }
        Log.d(tag, "Sent SIP Message:\n$message")
    }

    private fun startSipKeepAlive(config: SipAccountConfig) {
        stopSipKeepAlive()
        sipKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (
                isActive &&
                _state.value != SipState.DISCONNECTED &&
                _state.value != SipState.ERROR
            ) {
                delay(SIP_KEEPALIVE_INTERVAL_MS)
                if (!isActive ||
                    _state.value == SipState.DISCONNECTED ||
                    _state.value == SipState.ERROR
                ) {
                    break
                }
                // Do not compete with the initial REGISTER challenge/response.
                if (_state.value == SipState.CONNECTING) continue

                runCatching {
                    // OPTIONS is a valid SIP transaction and keeps the long-lived
                    // UDP/TCP/TLS signaling path open without changing the call dialog.
                    sendSipOptionsKeepAlive(config)
                }.onFailure { error ->
                    Log.w(tag, "SIP OPTIONS keepalive failed", error)
                }
            }
        }
    }

    private fun sendSipOptionsKeepAlive(config: SipAccountConfig) {
        val transport = config.protocol.name
        val user = config.sipUser
        val domain = config.sipRegistrar
        val optionsCseq = sipKeepAliveCseq++
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"

        val sb = StringBuilder()
        sb.append("OPTIONS sip:$domain SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: <sip:$domain>\r\n")
        sb.append("Call-ID: $sipKeepAliveCallId@$localIp\r\n")
        sb.append("CSeq: $optionsCseq OPTIONS\r\n")
        sb.append("User-Agent: SmartCalls/1.2.0 Android\r\n")
        sb.append("Accept: application/sdp\r\n")
        sb.append("Content-Length: 0\r\n\r\n")
        sendSipMessage(sb.toString(), config)
    }

    private fun stopSipKeepAlive() {
        sipKeepAliveJob?.cancel()
        sipKeepAliveJob = null
    }
    private fun listenForMessages(config: SipAccountConfig) {
        val buffer = ByteArray(8192)
        while (scope.isActive && _state.value != SipState.DISCONNECTED) {
            try {
                val rawMessage = when (config.protocol) {
                    SipTransportProtocol.UDP -> {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)
                        lastUdpSender = InetSocketAddress(packet.address, packet.port)
                        String(packet.data, 0, packet.length, Charsets.UTF_8)
                    }
                    SipTransportProtocol.TCP, SipTransportProtocol.TLS -> {
                        readSipMessageFromStream()
                    }
                }

                if (rawMessage.isNullOrBlank()) continue
                Log.d(tag, "Received SIP Message:\n$rawMessage")
                handleSipMessage(rawMessage, config)
            } catch (e: SocketTimeoutException) {
                // Keepalive check / retry
            } catch (e: Exception) {
                if (scope.isActive && _state.value != SipState.DISCONNECTED) {
                    val stateAtFailure = _state.value
                    Log.e(tag, "Error reading SIP stream", e)
                    stopSipKeepAlive()
                    if (stateAtFailure == SipState.IN_CALL) {
                        // A signaling socket error must not tear down an otherwise
                        // active RTP stream. RTP continues while the peer/SBC
                        // recovers its signaling path.
                        _statusText.value =
                            "Im Gespräch (SIP-Signalisierung kurz unterbrochen)"
                    } else {
                        stopInCallTimer()
                        stopRtpAudio()
                        stopCallRecording()
                        _state.value = SipState.ERROR
                        _statusText.value = "Verbindung unterbrochen: \${e.message}"
                    }
                }
                break
            }
        }
    }

    private fun readSipMessageFromStream(): String? {
        val reader = socketReader ?: return null
        val headerBuilder = StringBuilder()
        var line: String?

        // Read SIP headers
        while (reader.readLine().also { line = it } != null) {
            if (line.isNullOrEmpty()) {
                break
            }
            headerBuilder.append(line).append("\r\n")
        }

        if (headerBuilder.isEmpty()) {
            throw EOFException("SIP-Verbindung wurde geschlossen")
        }

        val headers = headerBuilder.toString()
        var contentLength = 0
        headers.lines().forEach { l ->
            if (l.startsWith("Content-Length:", ignoreCase = true) || l.startsWith("l:", ignoreCase = true)) {
                contentLength = l.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }

        val bodyBuilder = StringBuilder()
        if (contentLength > 0) {
            val bodyChars = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val r = reader.read(bodyChars, read, contentLength - read)
                if (r == -1) break
                read += r
            }
            bodyBuilder.append(bodyChars, 0, read)
        }

        return headers + "\r\n" + bodyBuilder.toString()
    }

    private fun handleSipMessage(message: String, config: SipAccountConfig) {
        val firstLine = message.lines().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return

        if (firstLine.startsWith("SIP/2.0", ignoreCase = true)) {
            val statusCode = firstLine.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: return
            val method = extractCSeqMethod(message)

            // OPTIONS keepalive responses must never terminate an active call,
            // even when the provider answers with 4xx/5xx.
            if (method == "OPTIONS") {
                Log.d(tag, "SIP OPTIONS keepalive response: $statusCode")
                return
            }

            when (statusCode) {
                100 -> {
                    if (method == "INVITE" && _state.value == SipState.DIALING) {
                        _statusText.value = "Wählt..."
                    }
                }

                180, 183 -> {
                    if (method == "INVITE" &&
                        (_state.value == SipState.DIALING || _state.value == SipState.RINGING)
                    ) {
                        _state.value = SipState.RINGING
                        _statusText.value = "Klingelt..."
                    }
                }

                200 -> {
                    when {
                        method == "REGISTER" && _state.value == SipState.CONNECTING -> {
                            _state.value = SipState.REGISTERED
                            _statusText.value =
                                "Registriert (${config.sipRegistrar}:${config.port} [${config.protocol.name}])"
                        }

                        method == "INVITE" &&
                            (_state.value == SipState.DIALING || _state.value == SipState.RINGING) -> {
                            extractDialogInfo(message)
                            val remoteMedia = parseRemoteSdp(message)

                            _state.value = SipState.IN_CALL
                            _statusText.value = if (remoteMedia == null) {
                                "Im Gespräch (kein RTP-Audio ausgehandelt)"
                            } else {
                                "Im Gespräch"
                            }

                            // The 200 response completes the SIP dialog only after ACK.
                            val responseCseq = extractCSeqNumber(message) ?: cseq
                            sendAck(config, responseCseq)
                            if (remoteMedia != null) {
                                startRtpAudio(remoteMedia)
                            } else {
                                Log.e(tag, "200 OK contained no supported RTP audio SDP")
                            }

                            startInCallTimer()
                            startCallRecording()
                        }

                        // A provider may refresh the established dialog with a re-INVITE.
                        // Answer it and ACK our own refresh responses so the call stays up.
                        method == "INVITE" && _state.value == SipState.IN_CALL -> {
                            extractDialogInfo(message)
                            val responseCseq = extractCSeqNumber(message) ?: cseq
                            sendAck(config, responseCseq)
                        }
                    }
                }

                401, 407 -> {
                    val authHeader = message.lines().firstOrNull {
                        it.startsWith("WWW-Authenticate:", ignoreCase = true) ||
                            it.startsWith("Proxy-Authenticate:", ignoreCase = true)
                    }
                    if (authHeader != null) {
                        parseAuthHeader(authHeader)
                        cseq++
                        when {
                            method == "REGISTER" && _state.value == SipState.CONNECTING -> {
                                val auth = buildDigestAuth(
                                    "REGISTER",
                                    "sip:${config.sipRegistrar}",
                                    config
                                )
                                sendRegister(config, auth)
                            }

                            method == "INVITE" &&
                                (_state.value == SipState.DIALING ||
                                    _state.value == SipState.RINGING) &&
                                activeCallTarget != null -> {
                                val auth = buildDigestAuth(
                                    "INVITE",
                                    inviteRequestUri
                                        ?: "sip:${activeCallTarget}@${config.sipRegistrar}",
                                    config
                                )
                                val headerName = if (statusCode == 407) {
                                    "Proxy-Authorization"
                                } else {
                                    "Authorization"
                                }
                                sendInvite(activeCallTarget!!, config, auth, headerName)
                            }

                            method == "INVITE" && _state.value == SipState.IN_CALL -> {
                                val requestUri = remoteContactUri
                                    ?: inviteRequestUri
                                    ?: "sip:${activeCallTarget ?: ""}@${config.sipRegistrar}"
                                val auth = buildDigestAuth("INVITE", requestUri, config)
                                val headerName = if (statusCode == 407) {
                                    "Proxy-Authorization"
                                } else {
                                    "Authorization"
                                }
                                sendSessionRefresh(config, auth, headerName)
                            }
                        }
                    } else {
                        finishCallWithState(
                            SipState.ERROR,
                            "Fehler: ${statusCode} Unauthorized (Kein Auth-Header)"
                        )
                    }
                }

                403 -> finishCallWithState(
                    SipState.ERROR,
                    "Fehler 403: Zugriff verweigert (Passwort/Benutzer prüfen)"
                )

                404 -> finishCallWithState(
                    SipState.ERROR,
                    "Fehler 404: Rufnummer nicht gefunden"
                )

                486 -> finishCallWithState(
                    SipState.ERROR,
                    "Besetzt (486 Busy Here)"
                )

                487 -> finishCallWithState(
                    SipState.REGISTERED,
                    "Anruf abgebrochen"
                )

                else -> {
                    if (statusCode >= 400) {
                        finishCallWithState(
                            SipState.ERROR,
                            "SIP Fehler ${statusCode}: ${firstLine.substringAfter(statusCode.toString()).trim()}"
                        )
                    }
                }
            }
            return
        }

        // Requests arrive on the same SIP connection. Ignoring BYE here used to
        // leave the UI and media session in an invalid state.
        when (firstLine.substringBefore(" ").uppercase(Locale.ROOT)) {
            "INVITE" -> {
                if (_state.value == SipState.IN_CALL) {
                    parseRemoteSdp(message)?.let { remote ->
                        remoteRtpAddress = InetSocketAddress(remote.address, remote.port)
                        negotiatedPayloadType = remote.payloadType
                        negotiatedCodecs = remote.codecs
                    }
                    sendInviteResponseForRequest(message, config)
                } else {
                    sendSipResponseForRequest(message, 491, "Request Pending", config)
                }
            }

            "UPDATE" -> {
                if (_state.value == SipState.IN_CALL) {
                    sendSipResponseForRequest(message, 200, "OK", config)
                } else {
                    sendSipResponseForRequest(message, 481, "Call/Transaction Does Not Exist", config)
                }
            }

            "BYE" -> {
                sendSipResponseForRequest(message, 200, "OK", config)
                finishCallWithState(
                    SipState.REGISTERED,
                    "Gespräch vom Gesprächspartner beendet"
                )
            }

            "CANCEL" -> {
                sendSipResponseForRequest(message, 200, "OK", config)
                finishCallWithState(SipState.REGISTERED, "Anruf abgebrochen")
            }

            "OPTIONS" -> sendSipResponseForRequest(message, 200, "OK", config)
            "ACK" -> Unit
        }
    }

    private fun sendRegister(config: SipAccountConfig, authHeader: String?) {
        val transport = config.protocol.name
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
        val user = config.sipUser
        val domain = config.sipRegistrar

        val sb = StringBuilder()
        sb.append("REGISTER sip:$domain SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: <sip:$user@$domain>\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $cseq REGISTER\r\n")
        sb.append("Contact: <sip:$user@$localIp:$localPort;transport=${transport.lowercase(Locale.ROOT)}>\r\n")
        sb.append("Expires: 3600\r\n")
        sb.append("User-Agent: SmartCalls/1.0.0 Android\r\n")
        if (authHeader != null) {
            sb.append("Authorization: $authHeader\r\n")
        }
        sb.append("Content-Length: 0\r\n\r\n")

        sendSipMessage(sb.toString(), config)
    }

    fun setMuted(value: Boolean) {
        muted = value
    }

    fun setSpeakerphoneOn(enabled: Boolean) {
        speakerphoneOn = enabled
        audioManager?.let { manager ->
            runCatching { manager.isSpeakerphoneOn = enabled }
        }
    }

    fun makeCall(targetNumber: String) {
        val config = currentConfig
        if (config == null || _state.value != SipState.REGISTERED) return

        val cleanNumber = targetNumber.replace(Regex("[^0-9+]"), "")
        activeCallTarget = cleanNumber
        _state.value = SipState.DIALING
        _statusText.value = "Wählt $cleanNumber..."
        cseq++
        callId = UUID.randomUUID().toString()
        fromTag = generateRandomHex(8)
        toTag = null
        inviteRequestUri = null
        inviteViaBranch = null
        remoteContactUri = null
        remoteToHeader = null
        routeSet = emptyList()
        stopSessionRefresh()
        sessionExpiresSeconds = DEFAULT_SESSION_EXPIRES_SECONDS
        sessionRefresher = "uac"

        scope.launch {
            try {
                prepareRtpSocket()
                sendInvite(cleanNumber, config, null)
            } catch (e: Exception) {
                Log.e(tag, "Error sending invite", e)
                stopRtpAudio()
                _state.value = SipState.ERROR
                _statusText.value = "Netzwerkfehler beim Anruf: ${e.message}"
            }
        }
    }
    private fun buildLocalSdp(): String {
        val rtpPort = rtpSocket?.localPort
            ?: throw IllegalStateException("RTP-Socket wurde vor dem SDP nicht geöffnet")
        return StringBuilder()
            .append("v=0\r\n")
            .append("o=SmartCalls 1000 1000 IN IP4 $localIp\r\n")
            .append("s=SmartCall\r\n")
            .append("c=IN IP4 $localIp\r\n")
            .append("t=0 0\r\n")
            .append("m=audio $rtpPort RTP/AVP 8 0 101\r\n")
            .append("a=rtpmap:8 PCMA/8000\r\n")
            .append("a=rtpmap:0 PCMU/8000\r\n")
            .append("a=rtpmap:101 telephone-event/8000\r\n")
            .append("a=ptime:20\r\n")
            .append("a=sendrecv\r\n")
            .toString()
    }

    private fun sendSessionRefresh(
        config: SipAccountConfig,
        authHeader: String? = null,
        authHeaderName: String = "Authorization"
    ) {
        if (_state.value != SipState.IN_CALL) return
        val target = activeCallTarget ?: return
        val requestUri = remoteContactUri
            ?: inviteRequestUri
            ?: "sip:$target@${config.sipRegistrar}"
        if (rtpSocket == null) return
        val refreshCseq = ++cseq
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
        val transport = config.protocol.name
        val user = config.sipUser
        val domain = config.sipRegistrar
        val sdp = buildLocalSdp()
        val sdpBytes = sdp.toByteArray(Charsets.UTF_8)

        val sb = StringBuilder()
        sb.append("INVITE $requestUri SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: ${remoteToHeader ?: "<sip:$target@$domain>"}\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $refreshCseq INVITE\r\n")
        sb.append("Contact: <sip:$user@$localIp:$localPort;transport=${transport.lowercase(Locale.ROOT)}>\r\n")
        routeSet.forEach { route -> sb.append("Route: $route\r\n") }


        sb.append("Content-Type: application/sdp\r\n")
        sb.append("User-Agent: SmartCalls/1.1.0 Android\r\n")
        if (authHeader != null) {
            sb.append("$authHeaderName: $authHeader\r\n")
        }
        sb.append("Content-Length: ${sdpBytes.size}\r\n\r\n")
        sb.append(sdp)
        sendSipMessage(sb.toString(), config)
    }

    private fun sendInvite(
        targetNumber: String,
        config: SipAccountConfig,
        authHeader: String?,
        authHeaderName: String = "Proxy-Authorization"
    ) {
        val transport = config.protocol.name
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
        val user = config.sipUser
        val domain = config.sipRegistrar
        val requestUri = "sip:$targetNumber@$domain"
        inviteRequestUri = requestUri
        inviteViaBranch = viaBranch

        val sdp = buildLocalSdp()

        val sdpBytes = sdp.toByteArray(Charsets.UTF_8)

        val sb = StringBuilder()
        sb.append("INVITE $requestUri SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: <sip:$targetNumber@$domain>\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $cseq INVITE\r\n")
        sb.append("Contact: <sip:$user@$localIp:$localPort;transport=${transport.lowercase(Locale.ROOT)}>\r\n")
        sb.append("Content-Type: application/sdp\r\n")
        sb.append("User-Agent: SmartCalls/1.1.0 Android\r\n")


        if (authHeader != null) {
            sb.append("$authHeaderName: $authHeader\r\n")
        }
        sb.append("Content-Length: ${sdpBytes.size}\r\n\r\n")
        sb.append(sdp)

        sendSipMessage(sb.toString(), config)
    }
    private fun sendAck(config: SipAccountConfig, inviteCseq: Int = cseq) {
        val target = activeCallTarget ?: return
        val transport = config.protocol.name
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
        val user = config.sipUser
        val domain = config.sipRegistrar
        val requestUri = remoteContactUri ?: inviteRequestUri ?: "sip:$target@$domain"
        val fallbackToHeader = if (toTag != null) {
            "<sip:$target@$domain>;tag=$toTag"
        } else {
            "<sip:$target@$domain>"
        }
        val toHeader = remoteToHeader ?: fallbackToHeader

        val sb = StringBuilder()
        sb.append("ACK $requestUri SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: $toHeader\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $inviteCseq ACK\r\n")
        routeSet.forEach { route -> sb.append("Route: $route\r\n") }
        sb.append("User-Agent: SmartCalls/1.1.0 Android\r\n")
        sb.append("Content-Length: 0\r\n\r\n")

        sendSipMessage(sb.toString(), config)
    }
    fun hangUp() {
        val config = currentConfig ?: return
        val stateBefore = _state.value
        val target = activeCallTarget
        val byeUri = remoteContactUri
            ?: inviteRequestUri
            ?: target?.let { "sip:$it@${config.sipRegistrar}" }
        val byeToHeader = remoteToHeader
            ?: target?.let {
                if (toTag != null) "<sip:$it@${config.sipRegistrar}>;tag=$toTag"
                else "<sip:$it@${config.sipRegistrar}>"
            }
        val cancelUri = inviteRequestUri
            ?: target?.let { "sip:$it@${config.sipRegistrar}" }
        val cancelBranch = inviteViaBranch
        val routes = routeSet.toList()

        stopInCallTimer()
        stopSessionRefresh()
        stopRtpAudio()
        stopCallRecording()

        scope.launch {
            try {
                if (stateBefore == SipState.IN_CALL && target != null && byeUri != null) {
                    cseq++
                    sendBye(config, byeUri, byeToHeader ?: "<sip:$target@${config.sipRegistrar}>", routes)
                } else if (
                    (stateBefore == SipState.DIALING || stateBefore == SipState.RINGING) &&
                    target != null &&
                    cancelUri != null
                ) {
                    sendCancel(config, cancelUri, target, cancelBranch)
                }
            } catch (e: Exception) {
                Log.w(tag, "Could not send hangup request", e)
            } finally {
                clearCallDialog()
                _state.value = SipState.REGISTERED
                _statusText.value =
                    "Registriert (${config.sipRegistrar}:${config.port} [${config.protocol.name}])"
            }
        }
    }

    private fun sendBye(
        config: SipAccountConfig,
        requestUri: String,
        toHeader: String,
        routes: List<String>
    ) {
        val transport = config.protocol.name
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
        val user = config.sipUser
        val domain = config.sipRegistrar
        val sb = StringBuilder()
        sb.append("BYE $requestUri SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: $toHeader\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $cseq BYE\r\n")
        routes.forEach { route -> sb.append("Route: $route\r\n") }
        sb.append("User-Agent: SmartCalls/1.1.0 Android\r\n")
        sb.append("Content-Length: 0\r\n\r\n")
        sendSipMessage(sb.toString(), config)
    }

    private fun sendCancel(
        config: SipAccountConfig,
        requestUri: String,
        target: String,
        branch: String?
    ) {
        val transport = config.protocol.name
        val user = config.sipUser
        val domain = config.sipRegistrar
        val viaBranch = branch ?: "z9hG4bK-${generateRandomHex(10)}"
        val sb = StringBuilder()
        sb.append("CANCEL $requestUri SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: <sip:$target@$domain>\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $cseq CANCEL\r\n")
        sb.append("User-Agent: SmartCalls/1.1.0 Android\r\n")
        sb.append("Content-Length: 0\r\n\r\n")
        sendSipMessage(sb.toString(), config)
    }

    private fun finishCallWithState(nextState: SipState, message: String) {
        stopInCallTimer()
        stopRtpAudio()
        stopCallRecording()
        clearCallDialog()
        _state.value = nextState
        _statusText.value = message
    }

    private fun clearCallDialog() {
        stopSessionRefresh()
        activeCallTarget = null
        inviteRequestUri = null
        inviteViaBranch = null
        remoteContactUri = null
        remoteToHeader = null
        routeSet = emptyList()
        toTag = null
    }
    private fun extractDialogInfo(message: String) {
        val toValue = extractHeaderValue(message, "To")
        remoteToHeader = toValue
        toTag = toValue?.let {
            Regex("(?i)(?:^|;)\\s*tag=([^;\\s]+)")
                .find(it)?.groupValues?.getOrNull(1)
        }
        remoteContactUri = parseSipUri(extractHeaderValue(message, "Contact"))
        // RFC 3261: the UAC uses the Record-Route list in reverse order.
        routeSet = headerValues(message, "Record-Route").asReversed()
    }

    private fun extractToTag(message: String) {
        extractDialogInfo(message)
    }

    private fun extractCSeqMethod(message: String): String? {
        val value = extractHeaderValue(message, "CSeq") ?: return null
        return value.trim().split(Regex("\\s+")).getOrNull(1)?.uppercase(Locale.ROOT)
    }

    private fun extractCSeqNumber(message: String): Int? {
        val value = extractHeaderValue(message, "CSeq") ?: return null
        return value.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
    }

    private fun headerValues(message: String, name: String): List<String> =
        message.lineSequence()
            .filter { line ->
                line.substringBefore(":").trim().equals(name, ignoreCase = true)
            }
            .map { it.substringAfter(":").trim() }
            .filter { it.isNotBlank() }
            .toList()

    private fun extractHeaderValue(message: String, name: String): String? =
        headerValues(message, name).firstOrNull()

    private fun parseSipUri(value: String?): String? {
        var candidate = value?.trim().orEmpty()
        if (candidate.isBlank()) return null
        if (candidate.contains("<") && candidate.contains(">")) {
            candidate = candidate.substringAfter("<").substringBefore(">")
        } else {
            candidate = candidate.substringBefore(";").trim()
        }
        return candidate.takeIf {
            it.startsWith("sip:", ignoreCase = true) ||
                it.startsWith("sips:", ignoreCase = true)
        }
    }

    private fun extractMessageBody(message: String): String {
        val crlf = message.indexOf("\r\n\r\n")
        if (crlf >= 0) return message.substring(crlf + 4)
        val lf = message.indexOf("\n\n")
        return if (lf >= 0) message.substring(lf + 2) else ""
    }

    private fun parseRemoteSdp(message: String): RemoteRtpDescription? {
        val body = extractMessageBody(message)
        if (body.isBlank()) return null

        var sessionAddress: String? = null
        var audioAddress: String? = null
        var audioPort: Int? = null
        var audioPayloadTypes: List<Int> = emptyList()
        var inAudio = false
        val codecs = mutableMapOf<Int, G711Codec>()

        body.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("m=", ignoreCase = true) -> {
                    val parts = line.substringAfter("m=").split(Regex("\\s+"))
                    inAudio = parts.firstOrNull()?.equals("audio", ignoreCase = true) == true
                    if (inAudio && parts.size >= 4) {
                        audioPort = parts[1].toIntOrNull()
                        audioPayloadTypes = parts.drop(3).mapNotNull { it.toIntOrNull() }
                    }
                }

                line.startsWith("c=", ignoreCase = true) -> {
                    val address = line.substringAfter("c=").trim()
                        .split(Regex("\\s+")).lastOrNull()
                    if (inAudio) audioAddress = address
                    else if (sessionAddress == null) sessionAddress = address
                }

                line.startsWith("a=rtpmap:", ignoreCase = true) && inAudio -> {
                    val parts = line.substringAfter(":").trim()
                        .split(Regex("\\s+"), limit = 2)
                    val payloadType = parts.firstOrNull()?.toIntOrNull()
                    val codecName = parts.getOrNull(1)
                        ?.substringBefore("/")
                        ?.uppercase(Locale.ROOT)
                    if (payloadType != null) {
                        when (codecName) {
                            "PCMA" -> codecs[payloadType] = G711Codec.PCMA
                            "PCMU" -> codecs[payloadType] = G711Codec.PCMU
                        }
                    }
                }
            }
        }

        // PCMA/PCMU have static RTP payload types, even if the answer omits rtpmap.
        if (!codecs.containsKey(8)) codecs[8] = G711Codec.PCMA
        if (!codecs.containsKey(0)) codecs[0] = G711Codec.PCMU

        val selectedPayloadType = audioPayloadTypes.firstOrNull { codecs.containsKey(it) }
            ?: return null
        val addressText = audioAddress ?: sessionAddress ?: return null
        val port = audioPort ?: return null
        if (port <= 0 || port > 65535 || addressText == "0.0.0.0") return null
        val address = runCatching { InetAddress.getByName(addressText) }.getOrNull() ?: return null

        return RemoteRtpDescription(
            address = address,
            port = port,
            payloadType = selectedPayloadType,
            codecs = codecs
        )
    }

    private fun sendInviteResponseForRequest(request: String, config: SipAccountConfig) {
        val sdp = runCatching { buildLocalSdp() }.getOrElse {
            sendSipResponseForRequest(request, 488, "Not Acceptable Here", config)
            return
        }
        val transport = config.protocol.name
        val sb = StringBuilder("SIP/2.0 200 OK\r\n")
        listOf("Via", "From", "To", "Call-ID", "CSeq").forEach { name ->
            headerValues(request, name).forEach { value ->
                sb.append("$name: $value\r\n")
            }
        }
        sb.append("Contact: <sip:${config.sipUser}@$localIp:$localPort;transport=${transport.lowercase(Locale.ROOT)}>\r\n")
        sb.append("Allow: INVITE, ACK, BYE, CANCEL, OPTIONS, UPDATE\r\n")


        sb.append("Content-Type: application/sdp\r\n")
        sb.append("User-Agent: SmartCalls/1.1.0 Android\r\n")
        val sdpBytes = sdp.toByteArray(Charsets.UTF_8)
        sb.append("Content-Length: ${sdpBytes.size}\r\n\r\n")
        sb.append(sdp)
        sendSipMessage(sb.toString(), config)
    }

    private fun sendSipResponseForRequest(
        request: String,
        statusCode: Int,
        reason: String,
        config: SipAccountConfig
    ) {
        val sb = StringBuilder("SIP/2.0 $statusCode $reason\r\n")
        listOf("Via", "From", "To", "Call-ID", "CSeq").forEach { name ->
            headerValues(request, name).forEach { value ->
                sb.append("$name: $value\r\n")
            }
        }
        sb.append("Content-Length: 0\r\n\r\n")
        sendSipMessage(sb.toString(), config)
    }

    private fun parseSessionExpiresSeconds(message: String): Int? {
        val value = extractHeaderValue(message, "Session-Expires") ?: return null
        return Regex("^\\s*(\\d+)")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.coerceAtLeast(MIN_SESSION_EXPIRES_SECONDS)
    }

    private fun updateSessionTimer(message: String) {
        parseSessionExpiresSeconds(message)?.let { seconds ->
            sessionExpiresSeconds = seconds
        }
        val value = extractHeaderValue(message, "Session-Expires") ?: return
        Regex("(?i)(?:^|;)\\s*refresher\\s*=\\s*(uac|uas)")
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { sessionRefresher = it.lowercase(Locale.ROOT) }
        Log.d(tag, "SIP session timer: ${sessionExpiresSeconds}s, refresher=$sessionRefresher")
    }

    private fun startSessionRefresh(config: SipAccountConfig) {
        stopSessionRefresh()
        if (!sessionRefresher.equals("uac", ignoreCase = true)) return
        sessionRefreshJob = scope.launch(Dispatchers.IO) {
            while (isActive && _state.value == SipState.IN_CALL) {
                val waitMs = (sessionExpiresSeconds * 1000L / 2).coerceAtLeast(10_000L)
                delay(waitMs)
                if (isActive && _state.value == SipState.IN_CALL) {
                    runCatching { sendSessionRefresh(config) }
                        .onFailure { Log.w(tag, "SIP session refresh failed", it) }
                }
            }
        }
    }

    private fun stopSessionRefresh() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = null
    }

    private fun parseAuthHeader(authHeader: String) {
        val parts = authHeader.substringAfter("Digest ").split(",")
        for (part in parts) {
            val key = part.substringBefore("=").trim()
            val value = part.substringAfter("=").trim().replace("\"", "")
            when (key.lowercase(Locale.ROOT)) {
                "realm" -> lastRealm = value
                "nonce" -> lastNonce = value
                "qop" -> lastQop = value
                "opaque" -> lastOpaque = value
            }
        }
    }

    private fun buildDigestAuth(method: String, uri: String, config: SipAccountConfig): String {
        val username = if (config.authUser.isNotBlank()) config.authUser else config.sipUser
        val password = config.sipPassword
        val realm = lastRealm
        val nonce = lastNonce
        val cnonce = generateRandomHex(8)
        val nc = "00000001"

        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("$method:$uri")

        val response = if (lastQop != null && (lastQop!!.contains("auth") || lastQop!!.contains("auth-int"))) {
            md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        } else {
            md5("$ha1:$nonce:$ha2")
        }

        val sb = StringBuilder()
        sb.append("Digest username=\"$username\", ")
        sb.append("realm=\"$realm\", ")
        sb.append("nonce=\"$nonce\", ")
        sb.append("uri=\"$uri\", ")
        sb.append("response=\"$response\", ")
        sb.append("algorithm=MD5")

        if (lastOpaque != null) {
            sb.append(", opaque=\"$lastOpaque\"")
        }
        if (lastQop != null) {
            sb.append(", qop=auth, nc=$nc, cnonce=\"$cnonce\"")
        }

        return sb.toString()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun generateRandomHex(length: Int): String {
        val chars = "0123456789abcdef"
        val random = SecureRandom()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    private fun startInCallTimer() {
        durationJob?.cancel()
        _callDurationSeconds.value = 0
        durationJob = scope.launch {
            while (isActive && _state.value == SipState.IN_CALL) {
                delay(1000)
                _callDurationSeconds.value += 1
            }
        }
    }

    private fun stopInCallTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    private fun prepareRtpSocket() {
        stopRtpAudio()
        val random = SecureRandom()
        var socket: DatagramSocket? = null

        repeat(20) {
            if (socket != null) return@repeat
            val candidate = runCatching { DatagramSocket(null) }.getOrNull()
                ?: return@repeat
            try {
                candidate.reuseAddress = true
                val port = RTP_PORT_MIN + random.nextInt(RTP_PORT_MAX - RTP_PORT_MIN + 1)
                candidate.bind(InetSocketAddress(port))
                socket = candidate
            } catch (_: BindException) {
                candidate.close()
            } catch (e: Exception) {
                candidate.close()
                throw e
            }
        }

        if (socket == null) {
            socket = DatagramSocket()
            Log.w(tag, "Could not bind in Easybell RTP range; using ${socket!!.localPort}")
        }
        socket!!.soTimeout = 1000
        rtpSocket = socket
        remoteRtpAddress = null
        negotiatedPayloadType = 8
        negotiatedCodecs = mapOf(8 to G711Codec.PCMA, 0 to G711Codec.PCMU)
        rtpSequence = SecureRandom().nextInt(65536)
        rtpTimestamp = SecureRandom().nextInt().toLong() and 0xffffffffL
        lastRtpSentAt = 0L
    }

    private fun startRtpAudio(remote: RemoteRtpDescription) {
        val socket = rtpSocket
        if (socket == null) {
            Log.e(tag, "Cannot start RTP audio without a local RTP socket")
            _statusText.value = "Im Gespräch (RTP-Socket fehlt)"
            return
        }

        remoteRtpAddress = InetSocketAddress(remote.address, remote.port)
        negotiatedPayloadType = remote.payloadType
        negotiatedCodecs = remote.codecs

        try {
            acquireCallWakeLock()
            prepareAudioDevices()
            audioTrack?.play()
            // Open the NAT/media path immediately, even if AudioRecord needs a moment
            // to become ready. This avoids provider-side no-RTP timeouts after answer.
            runCatching { sendRtpKeepAlive(socket) }
                .onFailure { Log.w(tag, "Initial RTP keepalive failed", it) }

            rtpReceiveJob = scope.launch(Dispatchers.IO) {
                receiveRtpAudio(socket)
            }
            audioCaptureJob = scope.launch(Dispatchers.IO) {
                captureAndSendRtp(socket)
            }
            rtpKeepAliveJob = scope.launch(Dispatchers.IO) {
                while (isActive && _state.value == SipState.IN_CALL && !socket.isClosed) {
                    delay(RTP_KEEPALIVE_INTERVAL_MS)
                    if (System.currentTimeMillis() - lastRtpSentAt >= RTP_KEEPALIVE_INTERVAL_MS) {
                        runCatching { sendRtpKeepAlive(socket) }
                            .onFailure { Log.w(tag, "RTP keepalive failed", it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start RTP audio", e)
            _statusText.value = "Im Gespräch (Audio konnte nicht gestartet werden)"
            stopRtpAudio()
        }
    }

    private fun acquireCallWakeLock() {
        if (callWakeLock?.isHeld == true) return
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return
        callWakeLock = runCatching {
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Stromruf:SmartCallsCall"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseCallWakeLock() {
        callWakeLock?.let { lock ->
            runCatching {
                if (lock.isHeld) lock.release()
            }
        }
        callWakeLock = null
    }

    private fun prepareAudioDevices() {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: throw IllegalStateException("AudioManager nicht verfügbar")
        audioManager = manager
        if (previousAudioMode == null) previousAudioMode = manager.mode
        if (previousSpeakerphoneOn == null) previousSpeakerphoneOn = manager.isSpeakerphoneOn

        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isSpeakerphoneOn = speakerphoneOn

        val inputMin = AudioRecord.getMinBufferSize(
            RTP_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (inputMin <= 0) {
            throw IllegalStateException("AudioRecord unterstützt 8 kHz nicht")
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            RTP_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(inputMin, RTP_FRAME_SAMPLES * 2 * 10)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Mikrofon konnte nicht initialisiert werden")
        }
        audioRecord = record

        val outputMin = AudioTrack.getMinBufferSize(
            RTP_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (outputMin <= 0) {
            throw IllegalStateException("AudioTrack unterstützt 8 kHz nicht")
        }

        @Suppress("DEPRECATION")
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RTP_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(outputMin, RTP_FRAME_SAMPLES * 2 * 10))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                RTP_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(outputMin, RTP_FRAME_SAMPLES * 2 * 10),
                AudioTrack.MODE_STREAM
            )
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException("Lautsprecher konnte nicht initialisiert werden")
        }
        audioTrack = track
    }

    private suspend fun captureAndSendRtp(socket: DatagramSocket) {
        val record = audioRecord ?: return
        val pcm = ShortArray(RTP_FRAME_SAMPLES)

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Mikrofonaufnahme konnte nicht gestartet werden")
            }

            while (
                currentCoroutineContext().isActive &&
                _state.value == SipState.IN_CALL &&
                !socket.isClosed
            ) {
                var filled = 0
                while (filled < RTP_FRAME_SAMPLES && currentCoroutineContext().isActive) {
                    val read = record.read(
                        pcm,
                        filled,
                        RTP_FRAME_SAMPLES - filled,
                        AudioRecord.READ_BLOCKING
                    )
                    if (read < 0) {
                        Log.w(tag, "AudioRecord wurde beendet ($read)")
                        return
                    }
                    if (read == 0) {
                        delay(20)
                        continue
                    }
                    filled += read
                }
                if (filled < RTP_FRAME_SAMPLES) continue

                val codec = negotiatedCodecs[negotiatedPayloadType] ?: G711Codec.PCMA
                val payload = ByteArray(RTP_FRAME_SAMPLES)
                for (i in 0 until RTP_FRAME_SAMPLES) {
                    payload[i] = if (muted) {
                        encodeG711Sample(0, codec)
                    } else {
                        encodeG711Sample(pcm[i].toInt(), codec)
                    }
                }

                callWavRecorder?.writeMic(pcm)
                sendRtpPacket(socket, payload)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "RTP microphone loop failed", e)
        }
    }

    private fun sendRtpPacket(socket: DatagramSocket, payload: ByteArray) {
        synchronized(rtpSendLock) {
            val destination = remoteRtpAddress ?: return
            val packet = ByteArray(12 + payload.size)
            packet[0] = 0x80.toByte()
            packet[1] = (negotiatedPayloadType and 0x7f).toByte()
            packet[2] = ((rtpSequence ushr 8) and 0xff).toByte()
            packet[3] = (rtpSequence and 0xff).toByte()
            packet[4] = ((rtpTimestamp ushr 24) and 0xff).toByte()
            packet[5] = ((rtpTimestamp ushr 16) and 0xff).toByte()
            packet[6] = ((rtpTimestamp ushr 8) and 0xff).toByte()
            packet[7] = (rtpTimestamp and 0xff).toByte()
            packet[8] = ((rtpSsrc ushr 24) and 0xff).toByte()
            packet[9] = ((rtpSsrc ushr 16) and 0xff).toByte()
            packet[10] = ((rtpSsrc ushr 8) and 0xff).toByte()
            packet[11] = (rtpSsrc and 0xff).toByte()
            payload.copyInto(packet, destinationOffset = 12)

            socket.send(DatagramPacket(packet, packet.size, destination))
            rtpSequence = (rtpSequence + 1) and 0xffff
            rtpTimestamp = (rtpTimestamp + RTP_FRAME_SAMPLES) and 0xffffffffL
            lastRtpSentAt = System.currentTimeMillis()
        }
    }

    private fun sendRtpKeepAlive(socket: DatagramSocket) {
        val codec = negotiatedCodecs[negotiatedPayloadType] ?: G711Codec.PCMA
        val silence = ByteArray(RTP_FRAME_SAMPLES) { encodeG711Sample(0, codec) }
        sendRtpPacket(socket, silence)
    }

    private suspend fun receiveRtpAudio(socket: DatagramSocket) {
        val buffer = ByteArray(4096)

        try {
            while (
                currentCoroutineContext().isActive &&
                _state.value == SipState.IN_CALL &&
                !socket.isClosed
            ) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }

                if (packet.length < 12) continue
                val first = buffer[0].toInt() and 0xff
                val second = buffer[1].toInt() and 0xff
                if ((first ushr 6) != 2) continue

                val csrcCount = first and 0x0f
                var payloadOffset = 12 + csrcCount * 4
                if (packet.length < payloadOffset) continue

                if ((first and 0x10) != 0) {
                    if (packet.length < payloadOffset + 4) continue
                    val extensionWords =
                        ((buffer[payloadOffset + 2].toInt() and 0xff) shl 8) or
                            (buffer[payloadOffset + 3].toInt() and 0xff)
                    payloadOffset += 4 + extensionWords * 4
                }
                if (packet.length <= payloadOffset) continue

                var payloadEnd = packet.length
                if ((first and 0x20) != 0) {
                    val padding = buffer[packet.length - 1].toInt() and 0xff
                    if (padding <= payloadEnd - payloadOffset) payloadEnd -= padding
                }

                val payloadType = second and 0x7f
                val codec = negotiatedCodecs[payloadType]
                    ?: when (payloadType) {
                        8 -> G711Codec.PCMA
                        0 -> G711Codec.PCMU
                        else -> null
                    }
                    ?: continue

                val payloadLength = payloadEnd - payloadOffset
                if (payloadLength <= 0) continue

                remoteRtpAddress = InetSocketAddress(packet.address, packet.port)
                val pcm = ShortArray(payloadLength)
                for (i in 0 until payloadLength) {
                    pcm[i] = decodeG711Sample(buffer[payloadOffset + i].toInt() and 0xff, codec)
                }

                callWavRecorder?.writeRemote(pcm)
                audioTrack?.write(pcm, 0, pcm.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "RTP receive loop failed", e)
        }
    }

    private fun stopRtpAudio() {
        releaseCallWakeLock()
        rtpReceiveJob?.cancel()
        audioCaptureJob?.cancel()
        rtpKeepAliveJob?.cancel()
        rtpReceiveJob = null
        audioCaptureJob = null
        rtpKeepAliveJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null

        try {
            rtpSocket?.close()
        } catch (_: Exception) {
        }
        rtpSocket = null
        remoteRtpAddress = null
        restoreAudioRoute()
    }

    private fun restoreAudioRoute() {
        val manager = audioManager ?: return
        previousSpeakerphoneOn?.let { previous ->
            runCatching { manager.isSpeakerphoneOn = previous }
        }
        previousAudioMode?.let { previous ->
            runCatching { manager.mode = previous }
        }
        audioManager = null
        previousAudioMode = null
        previousSpeakerphoneOn = null
    }

    private fun encodeG711Sample(sample: Int, codec: G711Codec): Byte =
        when (codec) {
            G711Codec.PCMA -> linearToALaw(sample)
            G711Codec.PCMU -> linearToMuLaw(sample)
        }

    private fun decodeG711Sample(value: Int, codec: G711Codec): Short =
        when (codec) {
            G711Codec.PCMA -> aLawToLinear(value)
            G711Codec.PCMU -> muLawToLinear(value)
        }

    private fun linearToMuLaw(sample: Int): Byte {
        val bias = 0x84
        val clip = 32635
        val sign = if (sample < 0) 0x80 else 0
        var pcm = if (sample < 0) -sample else sample
        pcm = pcm.coerceAtMost(clip) + bias

        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && (pcm and mask) == 0) {
            exponent--
            mask = mask ushr 1
        }
        val mantissa = (pcm ushr (exponent + 3)) and 0x0f
        return (((sign or (exponent shl 4) or mantissa) xor 0xff) and 0xff).toByte()
    }

    private fun muLawToLinear(value: Int): Short {
        val u = value.inv() and 0xff
        var sample = ((u and 0x0f) shl 3) + 0x84
        sample = sample shl ((u and 0x70) ushr 4)
        val decoded = if ((u and 0x80) != 0) 0x84 - sample else sample - 0x84
        return decoded.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun linearToALaw(sample: Int): Byte {
        var pcm = sample
        val mask: Int
        if (pcm >= 0) {
            mask = 0xd5
        } else {
            mask = 0x55
            pcm = -pcm - 1
        }
        pcm = pcm.coerceAtMost(32635)

        val encoded = if (pcm >= 256) {
            var exponent = 7
            var searchMask = 0x4000
            while (exponent > 0 && (pcm and searchMask) == 0) {
                exponent--
                searchMask = searchMask ushr 1
            }
            (exponent shl 4) or ((pcm ushr (exponent + 3)) and 0x0f)
        } else {
            pcm ushr 4
        }
        return ((encoded xor mask) and 0xff).toByte()
    }

    private fun aLawToLinear(value: Int): Short {
        val a = (value xor 0x55) and 0xff
        var sample = (a and 0x0f) shl 4
        val segment = (a and 0x70) ushr 4
        when (segment) {
            0 -> sample += 8
            1 -> sample += 0x108
            else -> {
                sample += 0x108
                sample = sample shl (segment - 1)
            }
        }
        val decoded = if ((a and 0x80) != 0) sample else -sample
        return decoded.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun startCallRecording() {
        try {
            val dir = File(context.filesDir, "smart_calls_recordings")
            if (!dir.exists()) dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
            val file = File(dir, "Call_${activeCallTarget ?: "Unknown"}_$timestamp.wav")
            val recorder = PcmCallRecorder(file)

            callWavRecorder = recorder
            currentRecordFile = file
            _lastRecordingFile.value = file
            _isRecording.value = true
            Log.d(tag, "Local call recording started: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start local call recording", e)
            _isRecording.value = false
        }
    }

    private fun stopCallRecording() {
        val recorder = callWavRecorder ?: return
        callWavRecorder = null
        val savedFile = recorder.outputFile

        scope.launch(Dispatchers.IO) {
            try {
                recorder.finish()
                if (savedFile.exists() && savedFile.length() > 44) {
                    Log.d(tag, "Local call recording saved: ${savedFile.absolutePath}")

                    // Auto-save to user selected folder / Google Drive if enabled
                    val storageMgr = RecordingStorageManager(context)
                    if (storageMgr.getCustomFolderUri() != null && storageMgr.isAutoExportEnabled()) {
                        val res = storageMgr.saveFileToCustomFolder(savedFile)
                        if (res.isSuccess) {
                            Log.d(tag, "Auto-saved recording to target: ${res.getOrNull()}")
                        } else {
                            Log.w(tag, "Could not auto-save to target: ${res.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Log.w(tag, "Local call recording contained no audio samples")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error finalizing local call recording", e)
            } finally {
                if (callWavRecorder == null) _isRecording.value = false
            }
        }
    }

    private class PcmCallRecorder(val outputFile: File) {
        private val workDir = File(outputFile.parentFile, ".smartcall_pcm")
        private val micFile = File(workDir, "${outputFile.name}.mic.pcm")
        private val remoteFile = File(workDir, "${outputFile.name}.remote.pcm")
        private val micStream: BufferedOutputStream
        private val remoteStream: BufferedOutputStream
        private var finished = false

        init {
            workDir.mkdirs()
            micFile.delete()
            remoteFile.delete()
            outputFile.delete()
            micStream = BufferedOutputStream(FileOutputStream(micFile))
            remoteStream = BufferedOutputStream(FileOutputStream(remoteFile))
        }

        fun writeMic(samples: ShortArray) {
            synchronized(this) {
                if (!finished) writeSamples(micStream, samples)
            }
        }

        fun writeRemote(samples: ShortArray) {
            synchronized(this) {
                if (!finished) writeSamples(remoteStream, samples)
            }
        }

        fun finish() {
            synchronized(this) {
                if (finished) return
                finished = true
                runCatching { micStream.flush(); micStream.close() }
                runCatching { remoteStream.flush(); remoteStream.close() }
            }

            try {
                combineToStereoWav()
            } finally {
                micFile.delete()
                remoteFile.delete()
            }
        }

        private fun writeSamples(output: OutputStream, samples: ShortArray) {
            val bytes = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                val value = samples[i].toInt()
                bytes[i * 2] = (value and 0xff).toByte()
                bytes[i * 2 + 1] = ((value ushr 8) and 0xff).toByte()
            }
            output.write(bytes)
        }

        private fun combineToStereoWav() {
            val micSamples = micFile.length() / 2
            val remoteSamples = remoteFile.length() / 2
            val totalSamples = maxOf(micSamples, remoteSamples)
            if (totalSamples <= 0) {
                outputFile.delete()
                return
            }

            var dataBytes = 0
            val left = ShortArray(160)
            val right = ShortArray(160)
            FileInputStream(micFile).use { micInput ->
                FileInputStream(remoteFile).use { remoteInput ->
                    BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                        writeWavHeader(output, 0)
                        var processed = 0L
                        while (processed < totalSamples) {
                            val frameSamples =
                                minOf(160L, totalSamples - processed).toInt()
                            java.util.Arrays.fill(left, 0, frameSamples, 0.toShort())
                            java.util.Arrays.fill(right, 0, frameSamples, 0.toShort())
                            if (processed < micSamples) {
                                readPcmSamples(micInput, left, frameSamples)
                            }
                            if (processed < remoteSamples) {
                                readPcmSamples(remoteInput, right, frameSamples)
                            }

                            val stereo = ByteArray(frameSamples * 4)
                            for (i in 0 until frameSamples) {
                                val leftValue = left[i].toInt()
                                val rightValue = right[i].toInt()
                                val offset = i * 4
                                stereo[offset] = (leftValue and 0xff).toByte()
                                stereo[offset + 1] = ((leftValue ushr 8) and 0xff).toByte()
                                stereo[offset + 2] = (rightValue and 0xff).toByte()
                                stereo[offset + 3] = ((rightValue ushr 8) and 0xff).toByte()
                            }
                            output.write(stereo)
                            dataBytes += stereo.size
                            processed += frameSamples
                        }
                    }
                }
            }

            RandomAccessFile(outputFile, "rw").use { file ->
                file.seek(4)
                writeLittleEndianInt(file, 36 + dataBytes)
                file.seek(40)
                writeLittleEndianInt(file, dataBytes)
            }
        }

        private fun readPcmSamples(
            input: InputStream,
            target: ShortArray,
            count: Int
        ) {
            val bytes = ByteArray(count * 2)
            var totalRead = 0
            while (totalRead < bytes.size) {
                val read = input.read(bytes, totalRead, bytes.size - totalRead)
                if (read <= 0) break
                totalRead += read
            }
            val samplesRead = totalRead / 2
            for (i in 0 until samplesRead) {
                target[i] = (
                    (bytes[i * 2].toInt() and 0xff) or
                        (bytes[i * 2 + 1].toInt() shl 8)
                    ).toShort()
            }
        }

        private fun writeWavHeader(output: OutputStream, dataBytes: Int) {
            output.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(output, 36 + dataBytes)
            output.write("WAVE".toByteArray(Charsets.US_ASCII))
            output.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(output, 16)
            writeLittleEndianShort(output, 1)
            writeLittleEndianShort(output, 2)
            writeLittleEndianInt(output, 8000)
            writeLittleEndianInt(output, 8000 * 2 * 16 / 8)
            writeLittleEndianShort(output, 2 * 16 / 8)
            writeLittleEndianShort(output, 16)
            output.write("data".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(output, dataBytes)
        }

        private fun writeLittleEndianShort(output: OutputStream, value: Int) {
            output.write(value and 0xff)
            output.write((value ushr 8) and 0xff)
        }

        private fun writeLittleEndianInt(output: OutputStream, value: Int) {
            output.write(value and 0xff)
            output.write((value ushr 8) and 0xff)
            output.write((value ushr 16) and 0xff)
            output.write((value ushr 24) and 0xff)
        }

        private fun writeLittleEndianInt(file: RandomAccessFile, value: Int) {
            file.write(value and 0xff)
            file.write((value ushr 8) and 0xff)
            file.write((value ushr 16) and 0xff)
            file.write((value ushr 24) and 0xff)
        }
    }

    fun disconnect() {
        stopInCallTimer()
        stopSessionRefresh()
        stopSipKeepAlive()
        stopRtpAudio()
        stopCallRecording()
        clientJob?.cancel()
        clientJob = null

        try {
            udpSocket?.close()
            tcpSocket?.close()
            sslSocket?.close()
        } catch (e: Exception) {
            Log.w(tag, "Error closing SIP sockets", e)
        }

        udpSocket = null
        tcpSocket = null
        sslSocket = null
        socketWriter = null
        socketReader = null
        lastUdpSender = null
        clearCallDialog()

        _state.value = SipState.DISCONNECTED
        _statusText.value = "Nicht verbunden"
    }
}
