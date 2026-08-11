package com.example.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Kommuniziert ausschließlich mit den sicheren Supabase Edge Functions. */
object AgentBackendClient {

    private const val FUNCTIONS_BASE = "https://yepluyipizbbrgoffqdq.supabase.co/functions/v1"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder().build()

    /** Prüft die Verbindung und gibt verfügbare Realtime-Modelle zurück. */
    suspend fun checkConnection(loginToken: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$FUNCTIONS_BASE/schluessel")
            .get()
            .addHeader("Authorization", "Bearer $loginToken")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    JSONObject(body.ifEmpty { "{}" }).optString("error", "HTTP ${resp.code}")
                )
            }
            JSONObject(body)
        }
    }

    /** Speichert den OpenAI API-Schlüssel verschlüsselt in Supabase Vault über /schluessel. */
    suspend fun saveOpenAiKey(loginToken: String, key: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("openai_api_key", key.trim()).toString()
        val req = Request.Builder()
            .url("$FUNCTIONS_BASE/schluessel")
            .post(payload.toRequestBody(JSON_MEDIA))
            .addHeader("Authorization", "Bearer $loginToken")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    JSONObject(body.ifEmpty { "{}" }).optString("error", "HTTP ${resp.code}")
                )
            }
            JSONObject(body)
        }
    }

    /** Erzeugt ein ephemeral Realtime Token über /realtime-token. */
    suspend fun createRealtimeSession(
        loginToken: String,
        agentId: String?,
        szenarioId: String?,
        test: Boolean = false,
    ): JSONObject = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            if (!agentId.isNull_or_blank()) put("agent_id", agentId)
            if (!szenarioId.isNull_or_blank()) put("szenario_id", szenarioId)
            put("test", test)
        }
        val req = Request.Builder()
            .url("$FUNCTIONS_BASE/realtime-token")
            .post(json.toString().toRequestBody(JSON_MEDIA))
            .addHeader("Authorization", "Bearer $loginToken")
            .build()

        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    JSONObject(body.ifEmpty { "{}" }).optString("error", "HTTP ${resp.code}")
                )
            }
            JSONObject(body)
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
}
