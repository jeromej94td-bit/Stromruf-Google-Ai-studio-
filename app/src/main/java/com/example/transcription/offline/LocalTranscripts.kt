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
    // Base q5_1 is intentionally used on-device. The previous small q5_1 model was
    // ~190 MB and could spend a long time in the very first 30 s window, leaving UI at 0 %.
    const val MODEL_SIZE = 59707625L
    const val MODEL_SHA = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
    const val MODEL_NAME = "ggml-base-q5_1.bin"
    private const val LEGACY_MODEL_NAME = "ggml-small-q5_1.bin"
    const val QUEUE = "smartcalls-local-german"
    const val DOWNLOAD = "smartcalls-whisper-model"
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
        if (ready(context)) enqueue(context, file.name)
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

    fun download(context: Context) {
        if (ready(context)) { resume(context); return }
        // Reclaim the obsolete ~190 MB model from PR #22 before downloading the faster model.
        File(directory(context), LEGACY_MODEL_NAME).delete()
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
            }
    }
}
