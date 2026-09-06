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
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.net.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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
    val sipRegistrar: String = "secure.sip.easybell.de",
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
    private var socketReader: SipStreamReader? = null

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
    @Volatile private var lastInviteResponseUdpSender: InetSocketAddress? = null

    /**
     * INVITE is stateful even on TLS. In particular, every 3xx-6xx final
     * response (including 401/407) needs a transaction ACK with the exact
     * original Via branch, Request-URI, From and Call-ID.
     */
    private data class DigestChallenge(
        val realm: String,
        val nonce: String,
        val qop: String?,
        val opaque: String?
    )

    private enum class InvitePurpose { INITIAL, SESSION_REFRESH }

    private data class InviteClientTransaction(
        val cseq: Int,
        val purpose: InvitePurpose,
        val requestUri: String,
        val viaHeader: String,
        val fromHeader: String,
        val callIdHeader: String,
        val routeHeaders: List<String>,
        val authHeaderName: String?,
        val authHeaderValue: String?,
        val authChallenge: DigestChallenge?
    )

    private data class DialogRouting(
        val requestUri: String,
        val routeHeaders: List<String>
    )

    private val inviteTransactionLock = Any()
    private val inviteTransactions = LinkedHashMap<Int, InviteClientTransaction>()
    private val handledInviteAuthChallenges = mutableSetOf<String>()
    private val successfulAckBranches = mutableMapOf<Int, String>()
    private var dialogAuthHeaderName: String? = null
    private var dialogAuthChallenge: DigestChallenge? = null

    // RTP media state. SIP only establishes the call; these sockets carry the audio.
    private enum class G711Codec { PCMA, PCMU }

    private data class SrtpCryptoParameters(
        val masterKey: ByteArray,
        val masterSalt: ByteArray,
        val authTagLength: Int = 10
    )

    /**
     * Minimaler SRTP-Kontext für AES_CM_128_HMAC_SHA1_80 nach RFC 3711.
     * Der Master-Key kommt bei TLS geschützt über SDP a=crypto (RFC 4568).
     */
    private class SrtpContext(parameters: SrtpCryptoParameters) {
        private val encryptionKey: ByteArray
        private val authenticationKey: ByteArray
        private val saltingKey: ByteArray
        private val authTagLength = parameters.authTagLength

        private var sendRolloverCounter = 0L
        private var lastSentSequence: Int? = null
        private var receiveRolloverCounter = 0L
        private var lastReceivedSequence: Int? = null
        private var highestReceivedIndex = -1L

        init {
            require(parameters.masterKey.size == 16)
            require(parameters.masterSalt.size == 14)
            require(authTagLength == 10)
            encryptionKey = deriveSessionKey(parameters.masterKey, parameters.masterSalt, 0x00, 16)
            authenticationKey = deriveSessionKey(parameters.masterKey, parameters.masterSalt, 0x01, 20)
            saltingKey = deriveSessionKey(parameters.masterKey, parameters.masterSalt, 0x02, 14)
        }

        @Synchronized
        fun protect(rtpPacket: ByteArray): ByteArray {
            if (rtpPacket.size < 12) return rtpPacket
            val sequence = readUInt16(rtpPacket, 2)
            val ssrc = readUInt32(rtpPacket, 8)
            if (lastSentSequence != null &&
                lastSentSequence!! > 0x8000 &&
                sequence < 0x8000 &&
                lastSentSequence!! - sequence > 0x8000
            ) {
                sendRolloverCounter = (sendRolloverCounter + 1) and 0xffffffffL
            }
            lastSentSequence = sequence
            val packetIndex = (sendRolloverCounter shl 16) or sequence.toLong()

            val encrypted = rtpPacket.copyOf()
            val payloadStart = payloadOffset(encrypted) ?: return encrypted
            cryptPayload(encrypted, payloadStart, encrypted.size, ssrc, packetIndex)

            val protectedPacket = ByteArray(encrypted.size + authTagLength)
            encrypted.copyInto(protectedPacket)
            authenticationTag(encrypted, sendRolloverCounter)
                .copyInto(protectedPacket, encrypted.size)
            return protectedPacket
        }

        @Synchronized
        fun unprotect(srtpPacket: ByteArray): ByteArray? {
            if (srtpPacket.size < 12 + authTagLength) return null
            val bodyLength = srtpPacket.size - authTagLength
            val encrypted = srtpPacket.copyOfRange(0, bodyLength)
            val sequence = readUInt16(encrypted, 2)
            val ssrc = readUInt32(encrypted, 8)
            val estimatedRoc = estimateReceiveRolloverCounter(sequence)
            val packetIndex = (estimatedRoc shl 16) or sequence.toLong()

            val expectedTag = authenticationTag(encrypted, estimatedRoc)
            val actualTag = srtpPacket.copyOfRange(bodyLength, srtpPacket.size)
            if (!MessageDigest.isEqual(expectedTag, actualTag)) return null

            val payloadStart = payloadOffset(encrypted) ?: return null
            cryptPayload(encrypted, payloadStart, encrypted.size, ssrc, packetIndex)

            if (packetIndex > highestReceivedIndex) {
                highestReceivedIndex = packetIndex
                receiveRolloverCounter = estimatedRoc
                lastReceivedSequence = sequence
            }
            return encrypted
        }

        private fun estimateReceiveRolloverCounter(sequence: Int): Long {
            val last = lastReceivedSequence ?: return receiveRolloverCounter
            return when {
                last < 0x8000 &&
                    sequence > 0x8000 &&
                    sequence - last > 0x8000 ->
                    (receiveRolloverCounter - 1).coerceAtLeast(0L)

                last > 0x8000 &&
                    sequence < 0x8000 &&
                    last - sequence > 0x8000 ->
                    (receiveRolloverCounter + 1) and 0xffffffffL

                else -> receiveRolloverCounter
            }
        }

        private fun authenticationTag(packet: ByteArray, rolloverCounter: Long): ByteArray {
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(authenticationKey, "HmacSHA1"))
            mac.update(packet)
            mac.update(
                byteArrayOf(
                    ((rolloverCounter ushr 24) and 0xff).toByte(),
                    ((rolloverCounter ushr 16) and 0xff).toByte(),
                    ((rolloverCounter ushr 8) and 0xff).toByte(),
                    (rolloverCounter and 0xff).toByte()
                )
            )
            return mac.doFinal().copyOf(authTagLength)
        }

        private fun cryptPayload(
            packet: ByteArray,
            payloadStart: Int,
            payloadEnd: Int,
            ssrc: Long,
            packetIndex: Long
        ) {
            if (payloadStart >= payloadEnd) return
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(encryptionKey, "AES"),
                IvParameterSpec(buildPacketIv(ssrc, packetIndex))
            )
            val keystream = cipher.doFinal(ByteArray(payloadEnd - payloadStart))
            for (i in keystream.indices) {
                packet[payloadStart + i] =
                    (packet[payloadStart + i].toInt() xor keystream[i].toInt()).toByte()
            }
        }

        private fun buildPacketIv(ssrc: Long, packetIndex: Long): ByteArray {
            // RFC 3711 §4.1.1:
            // IV = (k_s * 2^16) XOR (SSRC * 2^64) XOR (i * 2^16)
            //
            // The session salt is already the 112-bit k_s value. The SSRC and
            // packet index must be XORed into that value; overwriting those
            // bytes drops the salt bits and produces invalid SRTP packets.
            val iv = ByteArray(16)
            saltingKey.copyInto(iv, 0)
            xorUInt32(iv, 4, ssrc)
            xorUInt48(iv, 8, packetIndex)
            return iv
        }

        companion object {
            private fun deriveSessionKey(
                masterKey: ByteArray,
                masterSalt: ByteArray,
                label: Int,
                length: Int
            ): ByteArray {
                // key_id = label || (index DIV kdr), right-aligned in the 112-bit salt.
                val keyId = ByteArray(14)
                keyId[7] = label.toByte()
                val iv = ByteArray(16)
                for (i in masterSalt.indices) {
                    iv[i] = (masterSalt[i].toInt() xor keyId[i].toInt()).toByte()
                }
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(masterKey, "AES"),
                    IvParameterSpec(iv)
                )
                return cipher.doFinal(ByteArray(length))
            }

            private fun payloadOffset(packet: ByteArray): Int? {
                if (packet.size < 12) return null
                val first = packet[0].toInt() and 0xff
                if ((first ushr 6) != 2) return null

                var offset = 12 + (first and 0x0f) * 4
                if ((first and 0x10) != 0) {
                    if (packet.size < offset + 4) return null
                    val extensionWords =
                        ((packet[offset + 2].toInt() and 0xff) shl 8) or
                            (packet[offset + 3].toInt() and 0xff)
                    offset += 4 + extensionWords * 4
                }
                return offset.takeIf { it <= packet.size }
            }

            private fun readUInt16(packet: ByteArray, offset: Int): Int =
                ((packet[offset].toInt() and 0xff) shl 8) or
                    (packet[offset + 1].toInt() and 0xff)

            private fun readUInt32(packet: ByteArray, offset: Int): Long =
                ((packet[offset].toLong() and 0xffL) shl 24) or
                    ((packet[offset + 1].toLong() and 0xffL) shl 16) or
                    ((packet[offset + 2].toLong() and 0xffL) shl 8) or
                    (packet[offset + 3].toLong() and 0xffL)

            private fun xorUInt32(packet: ByteArray, offset: Int, value: Long) {
                for (i in 0 until 4) {
                    val shift = 24 - i * 8
                    packet[offset + i] = (
                        packet[offset + i].toInt() xor
                            (((value ushr shift) and 0xffL).toInt())
                        ).toByte()
                }
            }

            private fun xorUInt48(packet: ByteArray, offset: Int, value: Long) {
                for (i in 0 until 6) {
                    val shift = 40 - i * 8
                    packet[offset + i] = (
                        packet[offset + i].toInt() xor
                            (((value ushr shift) and 0xffL).toInt())
                        ).toByte()
                }
            }
        }
    }

    private data class RemoteRtpDescription(
        val address: InetAddress,
        val port: Int,
        val payloadType: Int,
        val codecs: Map<Int, G711Codec>,
        val usesSrtp: Boolean,
        val srtpCrypto: SrtpCryptoParameters?
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
    @Volatile private var localSrtpContext: SrtpContext? = null
    @Volatile private var remoteSrtpContext: SrtpContext? = null
    private var remoteSrtpParameters: SrtpCryptoParameters? = null
    @Volatile private var remoteUsesSrtp = false
    private var localSrtpMasterKeySalt: ByteArray? = null
    private var rtpSequence = SecureRandom().nextInt(65536)
    private var rtpTimestamp = SecureRandom().nextInt().toLong() and 0xffffffffL
    private val rtpSsrc = SecureRandom().nextInt()

    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    @Volatile private var muted = false
    @Volatile private var speakerphoneOn = true
    @Volatile private var lastRtpSentAt = 0L
    @Volatile private var rtpPacketsSent = 0L
    @Volatile private var rtpPacketsReceived = 0L
    @Volatile private var srtpPacketsRejected = 0L
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
        // Run the RTP watchdog at frame cadence. If AudioRecord stalls, it
        // fills any gap with real RTP silence instead of going silent.
        const val RTP_KEEPALIVE_INTERVAL_MS = 20L
        const val RTP_KEEPALIVE_GAP_MS = 60L
        const val DEFAULT_SESSION_EXPIRES_SECONDS = 3600
        const val MIN_SESSION_EXPIRES_SECONDS = 20
    }

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
                if (_state.value != SipState.DISCONNECTED) {
                    Log.e(tag, "Connection error", e)
                    _state.value = SipState.ERROR
                    _statusText.value = "Fehler: ${e.localizedMessage ?: "Verbindung fehlgeschlagen"}"
                }
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
                socketReader = SipStreamReader(BufferedInputStream(socket.getInputStream())) {
                    sendSipMessage("\r\n", config)
                    Log.i(tag, "SIP keepalive ping answered on existing socket")
                }
                socket.localAddress?.hostAddress
                    ?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                    ?.let { localIp = it }
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
                socketReader = SipStreamReader(BufferedInputStream(socket.getInputStream())) {
                    sendSipMessage("\r\n", config)
                    Log.i(tag, "SIP keepalive ping answered on existing socket")
                }
                socket.localAddress?.hostAddress
                    ?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                    ?.let { localIp = it }
                localPort = socket.localPort
            }
        }
    }

    private fun sendSipMessage(
        message: String,
        config: SipAccountConfig,
        udpDestination: InetSocketAddress? = null
    ) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        synchronized(sipSendLock) {
            when (config.protocol) {
                SipTransportProtocol.UDP -> {
                    val socket = udpSocket
                        ?: throw SocketException("SIP UDP socket is not connected")
                    val destination = udpDestination
                        ?: lastUdpSender
                        ?: InetSocketAddress(config.sipRegistrar, config.port)
                    val address = destination.address
                        ?: InetAddress.getByName(destination.hostString)
                    socket.send(
                        DatagramPacket(
                            bytes,
                            bytes.size,
                            address,
                            destination.port
                        )
                    )
                }

                SipTransportProtocol.TCP, SipTransportProtocol.TLS -> {
                    val writer = socketWriter
                        ?: throw SocketException("SIP signaling socket is not connected")
                    writer.write(bytes)
                    writer.flush()
                }
            }
        }
        Log.d(tag, "SIP TX ${message.lineSequence().firstOrNull().orEmpty()} cseq=${extractCSeqMethod(message)}/${extractCSeqNumber(message)}")
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
                    // Stream keepalive stays on the existing TLS/TCP socket.
                    if (config.protocol == SipTransportProtocol.UDP) {
                        sendSipOptionsKeepAlive(config)
                    } else {
                        sendSipMessage("\r\n\r\n", config)
                    }
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
        sb.append("User-Agent: SmartCalls/1.7.0 Android\r\n")
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
                Log.d(tag, "SIP RX ${rawMessage.lineSequence().firstOrNull().orEmpty()} cseq=${extractCSeqMethod(rawMessage)}/${extractCSeqNumber(rawMessage)}")
                handleSipMessage(rawMessage, config)
            } catch (e: SocketTimeoutException) {
                // Keepalive check / retry
            } catch (e: Exception) {
                if (scope.isActive && _state.value != SipState.DISCONNECTED) {
                    Log.e(tag, "Error reading SIP stream", e)
                    stopSipKeepAlive()
                    val media = "TX=$rtpPacketsSent RX=$rtpPacketsReceived SRTP-verworfen=$srtpPacketsRejected"
                    finishCallWithState(
                        SipState.ERROR,
                        "SIP-Verbindung unterbrochen (${e.javaClass.simpleName}); $media"
                    )
                    // A new REGISTER alone cannot restore the peer's old call.

                }
                break
            }
        }
    }

    private fun readSipMessageFromStream(): String =
        socketReader?.readMessage()
            ?: throw EOFException("SIP reader is not connected")

    private fun rememberInviteTransaction(transaction: InviteClientTransaction) {
        synchronized(inviteTransactionLock) {
            inviteTransactions[transaction.cseq] = transaction
            while (inviteTransactions.size > 12) {
                val oldestCseq = inviteTransactions.keys.firstOrNull() ?: break
                inviteTransactions.remove(oldestCseq)
            }
        }
    }

    private fun findInviteTransaction(inviteCseq: Int?): InviteClientTransaction? {
        if (inviteCseq == null) return null
        return synchronized(inviteTransactionLock) {
            inviteTransactions[inviteCseq]
        }
    }

    private fun markInviteChallengeHandled(
        transaction: InviteClientTransaction,
        statusCode: Int,
        challenge: DigestChallenge
    ): Boolean = synchronized(inviteTransactionLock) {
        handledInviteAuthChallenges.add(
            "${transaction.cseq}:$statusCode:${challenge.realm}:${challenge.nonce}"
        )
    }

    private fun successfulAckBranch(inviteCseq: Int): String =
        synchronized(inviteTransactionLock) {
            successfulAckBranches.getOrPut(inviteCseq) {
                "z9hG4bK-${generateRandomHex(10)}"
            }
        }

    private fun clearInviteTransactionState() {
        synchronized(inviteTransactionLock) {
            inviteTransactions.clear()
            handledInviteAuthChallenges.clear()
            successfulAckBranches.clear()
        }
        dialogAuthHeaderName = null
        dialogAuthChallenge = null
    }

    private fun handleSipMessage(message: String, config: SipAccountConfig) {
        val firstLine = message.lines().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return

        if (firstLine.startsWith("SIP/2.0", ignoreCase = true)) {
            val statusCode = firstLine.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: return
            val method = extractCSeqMethod(message)
            val responseCseq = extractCSeqNumber(message)
            val responseCallId = extractHeaderValue(message, "Call-ID")?.trim()
            val inviteTransaction = if (method == "INVITE") {
                findInviteTransaction(responseCseq)
            } else {
                null
            }

            // OPTIONS keepalive responses are independent of the active call.
            if (method == "OPTIONS") {
                Log.d(tag, "SIP OPTIONS keepalive response: $statusCode")
                return
            }

…33515 tokens truncated…         )
                        Text(
                            text = "Zielordner frei wählen (lokal oder Google Drive)",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                // Current Destination Surface Box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Bg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isGoogleDrive) Color(0xFF4285F4).copy(alpha = 0.4f) else CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isGoogleDrive) Color(0xFF4285F4).copy(alpha = 0.2f) else if (storageManager.getCustomFolderUri() != null) NeonGreen.copy(alpha = 0.2f) else SurfaceColor,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isGoogleDrive) Icons.Default.CloudDone else if (storageManager.getCustomFolderUri() != null) Icons.Default.FolderOpen else Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isGoogleDrive) Color(0xFF4285F4) else if (storageManager.getCustomFolderUri() != null) NeonGreen else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = storageDisplayName,
                                fontWeight = FontWeight.Bold,
                                color = if (isGoogleDrive) Color(0xFF93C5FD) else Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isGoogleDrive) "Aufnahmen werden direkt in Google Drive gesichert" else if (storageManager.getCustomFolderUri() != null) "Aufnahmen werden in diesen lokalen Ordner synchronisiert" else "Standardmäßig im internen App-Speicher abgelegt",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isGoogleDrive) Color(0xFF4285F4) else NeonCyan
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFolderUpload,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Zielordner / Drive wählen",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (storageManager.getCustomFolderUri() != null) {
                        OutlinedButton(
                            onClick = {
                                storageManager.resetToDefault()
                                storageDisplayName = storageManager.getStorageDisplayName()
                                isGoogleDrive = storageManager.isGoogleDrive()
                                Toast.makeText(ctx, "Auf Standard-Speicher zurückgesetzt", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Standard", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Auto-Sync Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Automatisch im Zielordner speichern",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Jede neue SmartCall-Aufnahme direkt übertragen",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = autoExportEnabled,
                        onCheckedChange = {
                            autoExportEnabled = it
                            storageManager.setAutoExportEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = NeonGreen.copy(alpha = 0.3f)
                        )
                    )
                }

                // Batch Copy Button
                if (recordingsList.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { copyAllToTarget() },
                        enabled = !isExportingAll,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isGoogleDrive) Color(0xFF4285F4).copy(alpha = 0.6f) else NeonGreen.copy(alpha = 0.6f))
                    ) {
                        if (isExportingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NeonGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Wird übertragen...", color = Color.White, fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = if (isGoogleDrive) Icons.Default.CloudUpload else Icons.Default.FolderCopy,
                                contentDescription = null,
                                tint = if (isGoogleDrive) Color(0xFF4285F4) else NeonGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Alle ${recordingsList.size} Aufnahmen jetzt in Zielordner kopieren",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        OfflineTranscriptionSetup()
        LocalGemmaSetup()

        // LOCAL RECORDINGS ARCHIVE CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gespeicherte Aufnahmen (${recordingsList.size})",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Über 1 Minute automatisch lokal auf Deutsch transkribieren",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showApiKeyDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Gemini API-Key",
                                tint = if (transcriber.hasApiKey()) Color(0xFF8B5CF6) else TextMuted
                            )
                        }
                        IconButton(onClick = {
                            refreshRecordings()
                            reloadTranscripts()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren", tint = TextMuted)
                        }
                    }
                }

                if (recordingsList.isEmpty()) {
                    Text(
                        text = "Noch keine Anrufe aufgezeichnet. Anrufe über Smart Calls werden automatisch hier gespeichert.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recordingsList.forEach { file ->
                            val isPlaying = currentlyPlayingPath == file.absolutePath
                            val isTranscribing = transcribingFiles.contains(file.name)
                            val transcriptResult = cachedTranscripts[file.name]
                            val isTranscribed = transcriptResult != null

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = Bg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isPlaying) NeonCyan else if (isTranscribed) Color(0xFF8B5CF6).copy(alpha = 0.5f) else CardBorder
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OfflineRecordingTranscript(file)
                                    // Row 1: File Info & Audio Actions
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = file.name,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )

                                                if (isTranscribed) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.AutoAwesome,
                                                                contentDescription = null,
                                                                tint = Color(0xFF8B5CF6),
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Text(
                                                                text = "KI-Notiz",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFFC4B5FD)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            val kb = file.length() / 1024
                                            val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(file.lastModified()))
                                            Text(
                                                text = "$date · ${kb} KB",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // Play / Pause
                                            IconButton(onClick = { playRecording(file) }) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Abspielen",
                                                    tint = if (isPlaying) NeonCyan else NeonGreen
                                                )
                                            }

                                            // Save to Target / Drive
                                            IconButton(
                                                onClick = { saveSingleToTarget(file) }
                                            ) {
                                                Icon(
                                                    imageVector = if (isGoogleDrive) Icons.Default.CloudUpload else Icons.Default.SaveAlt,
                                                    contentDescription = "In Zielordner speichern",
                                                    tint = if (isGoogleDrive) Color(0xFF4285F4) else NeonCyan
                                                )
                                            }

                                            // Share / Open in Drive
                                            IconButton(
                                                onClick = { storageManager.shareRecording(file) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Teilen / In Drive senden",
                                                    tint = TextMuted
                                                )
                                            }

                                            // Delete
                                            IconButton(onClick = {
                                                deleteRecording(file)
                                                transcriptCache.delete(file.name)
                                                reloadTranscripts()
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Löschen",
                                                    tint = DangerRed.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }

                                    // Row 2: Transcription Area / Note Preview
                                    if (isTranscribing) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color(0xFF8B5CF6),
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = "✨ Gemini analysiert Audio & erstellt Notiz...",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFC4B5FD),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    } else if (isTranscribed && transcriptResult != null) {
                                        // Preview card with quick view button
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { activeTranscriptResult = transcriptResult },
                                            shape = RoundedCornerShape(8.dp),
                                            color = SurfaceColor,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.25f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = transcriptResult.summary.ifBlank { transcriptResult.rawText }
                                                        .lineSequence()
                                                        .filter { it.isNotBlank() && !it.startsWith("#") }
                                                        .take(3)
                                                        .joinToString("\n"),
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFE2E8F0),
                                                    maxLines = 3,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    lineHeight = 16.sp
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Tippen für vollständige Notiz & Transkript",
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF93C5FD)
                                                    )
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Visibility,
                                                            contentDescription = null,
                                                            tint = Color(0xFF8B5CF6),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Ansehen",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF8B5CF6)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        // Transcribe Button
                                        OutlinedButton(
                                            onClick = { startTranscription(file) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f)),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFFC4B5FD)
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = Color(0xFF8B5CF6),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Kostenlos lokal auf Deutsch transkribieren",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SUPABASE SMART CALL NOTES (summary only)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Gespeicherte Smart-Call-Notizen (${smartCallNotes.size})",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Nur Zusammenfassungen über 1 Minute · kein Audio und kein Transkript in Supabase",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = { reloadSmartCallNotes() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Smart-Call-Notizen aktualisieren",
                            tint = TextMuted
                        )
                    }
                }

                when {
                    isLoadingSmartCallNotes -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan
                            )
                        }
                    }
                    smartCallNotes.isEmpty() -> {
                        Text(
                            text = "Noch keine passenden Smart-Call-Zusammenfassungen gespeichert.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            smartCallNotes.forEach { note ->
                                val minutes = note.durationSeconds / 60
                                val seconds = note.durationSeconds % 60
                                val duration = String.format(
                                    Locale.GERMANY,
                                    "%02d:%02d Min.",
                                    minutes,
                                    seconds
                                )
                                val date = SimpleDateFormat(
                                    "dd.MM.yyyy HH:mm",
                                    Locale.GERMANY
                                ).format(Date(note.callStartedAt))
                                val title = note.contactName?.takeIf { it.isNotBlank() }
                                    ?: note.phone

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Bg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        CardBorder
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = title,
                                                color = Color.White,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = duration,
                                                color = NeonCyan,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Text(
                                            text = "${note.phone} · $date",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = note.summary,
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            maxLines = 8
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // TRANSCRIPTION DETAIL DIALOG
        activeTranscriptResult?.let { result ->
            TranscriptionDetailDialog(
                result = result,
                onDismiss = { activeTranscriptResult = null },
                onReTranscribe = {
                    val file = recordingsList.find { it.name == result.fileName }
                    activeTranscriptResult = null
                    if (file != null) {
                        startTranscription(file)
                    } else {
                        Toast.makeText(ctx, "Audiodatei nicht gefunden", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // GEMINI API KEY DIALOG
        if (showApiKeyDialog) {
            GeminiApiKeyDialog(
                onDismiss = {
                    showApiKeyDialog = false
                    pendingFileToTranscribe = null
                },
                onKeySaved = { key ->
                    showApiKeyDialog = false
                    pendingFileToTranscribe?.let { file ->
                        startTranscription(file)
                        pendingFileToTranscribe = null
                    }
                }
            )
        }
    }
}
