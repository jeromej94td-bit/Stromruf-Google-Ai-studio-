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
    // Tiny q5_1 is the reliable fallback for the in-app worker: it stays multilingual
    // (including German) but avoids a first Whisper pass that can appear frozen on a phone.
    const val MODEL_SIZE = 32152673L
    // Official whisper.cpp model fingerprint (SHA-1 published with this model).
    const val MODEL_SHA = "2827a03e495b1ed3048ef28a6a4620537db4ee51"
    const val MODEL_NAME = "ggml-tiny-q5_1.bin"
    private val LEGACY_MODEL_NAMES = listOf("ggml-small-q5_1.bin", "ggml-base-q5_1.bin")
    const val QUEUE = "smartcalls-local-german"
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
        val saved = readJson(File(directory(context), "model.json")).optString("status", "Modell noch nicht geladen")
        return if (!ready(context) && saved.startsWith("Bereit"))
            "Schnelles Deutsch-Modell muss einmal geladen werden"
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

    @Synchronized fun request(context: Context, file: File) {
        val audio = recording(context, file.name)
        val previous = read(context, file.name)
        val unchanged = previous.optLong("size") == audio.length() &&
            previous.optLong("modified") == audio.lastModified()
        if (unchanged && previous.optString("state") == "done") return
        val value = if (unchanged) previous else JSONObject()
            .put("file", audio.name).put("size", audio.length()).put("modified", audio.lastModified())
            .put("nextMs", 0L).put("text", "")
        value.put("state", "pending").put("message", if (ready(context)) "Wartet auf Verarbeitung" else "Wartet auf Modelldownload")
        write(context, file.name, value)
        if (ready(context)) enqueue(context, file.name) else download(context)
    }

    fun enqueue(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<WhisperWorker>()
            .setInputData(workDataOf("file" to name))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .build()
        // One global chain bounds CPU/RAM. Workers also skip already completed files.
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun enqueueNoteSync(context: Context, name: String) {
        val request = OneTimeWorkRequestBuilder<SmartCallNoteWorker>().setInputData(workDataOf("file" to name)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$NOTE_SYNC_QUEUE-${id(name)}", ExistingWorkPolicy.KEEP, request)
    }

    fun download(context: Context) {
        if (ready(context)) { resume(context); return }
        // Reclaim older, slower models before downloading the fast fallback.
        LEGACY_MODEL_NAMES.forEach { File(directory(context), it).delete() }
        modelStatus(context, "Schnelles Deutsch-Modell wird vorbereitet …")
        WorkManager.getInstance(context).enqueueUniqueWork(DOWNLOAD, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WhisperModelWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build())
    }

    fun resume(context: Context) {
        if (!ready(context)) return
        directory(context).listFiles()?.filter { it.name.startsWith("job_") && it.extension == "json" }
            ?.forEach { file ->
                val value = readJson(file)
                val name = value.optString("file")
                if (name.isNotBlank() && value.optString("state") in setOf("pending", "running")) enqueue(context, name)
                else if (name.isNotBlank() && value.optString("state") == "done" && value.optString("syncState") != "done") enqueueNoteSync(context, name)
            }
    }
}
