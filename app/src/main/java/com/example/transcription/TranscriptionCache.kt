package com.example.transcription

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local persistent cache for transcription results.
 * Avoids re-calling the API for files that have already been transcribed.
 */
class TranscriptionCache(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, "smartcalls_transcriptions.json")

    companion object {
        private const val TAG = "TranscriptionCache"
    }

    @Synchronized
    fun get(fileName: String): TranscriptionResult? {
        return try {
            val all = loadAllInternal()
            all.find { it.fileName == fileName }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cached transcript for $fileName", e)
            null
        }
    }

    @Synchronized
    fun getAll(): List<TranscriptionResult> {
        return try {
            loadAllInternal()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all cached transcripts", e)
            emptyList()
        }
    }

    @Synchronized
    fun save(result: TranscriptionResult) {
        try {
            val list = loadAllInternal().toMutableList()
            list.removeAll { it.fileName == result.fileName }
            list.add(0, result) // Add at top

            val jsonArray = JSONArray()
            list.forEach { item ->
                val obj = JSONObject().apply {
                    put("fileName", item.fileName)
                    put("timestamp", item.timestamp)
                    put("summary", item.summary)
                    put("fullTranscript", item.fullTranscript)
                    put("rawText", item.rawText)
                    put("estimatedDurationSeconds", item.estimatedDurationSeconds)
                }
                jsonArray.put(obj)
            }

            cacheFile.writeText(jsonArray.toString())
            Log.d(TAG, "Saved transcript to cache: ${result.fileName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transcript to cache", e)
        }
    }

    @Synchronized
    fun delete(fileName: String) {
        try {
            val list = loadAllInternal().filter { it.fileName != fileName }
            val jsonArray = JSONArray()
            list.forEach { item ->
                val obj = JSONObject().apply {
                    put("fileName", item.fileName)
                    put("timestamp", item.timestamp)
                    put("summary", item.summary)
                    put("fullTranscript", item.fullTranscript)
                    put("rawText", item.rawText)
                    put("estimatedDurationSeconds", item.estimatedDurationSeconds)
                }
                jsonArray.put(obj)
            }
            cacheFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transcript from cache", e)
        }
    }

    private fun loadAllInternal(): List<TranscriptionResult> {
        if (!cacheFile.exists()) return emptyList()
        val text = cacheFile.readText().trim()
        if (text.isEmpty()) return emptyList()

        val list = mutableListOf<TranscriptionResult>()
        val array = JSONArray(text)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                TranscriptionResult(
                    fileName = obj.optString("fileName"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    summary = obj.optString("summary"),
                    fullTranscript = obj.optString("fullTranscript"),
                    rawText = obj.optString("rawText"),
                    estimatedDurationSeconds = obj.optLong("estimatedDurationSeconds", 0L)
                )
            )
        }
        return list
    }
}
