package com.example.transcription.offline

import androidx.annotation.Keep

@Keep
class WhisperNative(private val cancelled: () -> Boolean) {
    companion object { init { System.loadLibrary("stromruf_whisper") } }
    @Keep fun shouldAbort(): Boolean = cancelled()
    external fun open(path: String): Long
    external fun transcribe(handle: Long, pcm: FloatArray, skipBeforeMs: Int): String?
    external fun close(handle: Long)
}
