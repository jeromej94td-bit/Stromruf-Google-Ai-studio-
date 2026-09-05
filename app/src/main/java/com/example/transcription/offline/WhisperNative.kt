package com.example.transcription.offline

import android.os.SystemClock
import androidx.annotation.Keep

@Keep
class WhisperNative(private val cancelled: () -> Boolean) {
    companion object { init { System.loadLibrary("stromruf_whisper") } }
    @Volatile private var abortAtMs: Long = Long.MAX_VALUE
    @Volatile private var timedOut: Boolean = false
    fun beginChunk(timeoutMs: Long) { timedOut = false; abortAtMs = SystemClock.elapsedRealtime() + timeoutMs }
    fun didTimeOut(): Boolean = timedOut
    @Keep fun shouldAbort(): Boolean {
        if (cancelled()) return true
        if (SystemClock.elapsedRealtime() >= abortAtMs) { timedOut = true; return true }
        return false
    }
    external fun open(path: String): Long
    external fun transcribe(handle: Long, pcm: FloatArray, skipBeforeMs: Int): String?
    external fun close(handle: Long)
}
