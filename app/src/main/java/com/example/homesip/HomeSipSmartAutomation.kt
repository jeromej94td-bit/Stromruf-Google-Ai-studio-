package com.example.homesip

import android.content.Context
import android.util.Log
import com.example.database.AppDatabase
import com.example.database.CallLogEntity
import com.example.recording.RecordingStorageManager
import com.example.repository.StromrufRepository
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
import java.util.UUID

/** Sidecar around the Gold-Master HomeSipTrunk. SIP registration/signaling stays untouched. */
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
    private val recordingPaths = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun isRecording(file: File): Boolean = file.absolutePath in recordingPaths

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
        recordingPaths.add(file.absolutePath)
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
                // Linphone usually emits End before Released. HomeSipTrunk clears activeCall on End,
                // therefore Released can be filtered by the Gold-Master listener. Finalize here as
                // the primary trigger and keep Released below only as a harmless fallback.
                finalizeRecording(context.applicationContext, call)
            }
            Call.State.Released -> finalizeRecording(context.applicationContext, call)
            else -> Unit
        }
    }

    @Synchronized
    fun cancelPrepared() { session = null }

    /** Idempotent because the first caller atomically takes and clears the current session. */
    private fun finalizeRecording(context: Context, call: Call) {
        val finished = synchronized(this) {
            val current = session ?: return
            current.maxDurationSeconds = maxOf(current.maxDurationSeconds, call.duration)
            if (current.recordingStarted && call.isRecording) runCatching { call.stopRecording() }
            session = null
            current
        }

        val wallClockDuration = if (finished.connectedAt > 0L) {
            ((System.currentTimeMillis() - finished.connectedAt) / 1000L).toInt().coerceAtLeast(0)
        } else 0
        val reliableDuration = maxOf(finished.maxDurationSeconds, wallClockDuration)
        val callStartedAt = finished.connectedAt.takeIf { it > 0L } ?: finished.armedAt
        val callEndedAt = System.currentTimeMillis()
        val wasConnected = finished.connectedAt > 0L
        val file = finished.recordingFile

        // Call-log cloud sync must not delay the durable transcription schedule.
        scope.launch {
            runCatching {
                val repository = StromrufRepository(
                    context,
                    AppDatabase.getDatabase(context).stromrufDao()
                )
                repository.insertCallLog(
                    CallLogEntity(
                        id = UUID.nameUUIDFromBytes(
                            "smart-sip:${finished.number}:${finished.armedAt}".toByteArray(Charsets.UTF_8)
                        ).toString(),
                        phone = finished.number,
                        contactName = finished.contactName,
                        outcome = if (wasConnected) "erreicht_interesse" else "nicht_erreicht",
                        note = if (wasConnected)
                            "Smart Call automatisch erfasst (${reliableDuration}s)"
                        else
                            "Smart Call: Kunde nicht erreicht",
                        timestamp = callEndedAt,
                        durationSeconds = reliableDuration.toLong(),
                        callReason = "Smart Call",
                        callType = "smart_call"
                    )
                )
            }.onFailure { Log.w(TAG, "Smart-Call-Status konnte nicht gespeichert werden", it) }
        }

        scope.launch {
            if (file == null) return@launch

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

            if (!file.exists() || file.length() <= 44L || stableChecks < 2) {
                Log.e(TAG, "Smart-Call-Aufnahme ist nach dem Auflegen nicht lesbar: ${file.name}")
                recordingPaths.remove(file.absolutePath)
                return@launch
            }

            val metadata = runCatching {
                com.example.recording.SmartRecordingMetadata.resolve(context, finished.number, finished.contactName)
            }.getOrElse { org.json.JSONObject().put("phone", finished.number).put("contactName", finished.contactName.orEmpty()) }
            val completedFile = com.example.recording.SmartRecordingMetadata.rename(file, metadata)
            recordingPaths.add(completedFile.absolutePath)
            runCatching {
                LocalTranscripts.request(
                    context = context,
                    file = completedFile,
                    callDurationSeconds = reliableDuration,
                    callStartedAt = callStartedAt,
                    callEndedAt = callEndedAt,
                    metadata = metadata
                )
            }.onFailure { Log.e(TAG, "Transkriptions-Workflow konnte nicht automatisch eingeplant werden", it) }
            recordingPaths.remove(file.absolutePath)
            recordingPaths.remove(completedFile.absolutePath)

            runCatching {
                val storage = RecordingStorageManager(context)
                if (storage.isAutoExportEnabled() && storage.getCustomFolderUri() != null) {
                    storage.saveFileToCustomFolder(completedFile)
                }
            }.onFailure { Log.w(TAG, "Aufnahme konnte nicht in den Zielordner exportiert werden", it) }
        }
    }
}
