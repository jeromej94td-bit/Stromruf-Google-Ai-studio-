package com.example.transcription.offline

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sip.LinphoneSipClient
import com.example.sip.SipState
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
                                    LocalTranscripts.modelStatus(context, "Deutsch-Modell wird geladen: $percent %")
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
            LocalTranscripts.modelStatus(context, "Bereit · Deutsch · offline")
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
        // Smaller base q5_1 model keeps German transcription local but avoids the long
        // first-window latency of the 190 MB small model used by PR #22.
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-base-q5_1.bin"
    }
}

class WhisperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private fun callActive(): Boolean = LinphoneSipClient.getInstance(applicationContext).state.value in
        setOf(SipState.DIALING, SipState.RINGING, SipState.IN_CALL)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val name = inputData.getString("file") ?: return@withContext Result.success()
        val job = LocalTranscripts.read(context, name)
        if (job.optString("state") == "done") return@withContext Result.success()
        if (!LocalTranscripts.ready(context)) {
            job.put("state", "pending").put("message", "Wartet auf schnelles Deutsch-Modell")
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
                        .put("message", "Transkribiert auf dem Handy: $percent % · Abschnitt $section/$sections")
                    LocalTranscripts.write(context, name, job)

                    // Short windows are deliberate: the old 30 s window blocked inside native
                    // Whisper before the first checkpoint, so the UI could sit at 0 % for minutes.
                    val start = (next - 500).coerceAtLeast(0)
                    val end = (next + windowMs).coerceAtMost(wav.durationMs)
                    val pcm = wav.read16k(start, end - start)
                    val rms = if (pcm.isEmpty()) 0.0 else sqrt(pcm.sumOf { (it * it).toDouble() } / pcm.size)
                    val words = if (rms < 0.0001) "" else
                        native!!.transcribe(handle, pcm, (next - start).toInt())
                            ?: if (isStopped || callActive()) {
                                job.put("state", "pending").put("message", "Verarbeitung wird fortgesetzt")
                                LocalTranscripts.write(context, name, job)
                                return@withContext Result.retry()
                            } else throw IOException("Whisper-Verarbeitung fehlgeschlagen")
                    if (words.isNotBlank()) {
                        val seconds = next / 1000
                        text.append("[%02d:%02d] ".format(seconds / 60, seconds % 60))
                            .append(words.trim()).append("\n\n")
                    }
                    next = end
                    val completedPercent = (next * 100 / wav.durationMs).toInt().coerceAtMost(100)
                    job.put("nextMs", next).put("text", text.toString())
                        .put("message", "Transkribiert auf dem Handy: $completedPercent %")
                    LocalTranscripts.write(context, name, job)
                    if (SystemClock.elapsedRealtime() >= deadline && next < wav.durationMs) {
                        job.put("state", "pending").put("message", "Fortsetzung eingeplant")
                        LocalTranscripts.write(context, name, job)
                        LocalTranscripts.enqueue(context, name)
                        return@withContext Result.success()
                    }
                }
                job.put("state", "done").put("message", if (text.isBlank()) "Keine Sprache erkannt" else "Deutsch-Transkript fertig")
                LocalTranscripts.write(context, name, job)
            }
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: LinkageError) {
            job.put("state", "error").put("message", "Whisper fehlt in dieser APK – vollständigen nativen Build installieren")
            LocalTranscripts.write(context, name, job)
            Result.success() // Do not poison the global queue.
        } catch (e: Exception) {
            job.put("state", "error").put("message", e.message ?: "Transkription fehlgeschlagen")
            LocalTranscripts.write(context, name, job)
            Result.success()
        } finally { if (handle != 0L) native?.close(handle) }
    }
}
