package com.example.util

import android.content.Context
import android.util.Log
import com.example.database.ContactEntity
import com.example.database.FollowUpEntity
import com.example.database.CallLogEntity
import com.example.database.AiCallEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseDbClient {
    private const val SUPABASE_URL = "https://yepluyipizbbrgoffqdq.supabase.co"
    private const val SUPABASE_PUBLIC_KEY = "sb_publishable_lat183ycL-tC_3NDwzCHOw_GKmcNWqM"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
        
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private suspend fun getAuthHeaders(context: Context): Map<String, String>? {
        var token = SupabaseAuthClient.getSessionToken(context) ?: return null
        if (SupabaseAuthClient.isTokenExpired(token)) {
            Log.d("SupabaseDbClient", "Token is expired or close to expiry. Refreshing session...")
            val newToken = SupabaseAuthClient.refreshSession(context)
            if (newToken != null) {
                token = newToken
            } else {
                Log.e("SupabaseDbClient", "Failed to refresh token automatically!")
            }
        }
        return mapOf(
            "apikey" to SUPABASE_PUBLIC_KEY,
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json"
        )
    }

    fun getUserIdFromToken(token: String): String? {
        return SupabaseAuthClient.getTokenClaim(token, "sub")
    }

    // --- CONTACTS SYNC ---

    suspend fun fetchContacts(context: Context): List<ContactEntity> = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext emptyList()
        val list = mutableListOf<ContactEntity>()
        try {
            val url = "$SUPABASE_URL/rest/v1/contacts?select=*"
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.get().build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(bodyStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(
                            ContactEntity(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                phone = obj.getString("phone"),
                                company = obj.optString("company", null).takeIf { it != "null" && it.isNotEmpty() },
                                email = obj.optString("email", null).takeIf { it != "null" && it.isNotEmpty() },
                                lastCallAt = if (obj.isNull("last_call_at")) null else obj.getLong("last_call_at"),
                                lastOutcome = obj.optString("last_outcome", null).takeIf { it != "null" && it.isNotEmpty() },
                                isHotBox = obj.optBoolean("is_hot_box", false),
                                hasBeenCalledInHotCycle = obj.optBoolean("has_been_called_in_hot_cycle", false),
                                hotBoxStartHour = if (obj.isNull("hot_box_start_hour")) null else obj.getInt("hot_box_start_hour"),
                                hotBoxEndHour = if (obj.isNull("hot_box_end_hour")) null else obj.getInt("hot_box_end_hour"),
                                hotBoxWeekdays = obj.optString("hot_box_weekdays", null).takeIf { it != "null" && it.isNotEmpty() },
                                callReason = obj.optString("call_reason", null).takeIf { it != "null" && it.isNotEmpty() },
                                hotBoxListName = obj.optString("hot_box_list_name", null).takeIf { it != "null" && it.isNotEmpty() }
                            )
                        )
                    }
                } else {
                    Log.e("SupabaseDbClient", "fetchContacts failure: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchContacts error", e)
        }
        list
    }

    suspend fun upsertContact(context: Context, contact: ContactEntity): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context) ?: return@withContext false
        val headers = getAuthHeaders(context) ?: return@withContext false
        val userId = getUserIdFromToken(token)
        try {
            val url = "$SUPABASE_URL/rest/v1/contacts"
            val jsonObj = JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("phone", contact.phone)
                put("company", contact.company ?: JSONObject.NULL)
                put("email", contact.email ?: JSONObject.NULL)
                put("last_call_at", contact.lastCallAt ?: JSONObject.NULL)
                put("last_outcome", contact.lastOutcome ?: JSONObject.NULL)
                put("is_hot_box", contact.isHotBox)
                put("has_been_called_in_hot_cycle", contact.hasBeenCalledInHotCycle)
                put("hot_box_start_hour", contact.hotBoxStartHour ?: JSONObject.NULL)
                put("hot_box_end_hour", contact.hotBoxEndHour ?: JSONObject.NULL)
                put("hot_box_weekdays", contact.hotBoxWeekdays ?: JSONObject.NULL)
                put("call_reason", contact.callReason ?: JSONObject.NULL)
                put("hot_box_list_name", contact.hotBoxListName ?: JSONObject.NULL)
                if (userId != null) {
                    put("user_id", userId)
                }
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Prefer", "resolution=merge-duplicates")
            
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.e("SupabaseDbClient", "upsertContact failure: ${response.code} ${response.message} - Body: $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "upsertContact error", e)
            false
        }
    }

    suspend fun deleteContact(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext false
        try {
            val url = "$SUPABASE_URL/rest/v1/contacts?id=eq.$id"
            val requestBuilder = Request.Builder().url(url).delete()
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SupabaseDbClient", "deleteContact failure: ${response.code} ${response.message}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "deleteContact error", e)
            false
        }
    }

    // --- FOLLOWUPS SYNC ---

    suspend fun fetchFollowUps(context: Context): List<FollowUpEntity> = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext emptyList()
        val list = mutableListOf<FollowUpEntity>()
        try {
            val url = "$SUPABASE_URL/rest/v1/followups?select=*"
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.get().build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(bodyStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(
                            FollowUpEntity(
                                id = obj.getString("id"),
                                contactId = obj.optString("contact_id", null).takeIf { it != "null" && it.isNotEmpty() },
                                contactName = obj.getString("contact_name"),
                                contactPhone = obj.getString("contact_phone"),
                                note = obj.optString("note", null).takeIf { it != "null" && it.isNotEmpty() },
                                dueAt = obj.getLong("due_at"),
                                isCompleted = obj.optBoolean("is_completed", false),
                                callReason = obj.optString("call_reason", null).takeIf { it != "null" && it.isNotEmpty() }
                            )
                        )
                    }
                } else {
                    Log.e("SupabaseDbClient", "fetchFollowUps failure: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchFollowUps error", e)
        }
        list
    }

    suspend fun upsertFollowUp(context: Context, followup: FollowUpEntity): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context) ?: return@withContext false
        val headers = getAuthHeaders(context) ?: return@withContext false
        val userId = getUserIdFromToken(token)
        try {
            val url = "$SUPABASE_URL/rest/v1/followups"
            val jsonObj = JSONObject().apply {
                put("id", followup.id)
                put("contact_id", followup.contactId ?: JSONObject.NULL)
                put("contact_name", followup.contactName)
                put("contact_phone", followup.contactPhone)
                put("note", followup.note ?: JSONObject.NULL)
                put("due_at", followup.dueAt)
                put("is_completed", followup.isCompleted)
                put("call_reason", followup.callReason ?: JSONObject.NULL)
                if (userId != null) {
                    put("user_id", userId)
                }
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Prefer", "resolution=merge-duplicates")
            
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.e("SupabaseDbClient", "upsertFollowUp failure: ${response.code} ${response.message} - Body: $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "upsertFollowUp error", e)
            false
        }
    }

    suspend fun deleteFollowUp(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext false
        try {
            val url = "$SUPABASE_URL/rest/v1/followups?id=eq.$id"
            val requestBuilder = Request.Builder().url(url).delete()
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SupabaseDbClient", "deleteFollowUp failure: ${response.code} ${response.message}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "deleteFollowUp error", e)
            false
        }
    }

    // --- CALL LOGS SYNC ---

    suspend fun fetchCallLogs(context: Context): List<CallLogEntity> = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext emptyList()
        val list = mutableListOf<CallLogEntity>()
        try {
            val url = "$SUPABASE_URL/rest/v1/call_logs?select=*"
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.get().build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(bodyStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(
                            CallLogEntity(
                                id = obj.getString("id"),
                                phone = obj.getString("phone"),
                                contactName = obj.optString("contact_name", null).takeIf { it != "null" && it.isNotEmpty() },
                                outcome = obj.getString("outcome"),
                                note = obj.optString("note", null).takeIf { it != "null" && it.isNotEmpty() },
                                timestamp = obj.getLong("timestamp"),
                                durationSeconds = obj.optLong("duration_seconds", 0L),
                                callReason = obj.optString("call_reason", null).takeIf { it != "null" && it.isNotEmpty() },
                                callType = obj.optString("call_type", "einwaehlen")
                            )
                        )
                    }
                } else {
                    Log.e("SupabaseDbClient", "fetchCallLogs failure: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchCallLogs error", e)
        }
        list
    }

    suspend fun upsertCallLog(context: Context, callLog: CallLogEntity): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context) ?: return@withContext false
        val headers = getAuthHeaders(context) ?: return@withContext false
        val userId = getUserIdFromToken(token)
        try {
            val url = "$SUPABASE_URL/rest/v1/call_logs"
            val jsonObj = JSONObject().apply {
                put("id", callLog.id)
                put("phone", callLog.phone)
                put("contact_name", callLog.contactName ?: JSONObject.NULL)
                put("outcome", callLog.outcome)
                put("note", callLog.note ?: JSONObject.NULL)
                put("timestamp", callLog.timestamp)
                put("duration_seconds", callLog.durationSeconds)
                put("call_reason", callLog.callReason ?: JSONObject.NULL)
                put("call_type", callLog.callType)
                if (userId != null) {
                    put("user_id", userId)
                }
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Prefer", "resolution=merge-duplicates")
            
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.e("SupabaseDbClient", "upsertCallLog failure: ${response.code} ${response.message} - Body: $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "upsertCallLog error", e)
            false
        }
    }

    // --- AI CALLS SYNC ---

    suspend fun fetchAiCalls(context: Context): List<AiCallEntity> = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext emptyList()
        val list = mutableListOf<AiCallEntity>()
        try {
            val url = "$SUPABASE_URL/rest/v1/ai_calls?select=*"
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.get().build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(bodyStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(
                            AiCallEntity(
                                id = obj.getString("id"),
                                phone = obj.getString("phone"),
                                contactName = obj.optString("contact_name", null).takeIf { it != "null" && it.isNotEmpty() },
                                timestamp = obj.getLong("timestamp"),
                                audioFilePath = obj.optString("audio_file_path", null).takeIf { it != "null" && it.isNotEmpty() },
                                transcript = obj.getString("transcript"),
                                durationSeconds = obj.optLong("duration_seconds", 0L),
                                notes = obj.optString("notes", "")
                            )
                        )
                    }
                } else {
                    Log.e("SupabaseDbClient", "fetchAiCalls failure: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchAiCalls error", e)
        }
        list
    }

    suspend fun upsertAiCall(context: Context, aiCall: AiCallEntity): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context) ?: return@withContext false
        val headers = getAuthHeaders(context) ?: return@withContext false
        val userId = getUserIdFromToken(token)
        try {
            val url = "$SUPABASE_URL/rest/v1/ai_calls"
            val jsonObj = JSONObject().apply {
                put("id", aiCall.id)
                put("phone", aiCall.phone)
                put("contact_name", aiCall.contactName ?: JSONObject.NULL)
                put("timestamp", aiCall.timestamp)
                put("audio_file_path", aiCall.audioFilePath ?: JSONObject.NULL)
                put("transcript", aiCall.transcript)
                put("duration_seconds", aiCall.durationSeconds)
                put("notes", aiCall.notes)
                if (userId != null) {
                    put("user_id", userId)
                }
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Prefer", "resolution=merge-duplicates")
            
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.e("SupabaseDbClient", "upsertAiCall failure: ${response.code} ${response.message} - Body: $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "upsertAiCall error", e)
            false
        }
    }

    suspend fun deleteAiCall(context: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext false
        try {
            val url = "$SUPABASE_URL/rest/v1/ai_calls?id=eq.$id"
            val requestBuilder = Request.Builder().url(url).delete()
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SupabaseDbClient", "deleteAiCall failure: ${response.code} ${response.message}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "deleteAiCall error", e)
            false
        }
    }

    // --- CUSTOMER MESSAGES (Notizen) SYNC ---

    suspend fun fetchCustomerMessages(context: Context): List<com.example.database.CustomerMessageEntity> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<com.example.database.CustomerMessageEntity>()
            try {
                val jsonArray = fetchTableRows(context, "customer_messages")
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        com.example.database.CustomerMessageEntity(
                            id = obj.getString("id"),
                            contactId = obj.optString("contact_id", null).takeIf { it != "null" && it.isNotEmpty() },
                            contactName = obj.optString("contact_name", "Unbekannter Kunde"),
                            contactEmail = obj.optString("contact_email", null).takeIf { it != "null" && it.isNotEmpty() },
                            contactPhone = obj.optString("contact_phone", null).takeIf { it != "null" && it.isNotEmpty() },
                            rawNote = obj.optString("raw_note", ""),
                            transcript = obj.optString("transcript", null).takeIf { it != "null" && it.isNotEmpty() },
                            subject = obj.optString("subject", ""),
                            body = obj.optString("body", ""),
                            provider = obj.optString("provider", null).takeIf { it != "null" && it.isNotEmpty() },
                            status = obj.optString("status", "draft"),
                            createdAt = obj.optLong("created_at_ms", 0L),
                            sentAt = if (obj.isNull("sent_at_ms")) null else obj.getLong("sent_at_ms"),
                            errorMessage = obj.optString("error_message", null).takeIf { it != "null" && it.isNotEmpty() }
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SupabaseDbClient", "fetchCustomerMessages mapping error", e)
            }
            list
        }

    suspend fun upsertCustomerMessage(context: Context, message: com.example.database.CustomerMessageEntity): Boolean {
        val payload = JSONObject().apply {
            put("id", message.id)
            put("contact_id", message.contactId ?: JSONObject.NULL)
            put("contact_name", message.contactName)
            put("contact_email", message.contactEmail ?: JSONObject.NULL)
            put("contact_phone", message.contactPhone ?: JSONObject.NULL)
            put("raw_note", message.rawNote)
            put("transcript", message.transcript ?: JSONObject.NULL)
            put("subject", message.subject)
            put("body", message.body)
            put("provider", message.provider ?: JSONObject.NULL)
            put("status", message.status)
            put("created_at_ms", message.createdAt)
            put("sent_at_ms", message.sentAt ?: JSONObject.NULL)
            put("error_message", message.errorMessage ?: JSONObject.NULL)
        }
        return upsertTableRow(context, "customer_messages", payload)
    }

    // --- GENERIC TABLE HELPERS ---

    suspend fun fetchTableRows(context: Context, table: String): JSONArray = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext JSONArray()
        try {
            val url = "$SUPABASE_URL/rest/v1/$table?select=*"
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.get().build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: "[]"
                    return@withContext JSONArray(bodyStr)
                } else {
                    Log.e("SupabaseDbClient", "fetchTableRows failure for table $table: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchTableRows error for table $table", e)
        }
        JSONArray()
    }

    suspend fun upsertTableRow(context: Context, table: String, payload: JSONObject): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context) ?: return@withContext false
        val headers = getAuthHeaders(context) ?: return@withContext false
        val userId = getUserIdFromToken(token)
        try {
            val url = "$SUPABASE_URL/rest/v1/$table"
            if (userId != null && !payload.has("user_id")) {
                payload.put("user_id", userId)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Prefer", "resolution=merge-duplicates")
            
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.e("SupabaseDbClient", "upsertTableRow failure for table $table: ${response.code} ${response.message} - Body: $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "upsertTableRow error for table $table", e)
            false
        }
    }

    suspend fun deleteTableRow(context: Context, table: String, id: String): Boolean = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext false
        try {
            val url = "$SUPABASE_URL/rest/v1/$table?id=eq.$id"
            val requestBuilder = Request.Builder().url(url).delete()
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SupabaseDbClient", "deleteTableRow failure for table $table: ${response.code} ${response.message}")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "deleteTableRow error for table $table", e)
            false
        }
    }

    // --- ABSCHLUSS ANNAHMEN SYNC ---

    suspend fun fetchAnnahmen(context: Context): List<com.example.database.AnnahmeEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.database.AnnahmeEntity>()
        try {
            val jsonArray = fetchTableRows(context, "abschluss_annahmen")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(SupabaseSyncPayloads.annahmeFromJson(obj))
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchAnnahmen mapping error", e)
        }
        list
    }

    suspend fun upsertAnnahme(context: Context, item: com.example.database.AnnahmeEntity): Boolean {
        val payload = SupabaseSyncPayloads.annahmeToJson(item, null)
        return upsertTableRow(context, "abschluss_annahmen", payload)
    }

    suspend fun deleteAnnahme(context: Context, id: String): Boolean {
        return deleteTableRow(context, "abschluss_annahmen", id)
    }

    // --- PROMISED ANNAHMEN SYNC ---

    suspend fun fetchPromisedAnnahmen(context: Context): List<com.example.database.PromisedAnnahmeEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.database.PromisedAnnahmeEntity>()
        try {
            val jsonArray = fetchTableRows(context, "promised_annahmen")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(SupabaseSyncPayloads.promisedAnnahmeFromJson(obj))
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchPromisedAnnahmen mapping error", e)
        }
        list
    }

    suspend fun upsertPromisedAnnahme(context: Context, item: com.example.database.PromisedAnnahmeEntity): Boolean {
        val payload = SupabaseSyncPayloads.promisedAnnahmeToJson(item, null)
        return upsertTableRow(context, "promised_annahmen", payload)
    }

    suspend fun deletePromisedAnnahme(context: Context, id: String): Boolean {
        return deleteTableRow(context, "promised_annahmen", id)
    }

    // --- NEUKUNDEN SYNC ---

    suspend fun fetchNeukunden(context: Context): List<com.example.database.NeukundeEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.database.NeukundeEntity>()
        try {
            val jsonArray = fetchTableRows(context, "neukunden")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(SupabaseSyncPayloads.neukundeFromJson(obj))
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchNeukunden mapping error", e)
        }
        list
    }

    suspend fun upsertNeukunde(context: Context, item: com.example.database.NeukundeEntity): Boolean {
        val payload = SupabaseSyncPayloads.neukundeToJson(item, null)
        return upsertTableRow(context, "neukunden", payload)
    }

    suspend fun deleteNeukunde(context: Context, id: String): Boolean {
        return deleteTableRow(context, "neukunden", id)
    }

    // --- HEISSE ANGEBOTE SYNC ---

    suspend fun fetchHeisseAngebote(context: Context): List<com.example.database.HeissAngebotEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<com.example.database.HeissAngebotEntity>()
        try {
            val jsonArray = fetchTableRows(context, "heisse_angebote")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(SupabaseSyncPayloads.heissAngebotFromJson(obj))
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "fetchHeisseAngebote mapping error", e)
        }
        list
    }

    suspend fun upsertHeissAngebot(context: Context, item: com.example.database.HeissAngebotEntity): Boolean {
        val payload = SupabaseSyncPayloads.heissAngebotToJson(item, null)
        return upsertTableRow(context, "heisse_angebote", payload)
    }

    suspend fun deleteHeissAngebot(context: Context, id: String): Boolean {
        return deleteTableRow(context, "heisse_angebote", id)
    }

    // --- BULK FULL SYNC ENGINE ---

    suspend fun syncAll(
        context: Context,
        localDao: com.example.database.StromrufDao
    ): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context)
            ?: return@withContext false

        val userId = getUserIdFromToken(token)
            ?: return@withContext false

        val preferences = context.getSharedPreferences(
            "supabase_data_migration",
            Context.MODE_PRIVATE
        )

        val migrationKey = "local_data_uploaded_$userId"

        try {
            if (!preferences.getBoolean(migrationKey, false)) {
                Log.d("SupabaseDbClient", "Performing once-off local data upload/migration to Supabase for user $userId...")
                val contactsUploaded = localDao.getAllContactsList()
                    .map { upsertContact(context, it) }
                    .all { it }

                val followupsUploaded = localDao.getActiveFollowUpsList()
                    .map { upsertFollowUp(context, it) }
                    .all { it }

                val callLogsUploaded = localDao.getAllCallLogsList()
                    .map { upsertCallLog(context, it) }
                    .all { it }

                val aiCallsUploaded = localDao.getAllAiCallsList()
                    .map { upsertAiCall(context, it) }
                    .all { it }

                val annahmenUploaded = localDao.getAllAnnahmenList()
                    .map { upsertAnnahme(context, it) }
                    .all { it }

                val promisedUploaded = localDao.getPromisedAnnahmenList()
                    .map { upsertPromisedAnnahme(context, it) }
                    .all { it }

                val neukundenUploaded = localDao.getAllNeukundenList()
                    .map { upsertNeukunde(context, it) }
                    .all { it }

                val heisseUploaded = localDao.getAllHeisseAngeboteList()
                    .map { upsertHeissAngebot(context, it) }
                    .all { it }

                // Notizen/Kundennachrichten hochladen (Fehler hier sind nicht kritisch)
                try {
                    localDao.getAllCustomerMessagesList().forEach { upsertCustomerMessage(context, it) }
                } catch (e: Exception) {
                    Log.w("SupabaseDbClient", "customer_messages migration skipped: ${e.message}")
                }

                if (
                    !contactsUploaded ||
                    !followupsUploaded ||
                    !callLogsUploaded ||
                    !aiCallsUploaded ||
                    !annahmenUploaded ||
                    !promisedUploaded ||
                    !neukundenUploaded ||
                    !heisseUploaded
                ) {
                    Log.e("SupabaseDbClient", "One or more data migrations failed. Aborting full sync.")
                    return@withContext false
                }

                preferences.edit()
                    .putBoolean(migrationKey, true)
                    .apply()
                Log.d("SupabaseDbClient", "Once-off data migration completed successfully.")
            }

            // 2. Upload any local contacts before downloading from Supabase
            runCatching {
                localDao.getAllContactsList().forEach { contact -> upsertContact(context, contact) }
            }

            // 3. Fetch from Supabase
            Log.d("SupabaseDbClient", "Downloading latest from Supabase...")
            val remoteContacts = fetchContacts(context)
            val remoteFollowups = fetchFollowUps(context)
            val remoteCallLogs = fetchCallLogs(context)
            val remoteAiCalls = fetchAiCalls(context)
            val remoteAnnahmen = fetchAnnahmen(context)
            val remotePromised = fetchPromisedAnnahmen(context)
            val remoteNeukunden = fetchNeukunden(context)
            val remoteHeisse = fetchHeisseAngebote(context)
            val remoteMessages = fetchCustomerMessages(context)

            // 4. Insert into Local Database (acts as local cache)
            remoteContacts.forEach { localDao.insertContact(it) }
            // Note: We do NOT delete local contacts that are not yet on remote to prevent data loss.
            
            remoteFollowups.forEach { localDao.insertFollowUp(it) }
            if (remoteFollowups.isNotEmpty()) localDao.deleteFollowUpsNotIn(remoteFollowups.map { it.id })
            
            // Push unsynced local call logs up before applying remote deletion
            runCatching {
                localDao.getAllCallLogsList().forEach { log -> upsertCallLog(context, log) }
            }
            remoteCallLogs.forEach { localDao.insertCallLog(it) }
            if (remoteCallLogs.isNotEmpty()) localDao.deleteCallLogsNotIn(remoteCallLogs.map { it.id })
            
            remoteAiCalls.forEach { localDao.insertAiCall(it) }
            if (remoteAiCalls.isNotEmpty()) localDao.deleteAiCallsNotIn(remoteAiCalls.map { it.id })
            
            remoteAnnahmen.forEach { localDao.insertAnnahme(it) }
            if (remoteAnnahmen.isNotEmpty()) localDao.deleteAnnahmenNotIn(remoteAnnahmen.map { it.id })
            
            remotePromised.forEach { localDao.insertPromisedAnnahme(it) }
            if (remotePromised.isNotEmpty()) localDao.deletePromisedAnnahmenNotIn(remotePromised.map { it.id })
            
            remoteNeukunden.forEach { localDao.insertNeukunde(it) }
            if (remoteNeukunden.isNotEmpty()) localDao.deleteNeukundenNotIn(remoteNeukunden.map { it.id })
            
            remoteHeisse.forEach { localDao.insertHeissAngebot(it) }
            if (remoteHeisse.isNotEmpty()) localDao.deleteHeisseAngeboteNotIn(remoteHeisse.map { it.id })
            
            remoteMessages.forEach { localDao.insertCustomerMessage(it) }
            if (remoteMessages.isNotEmpty()) localDao.deleteCustomerMessagesNotIn(remoteMessages.map { it.id })
            
            Log.d("SupabaseDbClient", "Sync complete! Loaded ${remoteContacts.size} contacts, ${remoteFollowups.size} followups, ${remoteCallLogs.size} logs, ${remoteAiCalls.size} AI calls from cloud.")
            true
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "SyncAll error", e)
            false
        }
    }

    // Keep compatibility mapping to syncAll
    suspend fun syncAllDown(
        context: Context,
        localDao: com.example.database.StromrufDao
    ): Boolean {
        return syncAll(context, localDao)
    }

    suspend fun refreshLocalCache(
        context: Context,
        localDao: com.example.database.StromrufDao
    ): Boolean = withContext(Dispatchers.IO) {
        if (getAuthHeaders(context) == null) {
            return@withContext false
        }

        try {
            runCatching {
                localDao.getAllContactsList().forEach { contact -> upsertContact(context, contact) }
            }
            val remoteContacts = fetchContacts(context)
            remoteContacts.forEach { localDao.insertContact(it) }

            val remoteFollowups = fetchFollowUps(context)
            remoteFollowups.forEach { localDao.insertFollowUp(it) }
            if (remoteFollowups.isNotEmpty()) localDao.deleteFollowUpsNotIn(remoteFollowups.map { it.id })

            runCatching {
                localDao.getAllCallLogsList().forEach { log -> upsertCallLog(context, log) }
            }
            val remoteCallLogs = fetchCallLogs(context)
            remoteCallLogs.forEach { localDao.insertCallLog(it) }
            if (remoteCallLogs.isNotEmpty()) localDao.deleteCallLogsNotIn(remoteCallLogs.map { it.id })

            val remoteAiCalls = fetchAiCalls(context)
            remoteAiCalls.forEach { localDao.insertAiCall(it) }
            if (remoteAiCalls.isNotEmpty()) localDao.deleteAiCallsNotIn(remoteAiCalls.map { it.id })

            val remoteAnnahmen = fetchAnnahmen(context)
            remoteAnnahmen.forEach { localDao.insertAnnahme(it) }
            if (remoteAnnahmen.isNotEmpty()) localDao.deleteAnnahmenNotIn(remoteAnnahmen.map { it.id })

            val remotePromised = fetchPromisedAnnahmen(context)
            remotePromised.forEach { localDao.insertPromisedAnnahme(it) }
            if (remotePromised.isNotEmpty()) localDao.deletePromisedAnnahmenNotIn(remotePromised.map { it.id })

            val remoteNeukunden = fetchNeukunden(context)
            remoteNeukunden.forEach { localDao.insertNeukunde(it) }
            if (remoteNeukunden.isNotEmpty()) localDao.deleteNeukundenNotIn(remoteNeukunden.map { it.id })

            val remoteHeisse = fetchHeisseAngebote(context)
            remoteHeisse.forEach { localDao.insertHeissAngebot(it) }
            if (remoteHeisse.isNotEmpty()) localDao.deleteHeisseAngeboteNotIn(remoteHeisse.map { it.id })

            val remoteMessages = fetchCustomerMessages(context)
            remoteMessages.forEach { localDao.insertCustomerMessage(it) }
            if (remoteMessages.isNotEmpty()) localDao.deleteCustomerMessagesNotIn(remoteMessages.map { it.id })
            
            true
        } catch (e: Exception) {
            Log.e(
                "SupabaseDbClient",
                "Cloud cache refresh failed",
                e
            )
            false
        }
    }

    // --- HOTBOX CAMPAIGN LISTS SYNC ---

    suspend fun fetchHotBoxLists(
        context: Context
    ): List<String> = withContext(Dispatchers.IO) {

        val headers =
            getAuthHeaders(context)
                ?: return@withContext emptyList()

        val result = linkedSetOf<String>()

        try {

            val url =
                "$SUPABASE_URL/rest/v1/hot_box_lists?select=name&order=created_at.asc"

            val requestBuilder =
                Request.Builder()
                    .url(url)
                    .get()

            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            client.newCall(requestBuilder.build())
                .execute()
                .use { response ->

                    if (!response.isSuccessful) {
                        Log.e(
                            "SupabaseDbClient",
                            "fetchHotBoxLists: HTTP ${response.code} ${response.message}"
                        )

                        return@withContext emptyList()
                    }

                    val json =
                        JSONArray(
                            response.body?.string() ?: "[]"
                        )

                    for (i in 0 until json.length()) {

                        val name =
                            json.getJSONObject(i)
                                .optString("name")
                                .trim()

                        if (name.isNotEmpty()) {
                            result.add(name)
                        }
                    }
                }

        } catch (e: Exception) {

            Log.e(
                "SupabaseDbClient",
                "fetchHotBoxLists error",
                e
            )
        }

        result.toList()
    }

    suspend fun upsertHotBoxList(context: Context, name: String): Boolean = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context) ?: return@withContext false
        val headers = getAuthHeaders(context) ?: return@withContext false
        val userId = getUserIdFromToken(token) ?: return@withContext false
        try {
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) return@withContext false
            val id = UUID.nameUUIDFromBytes(
                "$userId:$trimmedName".toByteArray(StandardCharsets.UTF_8)
            ).toString()

            val url = "$SUPABASE_URL/rest/v1/hot_box_lists"
            val jsonObj = JSONObject().apply {
                put("id", id)
                put("user_id", userId)
                put("name", trimmedName)
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonObj.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Prefer", "resolution=merge-duplicates")
            
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.e("SupabaseDbClient", "upsertHotBoxList failure: ${response.code} ${response.message} - Body: $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "upsertHotBoxList error", e)
            false
        }
    }

    suspend fun deleteHotBoxList(context: Context, name: String): Boolean = withContext(Dispatchers.IO) {
        val headers = getAuthHeaders(context) ?: return@withContext false
        try {
            val baseUrl = "$SUPABASE_URL/rest/v1/hot_box_lists"
            val httpUrl = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("name", "eq.${name.trim()}")
                .build()

            val requestBuilder = Request.Builder().url(httpUrl).delete()
            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    Log.e("SupabaseDbClient", "deleteHotBoxList failure: ${response.code} ${response.message} - Body: $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "deleteHotBoxList error", e)
            false
        }
    }

    suspend fun replaceHotBoxLists(context: Context, localNames: Set<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            val remoteNames = fetchHotBoxLists(context).map { it.trim() }.toSet()
            val trimmedLocalNames = localNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

            // upload new local lists
            val toUpload = trimmedLocalNames - remoteNames
            for (name in toUpload) {
                upsertHotBoxList(context, name)
            }

            // delete removed local lists
            val toDelete = remoteNames - trimmedLocalNames
            for (name in toDelete) {
                deleteHotBoxList(context, name)
            }
            true
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "replaceHotBoxLists error", e)
            false
        }
    }

    suspend fun mergeHotBoxLists(
        context: Context,
        names: Collection<String>
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            val cleanNames = names
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            for (name in cleanNames) {
                if (!upsertHotBoxList(context, name)) {
                    return@withContext false
                }
            }

            true
        } catch (e: Exception) {
            Log.e(
                "SupabaseDbClient",
                "mergeHotBoxLists error",
                e
            )
            false
        }
    }

    // --- ANNAHME DOCUMENTS SYNC ---

    suspend fun fetchAnnahmeDokumente(
        context: Context
    ): List<com.example.database.AnnahmeDokumentEntity> = withContext(Dispatchers.IO) {

        val result = mutableListOf<com.example.database.AnnahmeDokumentEntity>()

        try {
            val headers = getAuthHeaders(context) ?: mapOf(
                "apikey" to SUPABASE_PUBLIC_KEY,
                "Authorization" to "Bearer $SUPABASE_PUBLIC_KEY"
            )
            val requestBuilder = Request.Builder()
                .url("$SUPABASE_URL/functions/v1/annahme-upload")
                .get()

            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    Log.e(
                        "SupabaseDbClient",
                        "Annahmen fetch failed: ${response.code} ${response.body?.string()}"
                    )
                    return@use
                }

                val root = JSONObject(response.body?.string() ?: "{}")
                val rows = root.optJSONArray("items") ?: JSONArray()

                for (i in 0 until rows.length()) {
                    val obj = rows.getJSONObject(i)

                    val created = obj.optString("createdAt", obj.optString("created_at", ""))

                    val timestamp = try {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                        val cleaned = created.replace("Z", "").substringBefore(".")
                        sdf.parse(cleaned)?.time ?: System.currentTimeMillis()
                    } catch (_: Throwable) {
                        System.currentTimeMillis()
                    }

                    val fileName = obj.optString("fileName", obj.optString("file_name", "Dokument.pdf"))
                    val regex = Regex("(?i)kd[-_]?\\d+")
                    val customerNumber = regex.find(fileName)?.value?.uppercase() ?: "Supabase"

                    val docId = obj.opt("id")?.toString() ?: ""

                    result.add(
                        com.example.database.AnnahmeDokumentEntity(
                            id = docId,

                            customerNumber = customerNumber,

                            fileName = fileName,

                            fileType = obj.optString(
                                "fileType",
                                obj.optString("file_type", "application/pdf")
                            ),

                            // Hier wird der temporäre Supabase Download-Link gespeichert
                            fileContentString = obj.optString(
                                "downloadUrl",
                                obj.optString("download_url", "")
                            ),

                            // Hier wird der echte Supabase Storage-Pfad gespeichert
                            localFilePath = obj.optString(
                                "storagePath",
                                obj.optString("storage_path", "")
                            ),

                            timestamp = timestamp
                        )
                    )
                }
            }

        } catch (e: Exception) {
            Log.e(
                "SupabaseDbClient",
                "fetchAnnahmeDokumente failed",
                e
            )
        }

        result
    }


    suspend fun getSignedUrl(
        context: Context,
        storagePath: String
    ): String? = withContext(Dispatchers.IO) {
        if (storagePath.isBlank()) return@withContext null
        try {
            val headers = getAuthHeaders(context) ?: mapOf(
                "apikey" to SUPABASE_PUBLIC_KEY,
                "Authorization" to "Bearer $SUPABASE_PUBLIC_KEY"
            )
            val cleanPath = if (storagePath.startsWith("/")) storagePath.substring(1) else storagePath
            val requestBody = JSONObject().put("expiresIn", 3600).toString().toRequestBody(JSON_MEDIA_TYPE)
            
            val requestBuilder = Request.Builder()
                .url("$SUPABASE_URL/storage/v1/object/sign/annahmen/$cleanPath")
                .post(requestBody)

            headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val root = JSONObject(responseBody)
                    val urlResult = root.optString("signedURL", root.optString("signedUrl", ""))
                    if (urlResult.isNotEmpty()) {
                        if (urlResult.startsWith("/")) {
                            return@withContext "$SUPABASE_URL$urlResult"
                        }
                        return@withContext urlResult
                    }
                } else {
                    Log.e("SupabaseDbClient", "getSignedUrl failed with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseDbClient", "getSignedUrl failed for $storagePath", e)
        }
        null
    }


    suspend fun downloadAnnahmeDokument(
        context: Context,
        doc: com.example.database.AnnahmeDokumentEntity
    ): ByteArray? = withContext(Dispatchers.IO) {

        var url = doc.fileContentString
        if (url.isBlank()) {
            Log.d("SupabaseDbClient", "downloadUrl is empty. Fetching signed URL...")
            url = getSignedUrl(context, doc.localFilePath) ?: ""
        }

        if (url.isBlank()) {
            return@withContext null
        }

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    Log.e(
                        "SupabaseDbClient",
                        "Annahme download failed: ${response.code}"
                    )

                    null
                } else {
                    response.body?.bytes()
                }
            }

        } catch (e: Exception) {
            Log.e(
                "SupabaseDbClient",
                "downloadAnnahmeDokument failed",
                e
            )

            null
        }
    }

    // --- MCP App Commands ---
    
    suspend fun getPendingCommands(context: Context): JSONArray {
        return withContext(Dispatchers.IO) {
            try {
                val token = SupabaseAuthClient.getSessionToken(context)
                if (token.isNullOrEmpty()) return@withContext JSONArray()
                
                // Fetch only pending commands newer than 2 minutes ago
                val twoMinsAgo = System.currentTimeMillis() - 120000
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val cutoff = format.format(java.util.Date(twoMinsAgo))

                val urlString = "$SUPABASE_URL/rest/v1/app_commands?status=eq.pending&created_at=gte.$cutoff&select=*"
                
                val request = Request.Builder()
                    .url(urlString)
                    .addHeader("apikey", SUPABASE_PUBLIC_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) JSONArray(body) else JSONArray()
                    } else {
                        Log.e("SupabaseDbClient", "Error fetching commands: ${response.code} ${response.message}")
                        JSONArray()
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseDbClient", "Exception fetching commands", e)
                JSONArray()
            }
        }
    }
    
    suspend fun markCommandDone(context: Context, commandId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val token = SupabaseAuthClient.getSessionToken(context)
                if (token.isNullOrEmpty()) return@withContext false
                
                val urlString = "$SUPABASE_URL/rest/v1/app_commands?id=eq.$commandId"
                
                val payload = JSONObject()
                payload.put("status", "done")
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                payload.put("executed_at", format.format(java.util.Date()))
                
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = payload.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url(urlString)
                    .addHeader("apikey", SUPABASE_PUBLIC_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Prefer", "return=minimal")
                    .patch(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("SupabaseDbClient", "Error marking command done: ${response.code} ${response.message}")
                        false
                    } else {
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e("SupabaseDbClient", "Exception marking command done", e)
                false
            }
        }
    }
}
