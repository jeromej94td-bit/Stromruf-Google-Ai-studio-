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

    // Digest Authentication
    private var lastRealm = ""
    private var lastNonce = ""
    private var lastQop: String? = null
    private var lastOpaque: String? = null

    // Audio & Recording
    private var mediaRecorder: MediaRecorder? = null
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
        _state.value = SipState.CONNECTING
        _statusText.value = "Verbinde über ${config.protocol.name} zu ${config.sipRegistrar}:${config.port}..."

        clientJob = scope.launch {
            try {
                findLocalIp()
                connectSocket(config)
                sendRegister(config, null)
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
                socket.soTimeout = 30000
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
                socket.soTimeout = 30000
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
        when (config.protocol) {
            SipTransportProtocol.UDP -> {
                val address = InetAddress.getByName(config.sipRegistrar)
                val packet = DatagramPacket(bytes, bytes.size, address, config.port)
                udpSocket?.send(packet)
            }
            SipTransportProtocol.TCP, SipTransportProtocol.TLS -> {
                socketWriter?.apply {
                    write(bytes)
                    flush()
                }
            }
        }
        Log.d(tag, "Sent SIP Message:\n$message")
    }

    private fun listenForMessages(config: SipAccountConfig) {
        val buffer = ByteArray(8192)
        while (scope.isActive && _state.value != SipState.DISCONNECTED) {
            try {
                val rawMessage = when (config.protocol) {
                    SipTransportProtocol.UDP -> {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)
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
                    Log.e(tag, "Error reading SIP stream", e)
                    _state.value = SipState.ERROR
                    _statusText.value = "Verbindung unterbrochen: ${e.message}"
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

        if (headerBuilder.isEmpty()) return null

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
        val firstLine = message.lines().firstOrNull() ?: return
        val statusCode = firstLine.split(" ").getOrNull(1)?.toIntOrNull() ?: return

        when (statusCode) {
            200 -> {
                if (_state.value == SipState.CONNECTING) {
                    _state.value = SipState.REGISTERED
                    _statusText.value = "Registriert (${config.sipRegistrar}:${config.port} [${config.protocol.name}])"
                } else if (_state.value == SipState.DIALING || _state.value == SipState.RINGING) {
                    _state.value = SipState.IN_CALL
                    _statusText.value = "Im Gespräch"
                    extractToTag(message)
                    sendAck(config)
                    startInCallTimer()
                    startCallRecording()
                }
            }
            401, 407 -> {
                // Parse Challenge
                val authHeader = message.lines().firstOrNull {
                    it.startsWith("WWW-Authenticate:", ignoreCase = true) ||
                    it.startsWith("Proxy-Authenticate:", ignoreCase = true)
                }
                if (authHeader != null) {
                    parseAuthHeader(authHeader)
                    cseq++
                    if (_state.value == SipState.CONNECTING) {
                        val auth = buildDigestAuth("REGISTER", "sip:${config.sipRegistrar}", config)
                        sendRegister(config, auth)
                    } else if (_state.value == SipState.DIALING && activeCallTarget != null) {
                        val auth = buildDigestAuth("INVITE", "sip:$activeCallTarget@${config.sipRegistrar}", config)
                        sendInvite(activeCallTarget!!, config, auth)
                    }
                } else {
                    _state.value = SipState.ERROR
                    _statusText.value = "Fehler: 401 Unauthorized (Kein Auth-Header)"
                }
            }
            100 -> {
                if (_state.value == SipState.DIALING) {
                    _statusText.value = "Wählt..."
                }
            }
            180, 183 -> {
                _state.value = SipState.RINGING
                _statusText.value = "Klingelt..."
            }
            403 -> {
                _state.value = SipState.ERROR
                _statusText.value = "Fehler 403: Zugriff verweigert (Passwort/Benutzer prüfen)"
                stopCallRecording()
            }
            404 -> {
                _state.value = SipState.ERROR
                _statusText.value = "Fehler 404: Rufnummer nicht gefunden"
                stopCallRecording()
            }
            486 -> {
                _state.value = SipState.ERROR
                _statusText.value = "Besetzt (486 Busy Here)"
                stopCallRecording()
            }
            487 -> {
                _state.value = SipState.REGISTERED
                _statusText.value = "Anruf abgebrochen"
                stopCallRecording()
            }
            else -> {
                if (statusCode >= 400) {
                    _state.value = SipState.ERROR
                    _statusText.value = "SIP Fehler $statusCode: ${firstLine.substringAfter(statusCode.toString()).trim()}"
                    stopCallRecording()
                }
            }
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

    fun makeCall(targetNumber: String) {
        val config = currentConfig
        if (config == null || _state.value != SipState.REGISTERED) return
        activeCallTarget = targetNumber
        _state.value = SipState.DIALING
        _statusText.value = "Wählt $targetNumber..."
        cseq++
        callId = UUID.randomUUID().toString()
        fromTag = generateRandomHex(8)
        toTag = null

        scope.launch {
            sendInvite(targetNumber, config, null)
        }
    }

    private fun sendInvite(targetNumber: String, config: SipAccountConfig, authHeader: String?) {
        val transport = config.protocol.name
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
        val user = config.sipUser
        val domain = config.sipRegistrar

        val sdp = StringBuilder()
            .append("v=0\r\n")
            .append("o=SmartCalls 1000 1000 IN IP4 $localIp\r\n")
            .append("s=SmartCall\r\n")
            .append("c=IN IP4 $localIp\r\n")
            .append("t=0 0\r\n")
            .append("m=audio 40000 RTP/AVP 8 0 101\r\n")
            .append("a=rtpmap:8 PCMA/8000\r\n")
            .append("a=rtpmap:0 PCMU/8000\r\n")
            .append("a=rtpmap:101 telephone-event/8000\r\n")
            .append("a=sendrecv\r\n")
            .toString()

        val sdpBytes = sdp.toByteArray(Charsets.UTF_8)

        val sb = StringBuilder()
        sb.append("INVITE sip:$targetNumber@$domain SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: <sip:$targetNumber@$domain>\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $cseq INVITE\r\n")
        sb.append("Contact: <sip:$user@$localIp:$localPort;transport=${transport.lowercase(Locale.ROOT)}>\r\n")
        sb.append("Content-Type: application/sdp\r\n")
        sb.append("User-Agent: SmartCalls/1.0.0 Android\r\n")
        if (authHeader != null) {
            sb.append("Proxy-Authorization: $authHeader\r\n")
        }
        sb.append("Content-Length: ${sdpBytes.size}\r\n\r\n")
        sb.append(sdp)

        sendSipMessage(sb.toString(), config)
    }

    private fun sendAck(config: SipAccountConfig) {
        val target = activeCallTarget ?: return
        val transport = config.protocol.name
        val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
        val user = config.sipUser
        val domain = config.sipRegistrar

        val toHeader = if (toTag != null) "<sip:$target@$domain>;tag=$toTag" else "<sip:$target@$domain>"

        val sb = StringBuilder()
        sb.append("ACK sip:$target@$domain SIP/2.0\r\n")
        sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
        sb.append("Max-Forwards: 70\r\n")
        sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
        sb.append("To: $toHeader\r\n")
        sb.append("Call-ID: $callId@$localIp\r\n")
        sb.append("CSeq: $cseq ACK\r\n")
        sb.append("User-Agent: SmartCalls/1.0.0 Android\r\n")
        sb.append("Content-Length: 0\r\n\r\n")

        sendSipMessage(sb.toString(), config)
    }

    fun hangUp() {
        val config = currentConfig ?: return
        val target = activeCallTarget
        stopInCallTimer()
        stopCallRecording()

        scope.launch {
            if (_state.value == SipState.IN_CALL && target != null) {
                // Send BYE
                cseq++
                val transport = config.protocol.name
                val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
                val user = config.sipUser
                val domain = config.sipRegistrar
                val toHeader = if (toTag != null) "<sip:$target@$domain>;tag=$toTag" else "<sip:$target@$domain>"

                val sb = StringBuilder()
                sb.append("BYE sip:$target@$domain SIP/2.0\r\n")
                sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
                sb.append("Max-Forwards: 70\r\n")
                sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
                sb.append("To: $toHeader\r\n")
                sb.append("Call-ID: $callId@$localIp\r\n")
                sb.append("CSeq: $cseq BYE\r\n")
                sb.append("User-Agent: SmartCalls/1.0.0 Android\r\n")
                sb.append("Content-Length: 0\r\n\r\n")

                sendSipMessage(sb.toString(), config)
            } else if (_state.value == SipState.DIALING || _state.value == SipState.RINGING) {
                // Send CANCEL
                val transport = config.protocol.name
                val viaBranch = "z9hG4bK-${generateRandomHex(10)}"
                val user = config.sipUser
                val domain = config.sipRegistrar

                val sb = StringBuilder()
                sb.append("CANCEL sip:$target@$domain SIP/2.0\r\n")
                sb.append("Via: SIP/2.0/$transport $localIp:$localPort;branch=$viaBranch;rport\r\n")
                sb.append("Max-Forwards: 70\r\n")
                sb.append("From: \"${config.displayName}\" <sip:$user@$domain>;tag=$fromTag\r\n")
                sb.append("To: <sip:$target@$domain>\r\n")
                sb.append("Call-ID: $callId@$localIp\r\n")
                sb.append("CSeq: $cseq CANCEL\r\n")
                sb.append("User-Agent: SmartCalls/1.0.0 Android\r\n")
                sb.append("Content-Length: 0\r\n\r\n")

                sendSipMessage(sb.toString(), config)
            }

            _state.value = SipState.REGISTERED
            _statusText.value = "Registriert (${config.sipRegistrar}:${config.port} [${config.protocol.name}])"
            activeCallTarget = null
        }
    }

    private fun extractToTag(message: String) {
        val toLine = message.lines().firstOrNull { it.startsWith("To:", ignoreCase = true) } ?: return
        if (toLine.contains("tag=")) {
            toTag = toLine.substringAfter("tag=").substringBefore(";").trim()
        }
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

    private fun startCallRecording() {
        try {
            val dir = File(context.filesDir, "smart_calls_recordings")
            if (!dir.exists()) dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
            val file = File(dir, "Call_${activeCallTarget ?: "Unknown"}_$timestamp.mp4")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            currentRecordFile = file
            _lastRecordingFile.value = file
            _isRecording.value = true
            Log.d(tag, "Recording started to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start audio recording", e)
            _isRecording.value = false
        }
    }

    private fun stopCallRecording() {
        try {
            if (_isRecording.value) {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                _isRecording.value = false
                Log.d(tag, "Recording stopped. File saved: ${currentRecordFile?.absolutePath}")

                // Auto-save to user selected folder / Google Drive if enabled
                currentRecordFile?.let { savedFile ->
                    if (savedFile.exists() && savedFile.length() > 0) {
                        scope.launch {
                            try {
                                val storageMgr = RecordingStorageManager(context)
                                if (storageMgr.getCustomFolderUri() != null && storageMgr.isAutoExportEnabled()) {
                                    val res = storageMgr.saveFileToCustomFolder(savedFile)
                                    if (res.isSuccess) {
                                        Log.d(tag, "Auto-saved recording to target: ${res.getOrNull()}")
                                    } else {
                                        Log.w(tag, "Could not auto-save to target: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Auto-export recording error", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping recording", e)
        }
    }

    fun disconnect() {
        stopInCallTimer()
        stopCallRecording()
        clientJob?.cancel()
        clientJob = null

        try {
            udpSocket?.close()
            tcpSocket?.close()
            sslSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        udpSocket = null
        tcpSocket = null
        sslSocket = null
        socketWriter = null
        socketReader = null

        _state.value = SipState.DISCONNECTED
        _statusText.value = "Nicht verbunden"
    }
}
