package com.example.transcription.offline

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Separate from Gemini's summary cache: a transcript is not a conversation summary. */
object LocalTranscripts {
    // Quality-first local fallback model for German phone calls.
    const val MODEL_SIZE = 190085487L
    const val MODEL_SHA = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"
    const val MODEL_NAME = "ggml-small-q5_1.bin"
    private val LEGACY_MODEL_NAMES = listOf("ggml-tiny-q5_1.bin", "ggml-base-q5_1.bin")
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

    @Synchronized fun read(context: Context, name: String): JSONObject =
        readJson(job(context, name))

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
        val saved = readJson(File(directory(context), "model.json")).optString("status", "Lokales Fallback-Modell noch nicht geladen")
        return if (!ready(context) && saved.startsWith("Bereit"))
            "Lokales Fallback-Modell muss einmal geladen werden"
        else saved
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

    /**
     * Main entry point. Groq Whisper Large v3 is attempted first. If no Groq key is configured
     * or the cloud request fails, PrimaryTranscriptionWorker falls back to local Whisper Small.
     */
    @Synchronized fun request(context: Context, file: File) {
        val audio = recording(context, file.name)
        val previous = read(context, file.name)
        val unchanged = previous.optLong("size") == audio.length() &&
            previous.optLong("modified") == audio.lastModified()
        if (unchanged && previous.optString("state") == "done") return
        val value = if (unchanged) previous else JSONObject()
            .put("file", audio.name).put("size", audio.length()).put("modified", audio.lastModified())
            .put("nextMs", 0L).put("text", "")
        value.put("state", "pending")
            .put("message", "Wartet auf Groq Whisper Large v3")
            .put("transcriptionSource", "pending")
        write(context, file.name, value)
        enqueuePrimary(context, file.name)
    }

    fun enqueuePrimary(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<PrimaryTranscriptionWorker>()
            .setInputData(workDataOf("file" to name))
            .setConstraints(Constraints.Builder().build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$PRIMARY_QUEUE-${id(name)}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Local Whisper fallback only. */
    fun enqueue(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<WhisperWorker>()
            .setInputData(workDataOf("file" to name))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun enqueueNoteSync(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<SmartCallNoteWorker>().setInputData(workDataOf("file" to name)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$NOTE_SYNC_QUEUE-${id(name)}", ExistingWorkPolicy.KEEP, request)
    }

    fun download(context: Context) {
        if (ready(context)) { resume(context); return }
        LEGACY_MODEL_NAMES.forEach { File(directory(context), it).delete() }
        modelStatus(context, "Whisper Small Fallback wird vorbereitet …")
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WhisperModelWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build())
    }

    fun resume(context: Context) {
        directory(context).listFiles()?.filter { it.name.startsWith("job_") && it.extension == "json" }
            ?.forEach { file ->
                val value = readJson(file)
                val name = value.optString("file")
                if (name.isBlank()) return@forEach
                when (value.optString("state")) {
                    "pending", "running" -> enqueuePrimary(context, name)
                    "done" -> if (value.optString("syncState") != "done") enqueueNoteSync(context, name)
                }
            }
    }
}
