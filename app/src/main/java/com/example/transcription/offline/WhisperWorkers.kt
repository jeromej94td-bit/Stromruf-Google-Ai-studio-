package com.example.transcription.offline

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.homesip.HomeSipStatus
import com.example.homesip.HomeSipTrunk
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class WhisperModelWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        if (LocalTranscripts.ready(context)) { LocalTranscripts.resume(context); return@withContext Result.success() }
        val partial = File(LocalTranscripts.directory(context), "model.download")
        try {
            if (partial.length() > LocalTranscripts.MODEL_SIZE) partial.delete()
            var offset = partial.length()
            if (offset < LocalTranscripts.MODEL_SIZE) {
                val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS).callTimeout(8, TimeUnit.MINUTES).build()
                val request = Request.Builder()
                    .url(MODEL_URL).header("Accept-Encoding", "identity")
                    .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download: HTTP ${response.code}")
                    if (response.code == 206) {
                        require(response.header("Content-Range")?.startsWith("bytes $offset-") == true) {
                            "Ungültige Download-Fortsetzung"
                        }
                    } else { offset = 0 }
                    val body = response.body ?: throw IOException("Leerer Download")
                    body.byteStream().use { input ->
                        FileOutputStream(partial, offset > 0).use { output ->
                            val buffer = ByteArray(65536)
                            var total = offset
                            var lastPercent = -1
                            while (true) {
                                ensureActive()
                                if (isStopped) throw CancellationException()
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= LocalTranscripts.MODEL_SIZE) { "Modelldatei unerwartet groß" }
                                output.write(buffer, 0, count)
                                val percent = (total * 100 / LocalTranscripts.MODEL_SIZE).toInt()
                                if (percent != lastPercent) {
                                    LocalTranscripts.modelStatus(context, "Schnelles Whisper-Fallback wird geladen: $percent %")
                                    lastPercent = percent
                                }
                            }
                        }
                    }
                }
            }
            require(partial.length() == LocalTranscripts.MODEL_SIZE) { "Download unvollständig" }
            LocalTranscripts.modelStatus(context, "Modelldatei wird geprüft …")
            val digest = MessageDigest.getInstance("SHA-256")
            partial.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) { ensureActive(); val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash != LocalTranscripts.MODEL_SHA) {
                partial.delete()
                throw IllegalArgumentException("Modellprüfung fehlgeschlagen – bitte erneut laden")
            }
            val target = LocalTranscripts.model(context)
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Modell konnte nicht gespeichert werden" }
            LocalTranscripts.modelStatus(context, "Bereit · Whisper Base q5_1 · Deutsch · offline")
            LocalTranscripts.resume(context)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) {
            LocalTranscripts.modelStatus(context, if (runAttemptCount < 5)
                "Download unterbrochen – Fortsetzung bei Internetverbindung"
                else "Download unterbrochen – bitte auf Fortsetzen tippen")
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (e: Exception) {
            LocalTranscripts.modelStatus(context, e.message ?: "Download fehlgeschlagen – erneut versuchen")
            Result.failure()
        }
    }

    companion object {
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-base-q5_1.bin"
    }
}

class WhisperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private fun callActive(): Boolean = HomeSipTrunk.get(applicationContext).state.value.status in
        setOf(HomeSipStatus.DIALING, HomeSipStatus.RINGING, HomeSipStatus.IN_CALL)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val name = inputData.getString("file") ?: return@withContext Result.success()
        val job = LocalTranscripts.read(context, name)
        if (job.optString("state") == "done") return@withContext Result.success()
        if (!LocalTranscripts.ready(context)) {
            job.put("state", "pending").put("message", "Wartet auf schnelles Whisper-Fallback")
            LocalTranscripts.write(context, name, job)
            return@withContext Result.success()
        }
        if (callActive()) return@withContext Result.retry()
        var native: WhisperNative? = null
        var handle = 0L
        try {
            val file = LocalTranscripts.recording(context, name)
            require(job.optLong("size") == file.length() && job.optLong("modified") == file.lastModified()) {
                "Aufnahme wurde geändert – bitte erneut starten"
            }
            PcmWave(file).use { wav ->
                job.put("durationMs", wav.durationMs)
                var next = job.optLong("nextMs").coerceIn(0L, wav.durationMs)
                val text = StringBuilder(job.optString("text"))
                val deadline = SystemClock.elapsedRealtime() + 7 * 60 * 1000
                val windowMs = 10_000L
                native = WhisperNative { isStopped || callActive() }
                handle = native!!.open(LocalTranscripts.model(context).absolutePath)
                check(handle != 0L) { "Whisper konnte das Modell nicht laden" }
                while (next < wav.durationMs) {
                    ensureActive()
                    if (isStopped) throw CancellationException()
                    if (callActive()) {
                        job.put("state", "pending").put("message", "Pausiert während des Telefonats")
                        LocalTranscripts.write(context, name, job)
                        return@withContext Result.retry()
                    }
                    val percent = (next * 100 / wav.durationMs).toInt()
                    val section = (next / windowMs + 1).toInt()
                    val sections = ((wav.durationMs + windowMs - 1) / windowMs).toInt()
                    job.put("state", "running")
                        .put("transcriptionSource", "local-whisper-base-q5_1")
                        .put("message", "Lokales Whisper: $percent % · Abschnitt $section/$sections")
                    LocalTranscripts.write(context, name, job)

                    val start = (next - 500).coerceAtLeast(0)
                    val end = (next + windowMs).coerceAtMost(wav.durationMs)
                    val pcm = wav.read16k(start, end - start)
                    val rms = if (pcm.isEmpty()) 0.0 else sqrt(pcm.sumOf { (it * it).toDouble() } / pcm.size)
                    native!!.beginChunk(45_000L)
                    val words = if (rms < 0.0001) "" else
                        native!!.transcribe(handle, pcm, (next - start).toInt())
                            ?: when {
                                isStopped || callActive() -> {
                                    job.put("state", "pending").put("message", "Verarbeitung wird fortgesetzt")
                                    LocalTranscripts.write(context, name, job)
                                    return@withContext Result.retry()
                                }
                                native!!.didTimeOut() -> {
                                    job.put("warning", "Ein sehr langsamer Audioabschnitt wurde übersprungen")
                                    ""
                                }
                                else -> throw IOException("Whisper-Verarbeitung fehlgeschlagen")
                            }
                    if (words.isNotBlank()) {
                        val seconds = next / 1000
                        text.append("[%02d:%02d] ".format(seconds / 60, seconds % 60))
                            .append(words.trim()).append("\n\n")
                    }
                    next = end
                    job.put("nextMs", next).put("text", text.toString())
                        .put("message", "Lokales Whisper: ${(next * 100 / wav.durationMs).toInt().coerceAtMost(100)} %")
                    LocalTranscripts.write(context, name, job)
                    if (SystemClock.elapsedRealtime() >= deadline && next < wav.durationMs) {
                        job.put("state", "pending").put("message", "Fortsetzung eingeplant")
                        LocalTranscripts.write(context, name, job)
                        LocalTranscripts.enqueue(context, name)
                        return@withContext Result.success()
                    }
                }
                val fallbackSummary = GermanCallSummary.create(text.toString())
                val gemma = if (text.isBlank()) null else LocalGemma.analyze(context, text.toString()).getOrNull()
                val nextAction = gemma?.nextAction.orEmpty()
                val baseSummary = gemma?.summary?.ifBlank { fallbackSummary } ?: fallbackSummary
                val summary = if (nextAction.isBlank()) baseSummary else "$baseSummary\nNächster Schritt: $nextAction"
                job.put("state", "done")
                    .put("summary", summary)
                    .put("customerText", gemma?.customerText.orEmpty())
                    .put("analysisSource", if (gemma != null) "gemma-3n-e2b" else "regelbasiert")
                    .put("nextAction", nextAction)
                    .put("syncState", "pending")
                    .put("message", if (text.isBlank()) "Keine Sprache erkannt" else if (gemma != null) "Lokales Whisper + Gemma fertig" else "Lokales Whisper fertig")
                    .put("syncMessage", "Zusammenfassung wird vorbereitet")
                LocalTranscripts.write(context, name, job)
                LocalTranscripts.enqueueNoteSync(context, name)
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: LinkageError) {
            job.put("state", "error").put("message", "Whisper fehlt in dieser APK – vollständigen nativen Build installieren")
            LocalTranscripts.write(context, name, job)
            Result.success()
        } catch (e: Exception) {
            job.put("state", "error").put("message", e.message ?: "Transkription fehlgeschlagen")
            LocalTranscripts.write(context, name, job)
            Result.success()
        } finally { if (handle != 0L) native?.close(handle) }
    }
}
