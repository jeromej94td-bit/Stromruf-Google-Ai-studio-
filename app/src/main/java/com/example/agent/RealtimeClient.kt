package com.example.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Connects the phone directly to ChatGPT Realtime API (PCM 24 kHz). */
object RealtimeClient {

    private const val TAG = "RealtimeClient"
    private const val SAMPLE_RATE = 24000

    enum class Phase { AUS, VERBINDET, BEREIT, AGENT_SPRICHT, KUNDE_SPRICHT, FEHLER }
    enum class Grund { NUTZER, AGENT, ZEITLIMIT, FEHLER }

    data class Zeile(
        val vomAgent: Boolean,
        val text: String,
        val zeit: Long = System.currentTimeMillis()
    )

    data class Werkzeugaufruf(
        val name: String,
        val argumente: JSONObject,
        val status: String = "Ausgefuehrt",
        val ergebnis: JSONObject? = null,
        val zeit: Long = System.currentTimeMillis()
    )

    private val _phase = MutableStateFlow(Phase.AUS)
    val phase: StateFlow<Phase> = _phase

    private val _verlauf = MutableStateFlow<List<Zeile>>(emptyList())
    val verlauf: StateFlow<List<Zeile>> = _verlauf

    private val _werkzeuge = MutableStateFlow<List<Werkzeugaufruf>>(emptyList())
    val werkzeuge: StateFlow<List<Werkzeugaufruf>> = _werkzeuge

    private val _fehler = MutableStateFlow<String?>(null)
    val fehler: StateFlow<String?> = _fehler

    private val _pegel = MutableStateFlow(0f)
    val pegel: StateFlow<Float> = _pegel

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId

    private val _startzeit = MutableStateFlow(0L)
    val startzeit: StateFlow<Long> = _startzeit

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var scope: CoroutineScope? = null
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private val laeuft = AtomicBoolean(false)
    private val agentSpricht = AtomicBoolean(false)
    private val ausgabe = ConcurrentLinkedQueue<ByteArray>()
    private var agentTextPuffer = StringBuilder()
    private var agentInfo: JSONObject? = null
    private var beendetCallback: ((Grund) -> Unit)? = null
    private var maxDauerMs = 10 * 60 * 1000L

    fun starten(
        context: Context,
        loginToken: String,
        agentId: String? = null,
        szenarioId: String? = null,
        test: Boolean = true,
        onBeendet: ((Grund) -> Unit)? = null
    ) {
        if (laeuft.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _fehler.value = "Mikrofon-Berechtigung fehlt."
            _phase.value = Phase.FEHLER
            return
        }

        beendetCallback = onBeendet
        _fehler.value = null
        _verlauf.value = emptyList()
        _werkzeuge.value = emptyList()
        _phase.value = Phase.VERBINDET
        laeuft.set(true)

        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            try {
                val antwort = AgentBackendClient.createRealtimeSession(loginToken, agentId, szenarioId, test)
                val token = antwort.optString("ephemeral_key")
                    .ifEmpty { antwort.optJSONObject("session")?.optString("client_secret") ?: "" }
                val wsUrl = antwort.optString("websocket_url", "wss://api.openai.com/v1/realtime?model=gpt-realtime")

                if (token.isBlank()) {
                    throw IllegalStateException("Kein Realtime-Token erhalten.")
                }

                agentInfo = antwort.optJSONObject("agent")
                maxDauerMs = ((agentInfo?.optInt("max_duration_min") ?: 10).coerceIn(1, 60)) * 60_000L
                val sid = antwort.optString("session_id")
                _sessionId.value = sid.ifEmpty { null }

                val wsReq = Request.Builder()
                    .url(wsUrl)
                    .header("Authorization", "Bearer $token")
                    .header("OpenAI-Beta", "realtime=v1")
                    .build()
                socket = http.newWebSocket(wsReq, baueZuhoerer(context))

                s.launch {
                    val start = System.currentTimeMillis()
                    while (laeuft.get()) {
                        if (System.currentTimeMillis() - start > maxDauerMs) {
                            beenden(Grund.ZEITLIMIT)
                            break
                        }
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Start fehlgeschlagen", e)
                _fehler.value = e.message ?: "Unbekannter Fehler"
                _phase.value = Phase.FEHLER
                laeuft.set(false)
                beendetCallback?.invoke(Grund.FEHLER)
            }
        }
    }

    fun textSenden(text: String) {
        val ws = socket ?: return
        if (text.isBlank()) return
        val ev = JSONObject()
            .put("type", "conversation.item.create")
            .put("item", JSONObject()
                .put("type", "message")
                .put("role", "user")
                .put("content", org.json.JSONArray().put(JSONObject()
                    .put("type", "input_text")
                    .put("text", text))))
        ws.send(ev.toString())
        ws.send(JSONObject().put("type", "response.create").toString())
        _verlauf.value = _verlauf.value + Zeile(false, text)
    }

    fun beenden(grund: Grund = Grund.NUTZER) {
        val liefNoch = laeuft.getAndSet(false)
        runCatching { socket?.close(1000, "fertig") }
        aufraeumen()
        _phase.value = Phase.AUS
        if (liefNoch) beendetCallback?.invoke(grund)
    }

    private fun aufraeumen() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching {
            track?.pause()
            track?.flush()
            track?.stop()
        }
        runCatching { track?.release() }
        track = null
        ausgabe.clear()
        agentSpricht.set(false)
        _pegel.value = 0f
        runCatching { scope?.cancel() }
        scope = null
        socket = null
    }

    private fun baueZuhoerer(context: Context): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _phase.value = Phase.BEREIT
            _startzeit.value = System.currentTimeMillis()
            starteAudio(context, webSocket)

            val begruessung = agentInfo?.optString("greeting").orEmpty()
            val anweisung = if (begruessung.isNotBlank()) {
                "Beginne das Gespraech genau mit diesem Satz: \"$begruessung\""
            } else {
                "Beginne das Gespraech mit einer kurzen Begruessung."
            }
            webSocket.send(
                JSONObject()
                    .put("type", "response.create")
                    .put("response", JSONObject().put("instructions", anweisung))
                    .toString()
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val ev = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (val typ = ev.optString("type")) {
                "response.audio.delta", "response.output_audio.delta" -> {
                    val b64 = ev.optString("delta")
                    if (b64.isNotBlank()) {
                        agentSpricht.set(true)
                        _phase.value = Phase.AGENT_SPRICHT
                        runCatching { ausgabe.add(Base64.decode(b64, Base64.DEFAULT)) }
                    }
                }
                "response.audio.done", "response.output_audio.done" -> {
                    agentSpricht.set(false)
                    if (_phase.value == Phase.AGENT_SPRICHT) _phase.value = Phase.BEREIT
                }
                "response.audio_transcript.delta", "response.output_audio_transcript.delta" -> {
                    agentTextPuffer.append(ev.optString("delta"))
                }
                "response.audio_transcript.done", "response.output_audio_transcript.done" -> {
                    val ausEvent = ev.optString("transcript")
                    val fertig = if (ausEvent.isNotBlank()) ausEvent else agentTextPuffer.toString()
                    agentTextPuffer = StringBuilder()
                    if (fertig.isNotBlank()) {
                        _verlauf.value = _verlauf.value + Zeile(true, fertig.trim())
                    }
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val t = ev.optString("transcript").trim()
                    if (t.isNotBlank()) _verlauf.value = _verlauf.value + Zeile(false, t)
                }
                "input_audio_buffer.speech_started" -> {
                    _phase.value = Phase.KUNDE_SPRICHT
                    if (agentSpricht.get()) {
                        ausgabe.clear()
                        runCatching {
                            track?.pause()
                            track?.flush()
                            track?.play()
                        }
                        agentSpricht.set(false)
                    }
                }
                "input_audio_buffer.speech_stopped" -> {
                    if (_phase.value == Phase.KUNDE_SPRICHT) _phase.value = Phase.BEREIT
                }
                "response.function_call_arguments.done" -> {
                    val name = ev.optString("name")
                    val callId = ev.optString("call_id")
                    val args = runCatching { JSONObject(ev.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                    if (name.isNotBlank()) {
                        _werkzeuge.value = _werkzeuge.value + Werkzeugaufruf(name, args)
                        val ergebnis = JSONObject().put("ok", true)
                        webSocket.send(
                            JSONObject()
                                .put("type", "conversation.item.create")
                                .put("item", JSONObject()
                                    .put("type", "function_call_output")
                                    .put("call_id", callId)
                                    .put("output", ergebnis.toString()))
                                .toString()
                        )
                        webSocket.send(JSONObject().put("type", "response.create").toString())
                    }
                }
                "error" -> {
                    val msg = ev.optJSONObject("error")?.optString("message") ?: "Fehler"
                    _fehler.value = msg
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _fehler.value = t.message ?: "Verbindung abgebrochen"
            _phase.value = Phase.FEHLER
            val liefNoch = laeuft.getAndSet(false)
            aufraeumen()
            if (liefNoch) beendetCallback?.invoke(Grund.FEHLER)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (laeuft.get()) beenden(Grund.AGENT)
        }
    }

    private fun starteAudio(context: Context, ws: WebSocket) {
        val s = scope ?: return
        val minAus = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minAus * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        runCatching { track?.play() }

        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
        }

        s.launch {
            while (laeuft.get()) {
                val stueck = ausgabe.poll()
                if (stueck == null) {
                    delay(10)
                    continue
                }
                runCatching { track?.write(stueck, 0, stueck.size) }
            }
        }

        val minRein = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        recorder = runCatching {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRein * 4
            )
        }.getOrNull()
        runCatching { recorder?.startRecording() }

        s.launch {
            val puffer = ByteArray(1920)
            while (laeuft.get()) {
                val gelesen = recorder?.read(puffer, 0, puffer.size) ?: -1
                if (gelesen <= 0) {
                    delay(10)
                    continue
                }
                var summe = 0L
                var i = 0
                while (i < gelesen - 1) {
                    val s16 = (puffer[i].toInt() and 0xFF) or (puffer[i + 1].toInt() shl 8)
                    summe += kotlin.math.abs(s16.toShort().toInt())
                    i += 2
                }
                val schnitt = if (gelesen > 0) summe / (gelesen / 2) else 0
                _pegel.value = (schnitt / 12000f).coerceIn(0f, 1f)

                val b64 = Base64.encodeToString(puffer, 0, gelesen, Base64.NO_WRAP)
                val msg = JSONObject()
                    .put("type", "input_audio_buffer.append")
                    .put("audio", b64)
                ws.send(msg.toString())
            }
        }
    }
}
