package com.example.homesip

import android.content.Context
import android.util.Log
import com.example.recording.RecordingStorageManager
import com.example.transcription.offline.LocalTranscripts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.core.Call
import org.linphone.core.CallParams
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sidecar around the Gold-Master HomeSipTrunk.
 * It does not own SIP registration/signaling and does not alter TLS/SRTP settings.
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
        var connectedAt: Long = 0L,
        var maxDurationSeconds: Int = 0
    )

    @Volatile private var session: Session? = null

    @Synchronized
    fun arm(number: String, contactName: String?) {
        session = Session(
            number = number.filter { it.isDigit() || it == '+' },
            contactName = contactName?.trim()?.takeIf { it.isNotBlank() },
            armedAt = System.currentTimeMillis()
        )
    }

    @Synchronized
    fun prepareCall(context: Context, number: String, params: CallParams) {
        val current = session ?: return
        if (System.currentTimeMillis() - current.armedAt > ARM_TTL_MS) {
            session = null
            return
        }
        if (number.filter { it.isDigit() || it == '+' } != current.number) return

        val dir = File(context.filesDir, "smart_calls_recordings")
        check(dir.isDirectory || dir.mkdirs()) { "Smart-Call-Aufnahmeordner konnte nicht erstellt werden" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "Call_${current.number}_$timestamp.wav")
        params.recordFile = file.absolutePath
        current.recordingFile = file
    }

    fun onCallState(context: Context, call: Call, state: Call.State) {
        val current = synchronized(this) {
            session?.also { it.maxDurationSeconds = maxOf(it.maxDurationSeconds, call.duration) }
        } ?: return

        when (state) {
            Call.State.StreamsRunning -> {
                synchronized(this) {
                    if (session?.connectedAt == 0L) session?.connectedAt = System.currentTimeMillis()
                }
                if (current.recordingFile != null && !current.recordingStarted) {
                    runCatching { call.startRecording() }
                        .onFailure { Log.w(TAG, "Linphone-Aufnahme konnte nicht gestartet werden", it) }
                    synchronized(this) { session?.recordingStarted = call.isRecording }
                }
            }
            Call.State.Error, Call.State.End -> {
                if (current.recordingStarted && call.isRecording) runCatching { call.stopRecording() }
            }
            Call.State.Released -> finalizeRecording(context.applicationContext, call)
            else -> Unit
        }
    }

    @Synchronized
    fun cancelPrepared() { session = null }

    private fun finalizeRecording(context: Context, call: Call) {
        val finished = synchronized(this) {
            val current = session ?: return
            current.maxDurationSeconds = maxOf(current.maxDurationSeconds, call.duration)
            if (current.recordingStarted && call.isRecording) runCatching { call.stopRecording() }
            session = null
            current
        }

        val file = finished.recordingFile ?: return
        val wallClockDuration = if (finished.connectedAt > 0L) {
            ((System.currentTimeMillis() - finished.connectedAt) / 1000L).toInt().coerceAtLeast(0)
        } else 0
        val reliableDuration = maxOf(finished.maxDurationSeconds, wallClockDuration)
        val callStartedAt = finished.connectedAt.takeIf { it > 0L } ?: finished.armedAt

        scope.launch {
            var previousSize = -1L
            var stableChecks = 0
            var attempts = 0
            while (attempts < 12 && stableChecks < 2) {
                if (file.exists() && file.length() > 44L) {
                    val size = file.length()
                    stableChecks = if (size == previousSize) stableChecks + 1 else 0
                    previousSize = size
                }
                attempts++
                if (stableChecks < 2) delay(500)
            }

            if (!file.exists() || file.length() <= 44L) {
                Log.e(TAG, "Smart-Call-Aufnahme ist nach dem Auflegen nicht lesbar: ${file.name}")
                return@launch
            }

            runCatching {
                LocalTranscripts.request(
                    context = context,
                    file = file,
                    callDurationSeconds = reliableDuration,
                    callStartedAt = callStartedAt
                )
            }.onFailure { Log.e(TAG, "Whisper-Workflow konnte nicht automatisch eingeplant werden", it) }

            runCatching {
                val storage = RecordingStorageManager(context)
                if (storage.isAutoExportEnabled() && storage.getCustomFolderUri() != null) {
                    storage.saveFileToCustomFolder(file)
                }
            }.onFailure { Log.w(TAG, "Aufnahme konnte nicht in den Zielordner exportiert werden", it) }
        }
    }
}
