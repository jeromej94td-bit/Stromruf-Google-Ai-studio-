package com.example.transcription.offline

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

class PcmWaveTest {
    private fun fixture(rate: Int, channels: Int, sample: (Int, Int) -> Int): File {
        fun ByteArrayOutputStream.le(value: Int, bytes: Int) {
            repeat(bytes) { write(value ushr (it * 8) and 255) }
        }
        val body = ByteArrayOutputStream().apply {
            write("WAVEJUNK".toByteArray()); le(3, 4); write(byteArrayOf(1, 2, 3, 0))
            write("fmt ".toByteArray()); le(16, 4); le(1, 2); le(channels, 2)
            le(rate, 4); le(rate * channels * 2, 4); le(channels * 2, 2); le(16, 2)
            write("data".toByteArray()); le(rate * channels * 2, 4)
            repeat(rate) { frame -> repeat(channels) { channel -> le(sample(frame, channel), 2) } }
        }.toByteArray()
        return File.createTempFile("pcm-test", ".wav").apply {
            outputStream().use {
                val header = ByteArrayOutputStream().apply { write("RIFF".toByteArray()); le(body.size, 4) }
                it.write(header.toByteArray()); it.write(body)
            }
        }
    }

    @Test fun telephone8kIsResampledAndInterpolated() {
        val file = fixture(8000, 1) { frame, _ -> if (frame % 2 == 0) 0 else 16384 }
        try {
            PcmWave(file).use {
                assertEquals(1000L, it.durationMs)
                val pcm = it.read16k(0, 1000)
                assertEquals(16000, pcm.size)
                assertEquals(0f, pcm[0], 0.0001f)
                assertEquals(0.25f, pcm[1], 0.0001f)
                assertEquals(0.5f, pcm[2], 0.0001f)
            }
        } finally { file.delete() }
    }

    @Test fun stereo48kIncludesBothSidesAndHandlesOddRiffChunks() {
        val file = fixture(48000, 2) { _, channel -> if (channel == 0) 16384 else 8192 }
        try {
            PcmWave(file).use {
                val pcm = it.read16k(500, 1000)
                assertEquals(8000, pcm.size)
                assertTrue(pcm.all { value -> abs(value - 0.375f) < 0.0001f })
            }
        } finally { file.delete() }
    }

    @Test fun unfinishedRecordingIsRejected() {
        val file = fixture(16000, 1) { _, _ -> 0 }
        try {
            java.io.RandomAccessFile(file, "rw").use { it.setLength(it.length() - 10) }
            assertThrows(IllegalArgumentException::class.java) { PcmWave(file).close() }
        } finally { file.delete() }
    }
}
