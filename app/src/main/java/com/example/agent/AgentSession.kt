package com.example.agent

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Ein Gespräch = eine Session mit vollständig getrenntem Kontext. */
class AgentSession(
    private val context: Context,
    val agent: AgentProfile,
    val mode: SessionMode,
    val direction: String,
    val remoteNumber: String,
    val contactId: String?,
    val contactName: String?,
    val campaignId: String? = null,
    val campaignCallId: String? = null,
    val campaignAttempts: Int = 0,
    val campaignMaxAttempts: Int = 2,
    val campaignAnlass: String? = null,
    private val wissen: String = "",
    private val cfg: RuntimeConfig,
    private val call: SipCall?,
    private val scope: CoroutineScope
) {
    val sessionId: String = UUID.randomUUID().toString()
    val startedAt: Long = System.currentTimeMillis()

    val status = MutableStateFlow(SessionStatus.VERBINDET)
    val transcript = MutableStateFlow<List<TranscriptLine>>(emptyList())
    val latenzen = MutableStateFlow(Latenzen())
    val fehler = MutableStateFlow<String?>(null)
    val nachbearbeitung = MutableStateFlow(false)

    private var job: Job? = null
    private var beendet = false
    private var bekannterText = ""
    private val dir = File(context.cacheDir, "agent_$sessionId").apply { mkdirs() }
    val recordFile = File(dir, "gespraech.wav")

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            try {
                if (cfg.recordingEnabled) runCatching { call?.startRecording() }
                var gruss = agent.greeting
                if (agent.aiDisclosure && cfg.aiDisclosureText.isNotBlank())
                    gruss = "${cfg.aiDisclosureText} $gruss"
                addLine(true, gruss)
                sprich(gruss)

                val ende = startedAt + agent.maxDurationMin * 60_000L
                while (!beendet && System.currentTimeMillis() < ende) {
                    status.value = SessionStatus.HOERT_ZU
                    val gehoert = hoere()
                    if (beendet) break
                    if (gehoert.isBlank()) continue
                    addLine(false, gehoert)

                    status.value = SessionStatus.DENKT
                    val t0 = System.currentTimeMillis()
                    val antwort = Llm.antwort(
                        cfg,
                        PromptBau.system(agent, contactName, wissen, campaignAnlass),
                        transcript.value.map { it.vomAgent to it.text }
                    )
                    latenzen.value = latenzen.value.copy(llmMs = System.currentTimeMillis() - t0)
                    if (beendet) break
                    addLine(true, antwort)
                    sprich(antwort)
                }
            } catch (e: CancellationException) {
                // regulär beendet
            } catch (e: Exception) {
                fehler.value = e.message ?: "Unbekannter Fehler"
                status.value = SessionStatus.FEHLER
            } finally {
                if (status.value != SessionStatus.FEHLER) status.value = SessionStatus.BEENDET
                runCatching { call?.stopRecording() }
                sichern()
            }
        }
    }

    fun stop() {
        if (beendet) return
        beendet = true
        job?.cancel()
        status.value = SessionStatus.BEENDET
        call?.let { SipEngine.beenden(it) }
    }

    private fun sichern() {
        scope.launch(Dispatchers.IO) {
            val endedAt = System.currentTimeMillis()
            AgentBackend.uploadSession(
                context = context, sessionId = sessionId,
                agentId = agent.id, agentName = agent.name, agentRole = agent.role,
                direction = direction, remoteNumber = remoteNumber,
                contactId = contactId, contactName = contactName, campaignId = campaignId,
                startedAt = startedAt, endedAt = endedAt,
                status = status.value.name.lowercase(), summary = null,
                transcript = transcript.value, latenzen = latenzen.value,
                recording = recordFile.takeIf { cfg.recordingEnabled && it.exists() }
            )
            // Nachbearbeitung: Agent pflegt das CRM (ChatGPT ODER Anthropic)
            if (transcript.value.size >= 2 && direction != "geraetetest_stumm") {
                nachbearbeitung.value = true
                runCatching { AgentBackend.runPostCall(context, sessionId) }
                nachbearbeitung.value = false
            }
            // Kampagnenstatus fortschreiben
            campaignCallId?.let {
                val erledigt = (endedAt - startedAt) >= 20_000 && transcript.value.size >= 3
                AgentBackend.finishCampaignCall(
                    context, it, sessionId, erledigt, campaignAttempts, campaignMaxAttempts)
            }
            runCatching { dir.deleteRecursively() }
            AgentRuntime.meldeFertig(this@AgentSession)
        }
    }

    private fun addLine(vomAgent: Boolean, text: String) {
        transcript.value = transcript.value + TranscriptLine(vomAgent, text)
    }

    // ---------------- Sprechen ----------------
    private suspend fun sprich(text: String) {
        status.value = SessionStatus.SPRICHT
        val t0 = System.currentTimeMillis()
        val wav = Tts.speak(cfg, agent.voiceId, agent.voiceSpeed, text,
            File(dir, "tts_${System.nanoTime()}.wav"))
        latenzen.value = latenzen.value.copy(ttsMs = System.currentTimeMillis() - t0)
        if (wav == null) {
            fehler.value = "Sprachausgabe fehlgeschlagen – OpenAI-Schlüssel prüfen"; return
        }
        spielLokal(wav)
        runCatching { wav.delete() }
    }

    private suspend fun spielLokal(wav: File) = withContext(Dispatchers.IO) {
        val bytes = wav.readBytes()
        val pcm = if (bytes.size > 44) bytes.copyOfRange(44, bytes.size) else bytes
        val rate = WavUtil.sampleRate(wav)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size.coerceAtLeast(8192)).build()
        track.play(); track.write(pcm, 0, pcm.size)
        delay(WavUtil.durationMs(wav) + 200)
        runCatching { track.stop(); track.release() }
    }

    // ---------------- Zuhören ----------------
    private suspend fun hoere(): String =
        if (mode == SessionMode.GERAETETEST) hoereMikro() else hoereCall()

    private suspend fun hoereMikro(): String = withContext(Dispatchers.IO) {
        val rate = 16000
        val bufSize = AudioRecord.getMinBufferSize(rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        } catch (e: SecurityException) {
            fehler.value = "Mikrofon-Berechtigung fehlt"; return@withContext ""
        }
        val out = ByteArrayOutputStream(); val buf = ByteArray(bufSize)
        rec.startRecording()
        val bis = System.currentTimeMillis() + agent.listenWindowSec * 1000L
        while (System.currentTimeMillis() < bis && !beendet) {
            val n = rec.read(buf, 0, buf.size); if (n > 0) out.write(buf, 0, n)
        }
        runCatching { rec.stop(); rec.release() }
        val wav = File(dir, "mic_${System.nanoTime()}.wav")
        wav.writeBytes(WavUtil.pcmToWav(out.toByteArray(), rate))
        val t0 = System.currentTimeMillis()
        val text = Stt.transcribe(cfg, wav)
        latenzen.value = latenzen.value.copy(sttMs = System.currentTimeMillis() - t0)
        runCatching { wav.delete() }
        text.trim()
    }

    /** SIP: Mitschnitt + Differenz-Transkription. */
    private suspend fun hoereCall(): String = withContext(Dispatchers.IO) {
        delay(agent.listenWindowSec * 1000L)
        if (!recordFile.exists() || beendet) return@withContext ""
        val schnappschuss = File(dir, "snap.wav")
        runCatching { recordFile.copyTo(schnappschuss, overwrite = true) }
            .getOrElse { return@withContext "" }
        val t0 = System.currentTimeMillis()
        val voll = Stt.transcribe(cfg, schnappschuss)
        latenzen.value = latenzen.value.copy(sttMs = System.currentTimeMillis() - t0)
        if (voll.length <= bekannterText.length) return@withContext ""
        val neu = voll.removePrefix(bekannterText).trim()
        bekannterText = voll
        val letzteAgentZeile = transcript.value.lastOrNull { it.vomAgent }?.text ?: ""
        if (neu.length > 3 && letzteAgentZeile.contains(neu.take(15))) "" else neu
    }
}
