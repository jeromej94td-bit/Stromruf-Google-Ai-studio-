package com.example.homesip

import android.content.Context
import android.util.Log
import com.example.recording.RecordingStorageManager
import com.example.transcription.offline.LocalTranscripts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.linphone.core.Call
import org.linphone.core.CallParams
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sidecar around the Gold-Master HomeSipTrunk.
 *
 * It never registers a SIP account, never changes TLS/SRTP/registrar settings and never owns
 * a Linphone Core. It is armed only by the "Smart-Anruf" button and adds recording + post-call
 * processing to that one call. Normal SIP calls are therefore byte-for-byte equivalent in their
 * signaling path when this sidecar is not armed.
 */
object HomeSipSmartAutomation {
    private const val TAG = "SmartCallFlow"
    private const val ARM_TTL_MS = 2 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Session(
        val number: String,
        val contactName: String?,
        val armedAt: Long,
        var recordingFile: File? = null,
        var recordingStarted: Boolean = false,
        var maxDurationSeconds: Int = 0
    )

    @Volatile
    private var session: Session? = null

    @Synchronized
    fun arm(number: String, contactName: String?) {
        val normalized = number.filter { it.isDigit() || it == '+' }
        session = Session(
            number = normalized,
            contactName = contactName?.trim()?.takeIf { it.isNotBlank() },
            armedAt = System.currentTimeMillis()
        )
    }

    /** Called immediately before the existing Gold-Master inviteAddressWithParams(). */
    @Synchronized
    fun prepareCall(context: Context, number: String, params: CallParams) {
        val current = session ?: return
        if (System.currentTimeMillis() - current.armedAt > ARM_TTL_MS) {
            session = null
            return
        }
        val normalized = number.filter { it.isDigit() || it == '+' }
        if (normalized != current.number) return

        val dir = File(context.filesDir, "smart_calls_recordings")
        check(dir.isDirectory || dir.mkdirs()) { "Smart-Call-Aufnahmeordner konnte nicht erstellt werden" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "Call_${normalized}_$timestamp.wav")
        params.recordFile = file.absolutePath
        current.recordingFile = file
    }

    /** Receives call states from the existing HomeSipTrunk listener without changing SIP logic. */
    fun onCallState(context: Context, call: Call, state: Call.State) {
        val current = synchronized(this) {
            session?.also { it.maxDurationSeconds = maxOf(it.maxDurationSeconds, call.duration) }
        } ?: return

        when (state) {
            Call.State.StreamsRunning -> {
                if (current.recordingFile != null && !current.recordingStarted) {
                    runCatching { call.startRecording() }
                        .onFailure { Log.w(TAG, "Linphone-Aufnahme konnte nicht gestartet werden", it) }
                    synchronized(this) {
                        session?.recordingStarted = call.isRecording
                    }
                }
            }
            Call.State.Error, Call.State.End -> {
                if (current.recordingStarted && call.isRecording) {
                    runCatching { call.stopRecording() }
                }
            }
            Call.State.Released -> finalizeRecording(context.applicationContext, call)
            else -> Unit
        }
    }

    @Synchronized
    fun cancelPrepared() {
        session = null
    }

    private fun finalizeRecording(context: Context, call: Call) {
        val finished = synchronized(this) {
            val current = session ?: return
            current.maxDurationSeconds = maxOf(current.maxDurationSeconds, call.duration)
            if (current.recordingStarted && call.isRecording) {
                runCatching { call.stopRecording() }
            }
            session = null
            current
        }

        val file = finished.recordingFile ?: return
        if (!file.exists() || file.length() <= 44L) return

        scope.launch {
            runCatching {
                // LocalTranscripts automatically uses an already installed Whisper model and
                // falls back to downloading the pinned local model only when it is actually absent.
                LocalTranscripts.request(context, file)
            }.onFailure { Log.e(TAG, "Whisper-Workflow konnte nicht eingeplant werden", it) }

            runCatching {
                val storage = RecordingStorageManager(context)
                if (storage.isAutoExportEnabled() && storage.getCustomFolderUri() != null) {
                    storage.saveFileToCustomFolder(file)
                }
            }.onFailure { Log.w(TAG, "Aufnahme konnte nicht in den Zielordner exportiert werden", it) }
        }
    }
}
