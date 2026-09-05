package com.example.transcription.offline

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

/** Bounded-memory reader for Linphone's PCM16 WAV, including extra RIFF chunks. */
class PcmWave(file: File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    var sampleRate = 0
        private set
    private var channels = 0
    private var dataOffset = 0L
    private var dataBytes = 0L
    val durationMs: Long get() = dataBytes / (channels * 2) * 1000 / sampleRate

    init {
        try {
            require(fourCC() == "RIFF") { "Nur PCM-WAV-Aufnahmen werden unterstützt" }
            uint()
            require(fourCC() == "WAVE") { "Ungültige WAV-Datei" }
            var formatFound = false
            var dataFound = false
            while (input.filePointer + 8 <= input.length()) {
                val id = fourCC()
                val size = uint()
                val start = input.filePointer
                require(size <= input.length() - start) { "Aufnahme noch unvollständig" }
                when (id) {
                    "fmt " -> {
                        require(size >= 16 && ushort() == 1) { "PCM16-WAV erforderlich" }
                        channels = ushort()
                        sampleRate = uint().toInt()
                        uint()
                        val alignment = ushort()
                        require(ushort() == 16 && channels in 1..2 &&
                            sampleRate in 8000..192000 && alignment == channels * 2) {
                            "Dieses WAV-Audioformat wird noch nicht unterstützt"
                        }
                        formatFound = true
                    }
                    "data" -> { dataOffset = start; dataBytes = size; dataFound = true }
                }
                input.seek(start + size + size % 2)
                if (formatFound && dataFound) break
            }
            require(formatFound && dataFound && dataBytes > 0 && dataBytes % (channels * 2) == 0L) {
                "Keine vollständigen Audiodaten vorhanden"
            }
        } catch (e: Exception) { input.close(); throw e }
    }

    fun read16k(startMs: Long, lengthMs: Long): FloatArray {
        require(startMs >= 0 && lengthMs in 1..32000)
        val totalFrames = dataBytes / (channels * 2)
        val first = startMs * sampleRate / 1000
        val frames = min(totalFrames - first, lengthMs * sampleRate / 1000).coerceAtLeast(0).toInt()
        if (frames == 0) return FloatArray(0)
        val bytes = ByteArray(frames * channels * 2)
        input.seek(dataOffset + first * channels * 2)
        input.readFully(bytes)
        fun mono(frame: Int): Float {
            var value = 0f
            for (channel in 0 until channels) {
                val offset = (frame * channels + channel) * 2
                value += (((bytes[offset].toInt() and 255) or (bytes[offset + 1].toInt() shl 8))
                    .toShort().toInt() / 32768f)
            }
            return value / channels
        }
        return FloatArray((frames.toLong() * 16000 / sampleRate).toInt()) { i ->
            val pos = i.toDouble() * sampleRate / 16000
            val left = pos.toInt().coerceAtMost(frames - 1)
            val right = min(left + 1, frames - 1)
            val fraction = (pos - left).toFloat()
            mono(left) * (1 - fraction) + mono(right) * fraction
        }
    }

    private fun fourCC(): String = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
    private fun ushort(): Int = java.lang.Short.reverseBytes(input.readShort()).toInt() and 65535
    private fun uint(): Long = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
    override fun close() = input.close()
}
