package com.example.transcription.offline

import android.content.Context
import android.util.Log
import com.example.util.SupabaseAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Smart-Call-specific summary sync.
 * Only summary metadata is sent. WAV and full transcript never leave the device here.
 */
object SmartCallSupabaseSync {
    private const val SUPABASE_URL = "https://yepluyipizbbrgoffqdq.supabase.co"
    private const val SUPABASE_PUBLIC_KEY = "sb_publishable_lat183ycL-tC_3NDwzCHOw_GKmcNWqM"
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun saveSummary(
        context: Context,
        phone: String,
        contactId: String?,
        contactName: String?,
        callStartedAt: Long,
        durationSeconds: Long,
        summary: String,
        sourceFileName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanSummary = summary.trim()
        val cleanSource = sourceFileName.trim()
        if (cleanSummary.isBlank() || cleanSource.isBlank()) return@withContext false

        var token = SupabaseAuthClient.getSessionToken(context) ?: return@withContext false
        if (SupabaseAuthClient.isTokenExpired(token)) {
            token = SupabaseAuthClient.refreshSession(context) ?: return@withContext false
        }
        val uid = SupabaseAuthClient.getTokenClaim(token, "sub") ?: return@withContext false
        val noteId = UUID.nameUUIDFromBytes("$uid:$cleanSource".toByteArray(Charsets.UTF_8)).toString()
        val body = JSONObject().apply {
            put("id", noteId)
            put("user_id", uid)
            put("phone", phone.trim().ifBlank { "Unbekannt" })
            put("contact_id", contactId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("contact_name", contactName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("call_started_at", iso(callStartedAt.takeIf { it > 0L } ?: System.currentTimeMillis()))
            put("duration_seconds", durationSeconds.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            put("summary", cleanSummary)
            put("source_file_name", cleanSource)
            put("updated_at", iso(System.currentTimeMillis()))
        }

        try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/smartcall_notes")
                .header("apikey", SUPABASE_PUBLIC_KEY)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates")
                .post(body.toString().toRequestBody(json))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SmartCallSync", "smartcall_notes ${response.code}: ${response.body?.string()}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SmartCallSync", "Smart-Call-Notiz konnte nicht synchronisiert werden", e)
            false
        }
    }

    private fun iso(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }
}
