package com.example.transcription.offline

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Groq Whisper Large v3 primary, fast local Whisper fallback, then Gemma/Supabase. */
object LocalTranscripts {
    const val MODEL_SIZE = 59707625L
    const val MODEL_SHA = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
    const val MODEL_NAME = "ggml-base-q5_1.bin"
    private val LEGACY_MODEL_NAMES = listOf("ggml-small-q5_1.bin", "ggml-tiny-q5_1.bin")
    const val QUEUE = "smartcalls-local-german"
    const val PRIMARY_QUEUE = "smartcalls-primary-transcription"
    const val DOWNLOAD = "smartcalls-whisper-model"
    const val NOTE_SYNC_QUEUE = "smartcalls-summary-sync"

    fun directory(context: Context) = File(context.noBackupFilesDir, "local_transcription").apply { mkdirs() }
    fun model(context: Context) = File(directory(context), MODEL_NAME)
    fun ready(context: Context) = model(context).let { it.isFile && it.length() == MODEL_SIZE }

    private fun id(name: String) = MessageDigest.getInstance("SHA-256")
        .digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun job(context: Context, name: String) = File(directory(context), "job_${id(name)}.json")

    @Synchronized fun read(context: Context, name: String): JSONObject = readJson(job(context, name))

    private fun readJson(file: File): JSONObject =
        runCatching { JSONObject(AtomicFile(file).openRead().bufferedReader().use { it.readText() }) }
            .getOrElse { JSONObject() }

    @Synchronized fun write(context: Context, name: String, value: JSONObject) {
        atomicWrite(job(context, name), value.toString())
    }

    private fun atomicWrite(file: File, text: String) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try { out.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(out) }
        catch (e: Exception) { atomic.failWrite(out); throw e }
    }

    @Synchronized fun modelStatus(context: Context): String {
        val saved = readJson(File(directory(context), "model.json"))
            .optString("status", "Schnelles lokales Whisper-Fallback noch nicht geladen")
        return if (!ready(context) && saved.startsWith("Bereit"))
            "Schnelles lokales Whisper-Fallback muss einmal geladen werden" else saved
    }

    @Synchronized fun modelStatus(context: Context, message: String) {
        atomicWrite(File(directory(context), "model.json"), JSONObject().put("status", message).toString())
    }

    fun recording(context: Context, name: String): File {
        require(name == File(name).name && name.endsWith(".wav", true)) { "PCM-WAV-Aufnahme erforderlich" }
        val root = File(context.filesDir, "smart_calls_recordings").canonicalFile
        return File(root, name).canonicalFile.also {
            require(it.parentFile == root && it.isFile) { "Aufnahme nicht mehr vorhanden" }
        }
    }

    @Synchronized fun request(
        context: Context,
        file: File,
        callDurationSeconds: Int = 0,
        callStartedAt: Long = 0L,
        callEndedAt: Long = file.lastModified(),
        metadata: JSONObject? = null
    ) {
        val audio = recording(context, file.name)
        val previous = read(context, file.name)
        val unchanged = previous.optLong("size") == audio.length() &&
            previous.optLong("modified") == audio.lastModified()
        if (unchanged && previous.optString("state") in setOf("pending", "running")) {
            resumeJob(context, file.name, previous)
            return
        }
        if (unchanged && previous.optString("state") == "done") {
            if (callDurationSeconds > 0) previous.put("callDurationSeconds", maxOf(previous.optInt("callDurationSeconds"), callDurationSeconds))
            if (callStartedAt > 0L) previous.put("callStartedAt", callStartedAt)
            write(context, file.name, previous)
            if (previous.optString("syncState") !in setOf("done", "local_only")) enqueueNoteSync(context, file.name)
            return
        }
        val value = if (unchanged) previous else JSONObject()
            .put("file", audio.name)
            .put("size", audio.length())
            .put("modified", audio.lastModified())
            .put("nextMs", 0L)
            .put("text", "")
        if (callDurationSeconds > 0) value.put("callDurationSeconds", callDurationSeconds)
        if (callStartedAt > 0L) value.put("callStartedAt", callStartedAt)
        metadata?.keys()?.forEach { key -> value.put(key, metadata.get(key)) }
        value.put("scheduledAt", callEndedAt + 90_000L)
        value.put("state", "pending")
            .put("message", "Automatischer Start frühestens 90 Sekunden nach dem Auflegen")
            .put("transcriptionSource", "pending")
            .put("syncState", "pending")
        write(context, file.name, value)
        enqueuePrimary(context, file.name)
    }

    /** Picks up recordings that already exist but have never been processed or changed since the last job. */
    fun scanExisting(context: Context) {
        val root = File(context.filesDir, "smart_calls_recordings")
        root.listFiles()
            ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) && it.length() > 44L &&
                !com.example.homesip.HomeSipSmartAutomation.isRecording(it) }
            ?.sortedBy { it.lastModified() }
            ?.forEach { audio ->
                val previous = read(context, audio.name)
                val unchanged = previous.optLong("size") == audio.length() &&
                    previous.optLong("modified") == audio.lastModified()
                val state = previous.optString("state")
                when {
                    unchanged && state == "done" -> {
                        if (previous.optString("syncState") !in setOf("done", "local_only")) {
                            enqueueNoteSync(context, audio.name)
                        }
                    }
                    unchanged && state in setOf("pending", "running") -> resumeJob(context, audio.name, previous)
                    state == "error" -> Unit // A failed job needs an explicit retry, not a polling loop.
                    System.currentTimeMillis() - audio.lastModified() >= 90_000L -> request(context, audio)
                }
            }
    }

    fun enqueuePrimary(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<PrimaryTranscriptionWorker>()
            .setInitialDelay((read(context, name).optLong("scheduledAt") - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("file" to name))
            .setConstraints(Constraints.Builder().build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$PRIMARY_QUEUE-${id(name)}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun enqueue(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<WhisperWorker>()
            .setInputData(workDataOf("file" to name))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("$QUEUE-${id(name)}", ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueNoteSync(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<SmartCallNoteWorker>()
            .setInputData(workDataOf("file" to name))
            // Local follow-ups must also be created while offline. The worker retries cloud sync.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$NOTE_SYNC_QUEUE-${id(name)}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun download(context: Context) {
        if (ready(context)) { resume(context); return }
        LEGACY_MODEL_NAMES.forEach { File(directory(context), it).delete() }
        WorkManager.getInstance(context).enqueueUniqueWork(
            DOWNLOAD,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WhisperModelWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build()
        )
    }

    fun resume(context: Context) {
        scanExisting(context)
    }

    private fun resumeJob(context: Context, name: String, value: JSONObject) {
        if (value.optString("transcriptionSource") == "local-whisper-base-q5_1") {
            if (ready(context)) enqueue(context, name) else download(context)
        } else enqueuePrimary(context, name)
    }

    suspend fun enrich(context: Context, name: String, value: JSONObject) {
        if (value.optString("phone").isNotBlank()) return
        val phone = com.example.recording.SmartRecordingMetadata.phone(value, name)
        val metadata = runCatching {
            com.example.recording.SmartRecordingMetadata.resolve(context, phone, null)
        }.getOrElse { JSONObject().put("phone", phone) }
        metadata.keys().forEach { key -> value.put(key, metadata.get(key)) }
        write(context, name, value)
    }
}
