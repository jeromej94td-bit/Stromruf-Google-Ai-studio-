package com.example.agent

import android.content.Context
import android.util.Log
import com.example.util.SupabaseAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Gesamte Supabase-Anbindung des Agents-Reiters. */
object AgentBackend {

    private const val SUPABASE_URL = "https://yepluyipizbbrgoffqdq.supabase.co"
    private const val SUPABASE_PUBLIC_KEY = "sb_publishable_lat183ycL-tC_3NDwzCHOw_GKmcNWqM"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private suspend fun headers(context: Context): Map<String, String>? {
        var token = SupabaseAuthClient.getSessionToken(context) ?: return null
        if (SupabaseAuthClient.isTokenExpired(token)) {
            token = SupabaseAuthClient.refreshSession(context) ?: return null
        }
        return mapOf("apikey" to SUPABASE_PUBLIC_KEY, "Authorization" to "Bearer $token")
    }
    private suspend fun userId(context: Context): String? {
        val t = SupabaseAuthClient.getSessionToken(context) ?: return null
        return SupabaseAuthClient.getTokenClaim(t, "sub")
    }
    private fun iso(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC"); return f.format(Date(ms))
    }
    private fun parseIso(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        return runCatching {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC"); f.parse(s.take(19))!!.time
        }.getOrDefault(0L)
    }
    private fun String?.orNullIfEmpty(): String? =
        this?.takeIf { it.isNotEmpty() && it != "null" }

    private suspend fun get(context: Context, pfad: String): JSONArray? {
        val h = headers(context) ?: return null
        return try {
            val rb = Request.Builder().url("$SUPABASE_URL/rest/v1/$pfad").get()
            h.forEach { (k, v) -> rb.addHeader(k, v) }
            client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) { Log.e("AgentBackend", "GET $pfad ${r.code}"); null }
                else JSONArray(r.body?.string() ?: "[]")
            }
        } catch (e: Exception) { Log.e("AgentBackend", "GET $pfad", e); null }
    }

    private suspend fun upsert(context: Context, tabelle: String, body: JSONObject): Boolean {
        val h = headers(context) ?: return false
        return try {
            val rb = Request.Builder().url("$SUPABASE_URL/rest/v1/$tabelle")
                .post(body.toString().toRequestBody(JSON))
                .addHeader("Prefer", "resolution=merge-duplicates")
                .addHeader("Content-Type", "application/json")
            h.forEach { (k, v) -> rb.addHeader(k, v) }
            client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) Log.e("AgentBackend", "UPSERT $tabelle ${r.code}: ${r.body?.string()}")
                r.isSuccessful
            }
        } catch (e: Exception) { Log.e("AgentBackend", "UPSERT $tabelle", e); false }
    }

    private suspend fun patch(context: Context, pfad: String, body: JSONObject): Boolean {
        val h = headers(context) ?: return false
        return try {
            val rb = Request.Builder().url("$SUPABASE_URL/rest/v1/$pfad")
                .patch(body.toString().toRequestBody(JSON))
                .addHeader("Content-Type", "application/json")
            h.forEach { (k, v) -> rb.addHeader(k, v) }
            client.newCall(rb.build()).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    private suspend fun delete(context: Context, pfad: String): Boolean {
        val h = headers(context) ?: return false
        return try {
            val rb = Request.Builder().url("$SUPABASE_URL/rest/v1/$pfad").delete()
            h.forEach { (k, v) -> rb.addHeader(k, v) }
            client.newCall(rb.build()).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    // =================== Agenten ===================
    suspend fun fetchAgents(context: Context): List<AgentProfile>? = withContext(Dispatchers.IO) {
        val arr = get(context, "agent_profiles?select=*&order=sort_order.asc") ?: return@withContext null
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AgentProfile(
                id = o.getString("id"),
                name = o.optString("name", "Agent"),
                role = o.optString("role", "").orNullIfEmpty() ?: "",
                direction = o.optString("direction", "beide"),
                greeting = o.optString("greeting", ""),
                systemPrompt = o.optString("system_prompt", ""),
                language = o.optString("language", "de"),
                voiceId = o.optString("voice_id", "nova"),
                voiceSpeed = o.optDouble("voice_speed", 1.0).toFloat(),
                formOfAddress = o.optString("form_of_address", "sie"),
                maxParallel = o.optInt("max_parallel", 2),
                listenWindowSec = o.optInt("listen_window_sec", 6),
                maxDurationMin = o.optInt("max_duration_min", 15),
                aiDisclosure = o.optBoolean("ai_disclosure", true),
                shortAnswers = o.optBoolean("short_answers", true),
                transferNumber = o.optString("transfer_number", "").orNullIfEmpty() ?: "",
                useKnowledge = o.optBoolean("use_knowledge", true),
                isActive = o.optBoolean("is_active", false),
                sortOrder = o.optInt("sort_order", 0)
            )
        }
    }

    suspend fun upsertAgent(context: Context, a: AgentProfile): Boolean = withContext(Dispatchers.IO) {
        val uid = userId(context)
        upsert(context, "agent_profiles", JSONObject().apply {
            put("id", a.id); put("name", a.name); put("role", a.role)
            put("direction", a.direction); put("greeting", a.greeting)
            put("system_prompt", a.systemPrompt); put("language", a.language)
            put("voice_id", a.voiceId); put("voice_speed", a.voiceSpeed.toDouble())
            put("form_of_address", a.formOfAddress); put("max_parallel", a.maxParallel)
            put("listen_window_sec", a.listenWindowSec); put("max_duration_min", a.maxDurationMin)
            put("ai_disclosure", a.aiDisclosure); put("short_answers", a.shortAnswers)
            put("transfer_number", a.transferNumber.orNullIfEmpty() ?: JSONObject.NULL)
            put("use_knowledge", a.useKnowledge)
            put("is_active", a.isActive); put("sort_order", a.sortOrder)
            put("updated_at", iso(System.currentTimeMillis()))
            if (uid != null) put("user_id", uid)
        })
    }

    suspend fun deleteAgent(context: Context, id: String): Boolean =
        withContext(Dispatchers.IO) { delete(context, "agent_profiles?id=eq.$id") }

    // =================== Konfiguration ===================
    suspend fun fetchConfig(context: Context): RuntimeConfig? = withContext(Dispatchers.IO) {
        val arr = get(context, "agent_runtime_config?select=*&limit=1") ?: return@withContext null
        if (arr.length() == 0) return@withContext RuntimeConfig()
        val o = arr.getJSONObject(0)
        RuntimeConfig(
            sipDisplayName = o.optString("sip_display_name", ""),
            sipUser = o.optString("sip_user", "").orNullIfEmpty() ?: "",
            sipPassword = o.optString("sip_password", "").orNullIfEmpty() ?: "",
            sipDomain = o.optString("sip_domain", "").orNullIfEmpty() ?: "",
            sipProxy = o.optString("sip_proxy", "").orNullIfEmpty() ?: "",
            sipPort = o.optInt("sip_port", 5060),
            sipTransport = o.optString("sip_transport", "UDP"),
            sipCallerId = o.optString("sip_caller_id", "").orNullIfEmpty() ?: "",
            sipMaxLines = o.optInt("sip_max_lines", 4),
            routingStrategy = o.optString("routing_strategy", "round_robin"),
            fixedAgentId = o.optString("fixed_agent_id", "").orNullIfEmpty(),
            llmProvider = o.optString("llm_provider", "openai"),
            llmBaseUrl = o.optString("llm_base_url", "https://api.openai.com/v1"),
            llmModel = o.optString("llm_model", "gpt-4o-mini"),
            llmApiKey = o.optString("llm_api_key", "").orNullIfEmpty() ?: "",
            sttBaseUrl = o.optString("stt_base_url", "https://api.openai.com/v1"),
            sttModel = o.optString("stt_model", "whisper-1"),
            ttsBaseUrl = o.optString("tts_base_url", "https://api.openai.com/v1"),
            ttsModel = o.optString("tts_model", "tts-1"),
            openaiApiKey = o.optString("openai_api_key", "").orNullIfEmpty() ?: "",
            aiDisclosureText = o.optString("ai_disclosure_text", ""),
            recordingEnabled = o.optBoolean("recording_enabled", true),
            retentionDays = o.optInt("retention_days", 7)
        )
    }

    suspend fun saveConfig(context: Context, c: RuntimeConfig): Boolean = withContext(Dispatchers.IO) {
        val uid = userId(context) ?: return@withContext false
        upsert(context, "agent_runtime_config", JSONObject().apply {
            put("user_id", uid)
            put("sip_display_name", c.sipDisplayName)
            put("sip_user", c.sipUser); put("sip_password", c.sipPassword)
            put("sip_domain", c.sipDomain); put("sip_proxy", c.sipProxy)
            put("sip_port", c.sipPort); put("sip_transport", c.sipTransport)
            put("sip_caller_id", c.sipCallerId); put("sip_max_lines", c.sipMaxLines)
            put("routing_strategy", c.routingStrategy)
            put("fixed_agent_id", c.fixedAgentId ?: JSONObject.NULL)
            put("llm_provider", c.llmProvider); put("llm_base_url", c.llmBaseUrl)
            put("llm_model", c.llmModel); put("llm_api_key", c.llmApiKey)
            put("stt_base_url", c.sttBaseUrl); put("stt_model", c.sttModel)
            put("tts_base_url", c.ttsBaseUrl); put("tts_model", c.ttsModel)
            put("openai_api_key", c.openaiApiKey)
            put("ai_disclosure_text", c.aiDisclosureText)
            put("recording_enabled", c.recordingEnabled)
            put("retention_days", c.retentionDays)
            put("updated_at", iso(System.currentTimeMillis()))
        })
    }

    // =================== Sessions ===================
    suspend fun fetchSessions(context: Context): List<CallSessionRow>? = withContext(Dispatchers.IO) {
        val arr = get(context, "agent_call_sessions?select=*&order=started_at.desc&limit=200")
            ?: return@withContext null
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CallSessionRow(
                id = o.getString("id"),
                agentName = o.optString("agent_name", "Agent"),
                agentRole = o.optString("agent_role", "").orNullIfEmpty(),
                direction = o.optString("direction", "eingehend"),
                remoteNumber = o.optString("remote_number", "").orNullIfEmpty(),
                contactName = o.optString("contact_name", "").orNullIfEmpty(),
                startedAt = parseIso(o.optString("started_at", "")),
                durationSec = o.optInt("duration_sec", 0),
                status = o.optString("status", "beendet"),
                outcome = o.optString("outcome", "").orNullIfEmpty(),
                summary = o.optString("summary", "").orNullIfEmpty(),
                sentiment = o.optString("sentiment", "").orNullIfEmpty(),
                transcript = parseTranscript(o.optJSONArray("transcript")),
                recordingPath = o.optString("recording_path", "").orNullIfEmpty(),
                recordingExpiresAt = parseIso(o.optString("recording_expires_at", ""))
                    .takeIf { it > 0 }
            )
        }
    }

    private fun parseTranscript(arr: JSONArray?): List<Pair<Boolean, String>> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            o.optBoolean("vomAgent", false) to o.optString("text", "")
        }.filter { it.second.isNotBlank() }
    }

    suspend fun uploadSession(
        context: Context, sessionId: String, agentId: String?, agentName: String,
        agentRole: String?, direction: String, remoteNumber: String,
        contactId: String?, contactName: String?, campaignId: String?,
        startedAt: Long, endedAt: Long, status: String, summary: String?,
        transcript: List<TranscriptLine>, latenzen: Latenzen, recording: File?
    ): Boolean = withContext(Dispatchers.IO) {
        val h = headers(context) ?: return@withContext false
        val uid = userId(context) ?: return@withContext false

        var pfad: String? = null; var groesse: Long? = null
        if (recording != null && recording.exists() && recording.length() > 1000) {
            val p = "$uid/$sessionId.wav"
            try {
                val rb = Request.Builder()
                    .url("$SUPABASE_URL/storage/v1/object/agent-recordings/$p")
                    .post(recording.asRequestBody("audio/wav".toMediaType()))
                    .addHeader("Content-Type", "audio/wav").addHeader("x-upsert", "true")
                h.forEach { (k, v) -> rb.addHeader(k, v) }
                client.newCall(rb.build()).execute().use { r ->
                    if (r.isSuccessful) { pfad = p; groesse = recording.length() }
                    else Log.e("AgentBackend", "Upload ${r.code}: ${r.body?.string()}")
                }
            } catch (e: Exception) { Log.e("AgentBackend", "Upload", e) }
        }

        val tArr = JSONArray()
        transcript.forEach {
            tArr.put(JSONObject().put("vomAgent", it.vomAgent).put("text", it.text).put("ts", it.ts))
        }
        upsert(context, "agent_call_sessions", JSONObject().apply {
            put("id", sessionId); put("user_id", uid)
            put("agent_id", agentId ?: JSONObject.NULL); put("agent_name", agentName)
            put("agent_role", agentRole ?: JSONObject.NULL); put("direction", direction)
            put("remote_number", remoteNumber)
            put("contact_id", contactId ?: JSONObject.NULL)
            put("contact_name", contactName ?: JSONObject.NULL)
            put("campaign_id", campaignId ?: JSONObject.NULL)
            put("started_at", iso(startedAt)); put("ended_at", iso(endedAt))
            put("duration_sec", ((endedAt - startedAt) / 1000).toInt())
            put("status", status); put("summary", summary ?: JSONObject.NULL)
            put("transcript", tArr)
            put("latencies", JSONObject().put("stt_ms", latenzen.sttMs)
                .put("llm_ms", latenzen.llmMs).put("tts_ms", latenzen.ttsMs))
            put("recording_path", pfad ?: JSONObject.NULL)
            put("recording_bytes", groesse ?: JSONObject.NULL)
        })
    }

    suspend fun signedRecordingUrl(context: Context, path: String): String? =
        withContext(Dispatchers.IO) {
            val h = headers(context) ?: return@withContext null
            try {
                val rb = Request.Builder()
                    .url("$SUPABASE_URL/storage/v1/object/sign/agent-recordings/$path")
                    .post(JSONObject().put("expiresIn", 3600).toString().toRequestBody(JSON))
                    .addHeader("Content-Type", "application/json")
                h.forEach { (k, v) -> rb.addHeader(k, v) }
                client.newCall(rb.build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    val s = JSONObject(r.body?.string() ?: "{}")
                        .optString("signedURL", "").orNullIfEmpty() ?: return@withContext null
                    "$SUPABASE_URL/storage/v1$s"
                }
            } catch (e: Exception) { null }
        }

    suspend fun deleteSession(context: Context, s: CallSessionRow): Boolean =
        withContext(Dispatchers.IO) {
            s.recordingPath?.let { p ->
                val h = headers(context) ?: return@withContext false
                runCatching {
                    val rb = Request.Builder()
                        .url("$SUPABASE_URL/storage/v1/object/agent-recordings/$p").delete()
                    h.forEach { (k, v) -> rb.addHeader(k, v) }
                    client.newCall(rb.build()).execute().close()
                }
            }
            delete(context, "agent_call_sessions?id=eq.${s.id}")
        }

    // =================== Nachbearbeitung / Aktionen ===================
    suspend fun runPostCall(context: Context, sessionId: String): Boolean =
        withContext(Dispatchers.IO) {
            val h = headers(context) ?: return@withContext false
            try {
                val rb = Request.Builder()
                    .url("$SUPABASE_URL/functions/v1/agentcall-postcall")
                    .post(JSONObject().put("session_id", sessionId).toString().toRequestBody(JSON))
                    .addHeader("Content-Type", "application/json")
                h.forEach { (k, v) -> rb.addHeader(k, v) }
                client.newCall(rb.build()).execute().use { r ->
                    if (!r.isSuccessful) Log.e("AgentBackend", "postcall ${r.code}: ${r.body?.string()}")
                    r.isSuccessful
                }
            } catch (e: Exception) { false }
        }

    suspend fun fetchActions(context: Context, sessionId: String): List<AgentAction> =
        withContext(Dispatchers.IO) {
            val arr = get(context,
                "agent_actions?select=*&session_id=eq.$sessionId&order=created_at.asc")
                ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AgentAction(
                    id = o.getString("id"),
                    sessionId = o.optString("session_id", "").orNullIfEmpty(),
                    toolName = o.optString("tool_name", ""),
                    arguments = formatiereArgumente(o.optJSONObject("arguments")),
                    reason = o.optString("reason", "").orNullIfEmpty(),
                    status = o.optString("status", "ausgefuehrt"),
                    error = o.optString("error", "").orNullIfEmpty(),
                    createdAt = parseIso(o.optString("created_at", ""))
                )
            }
        }

    private fun formatiereArgumente(o: JSONObject?): String {
        if (o == null) return ""
        val teile = mutableListOf<String>()
        o.keys().forEach { k ->
            if (k == "_begruendung") return@forEach
            val v = o.opt(k)?.toString() ?: return@forEach
            if (v.isNotBlank() && v != "null") teile += "$k: $v"
        }
        return teile.joinToString(" · ")
    }

    suspend fun applyActions(context: Context, ids: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext true
            val h = headers(context) ?: return@withContext false
            try {
                val arr = JSONArray(); ids.forEach { arr.put(it) }
                val rb = Request.Builder()
                    .url("$SUPABASE_URL/functions/v1/agentcall-postcall")
                    .post(JSONObject().put("mode", "apply").put("action_ids", arr)
                        .toString().toRequestBody(JSON))
                    .addHeader("Content-Type", "application/json")
                h.forEach { (k, v) -> rb.addHeader(k, v) }
                client.newCall(rb.build()).execute().use { it.isSuccessful }
            } catch (e: Exception) { false }
        }

    suspend fun discardAction(context: Context, id: String): Boolean =
        patch(context, "agent_actions?id=eq.$id", JSONObject().put("status", "verworfen"))

    // =================== Policy ===================
    suspend fun fetchPolicy(context: Context): ToolPolicy = withContext(Dispatchers.IO) {
        val arr = get(context, "agent_tool_policy?select=*&limit=1") ?: return@withContext ToolPolicy()
        if (arr.length() == 0) return@withContext ToolPolicy()
        val o = arr.getJSONObject(0)
        val tools = o.optJSONArray("allowed_tools")
        ToolPolicy(
            autoApply = o.optBoolean("auto_apply", false),
            allowedTools = if (tools == null) ToolPolicy().allowedTools
                else (0 until tools.length()).map { tools.getString(it) },
            maxActions = o.optInt("max_actions", 8),
            extraPrompt = o.optString("extra_prompt", "")
        )
    }

    suspend fun savePolicy(context: Context, p: ToolPolicy): Boolean = withContext(Dispatchers.IO) {
        val uid = userId(context) ?: return@withContext false
        val arr = JSONArray(); p.allowedTools.forEach { arr.put(it) }
        upsert(context, "agent_tool_policy", JSONObject()
            .put("user_id", uid).put("auto_apply", p.autoApply)
            .put("allowed_tools", arr).put("max_actions", p.maxActions)
            .put("extra_prompt", p.extraPrompt)
            .put("updated_at", iso(System.currentTimeMillis())))
    }

    // =================== Wissen ===================
    suspend fun fetchKnowledge(context: Context): List<KnowledgeEntry> = withContext(Dispatchers.IO) {
        val arr = get(context, "agent_knowledge?select=*&order=created_at.desc")
            ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KnowledgeEntry(
                id = o.getString("id"),
                agentId = o.optString("agent_id", "").orNullIfEmpty(),
                title = o.optString("title", ""),
                sourceType = o.optString("source_type", "text"),
                sourceUrl = o.optString("source_url", "").orNullIfEmpty() ?: "",
                content = o.optString("content", ""),
                isActive = o.optBoolean("is_active", true)
            )
        }
    }

    suspend fun upsertKnowledge(context: Context, k: KnowledgeEntry): Boolean =
        withContext(Dispatchers.IO) {
            val uid = userId(context)
            upsert(context, "agent_knowledge", JSONObject().apply {
                put("id", k.id); put("agent_id", k.agentId ?: JSONObject.NULL)
                put("title", k.title); put("source_type", k.sourceType)
                put("source_url", k.sourceUrl.orNullIfEmpty() ?: JSONObject.NULL)
                put("content", k.content); put("is_active", k.isActive)
                put("updated_at", iso(System.currentTimeMillis()))
                if (uid != null) put("user_id", uid)
            })
        }

    suspend fun deleteKnowledge(context: Context, id: String): Boolean =
        delete(context, "agent_knowledge?id=eq.$id")

    /** Wissenstext für einen Agenten zusammenstellen (max. ~6000 Zeichen). */
    suspend fun knowledgeTextFor(context: Context, agentId: String): String {
        val alle = fetchKnowledge(context)
        val passend = alle.filter { it.isActive && (it.agentId == null || it.agentId == agentId) }
        val sb = StringBuilder()
        for (k in passend) {
            if (sb.length > 6000) break
            sb.append("### ").append(k.title).append("\n")
                .append(k.content.take(2500)).append("\n\n")
        }
        return sb.toString().take(6500)
    }

    // =================== Kampagnen ===================
    suspend fun fetchCampaigns(context: Context): List<Campaign> = withContext(Dispatchers.IO) {
        val arr = get(context, "agent_campaigns?select=*&order=created_at.desc")
            ?: return@withContext emptyList()
        val liste = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Campaign(
                id = o.getString("id"), name = o.optString("name", ""),
                agentId = o.optString("agent_id", ""),
                hotboxListName = o.optString("hotbox_list_name", "").orNullIfEmpty(),
                status = o.optString("status", "pausiert"),
                startHour = o.optInt("start_hour", 9), endHour = o.optInt("end_hour", 18),
                maxParallel = o.optInt("max_parallel", 1), maxAttempts = o.optInt("max_attempts", 2)
            )
        }
        // Fortschritt nachladen
        liste.map { c ->
            val st = get(context,
                "agent_campaign_calls?select=status&campaign_id=eq.${c.id}") ?: JSONArray()
            var offen = 0; var erledigt = 0
            for (i in 0 until st.length()) {
                when (st.getJSONObject(i).optString("status")) {
                    "offen", "laeuft" -> offen++
                    "erledigt" -> erledigt++
                }
            }
            c.copy(offen = offen, erledigt = erledigt, gesamt = st.length())
        }
    }

    /** Kampagne anlegen + Hotbox-Kontakte als Anrufliste einfrieren. */
    suspend fun createCampaign(context: Context, c: Campaign): String? = withContext(Dispatchers.IO) {
        val uid = userId(context) ?: return@withContext "Nicht angemeldet."
        val ok = upsert(context, "agent_campaigns", JSONObject().apply {
            put("id", c.id); put("user_id", uid); put("name", c.name)
            put("agent_id", c.agentId)
            put("hotbox_list_name", c.hotboxListName ?: JSONObject.NULL)
            put("status", c.status); put("start_hour", c.startHour); put("end_hour", c.endHour)
            put("max_parallel", c.maxParallel); put("max_attempts", c.maxAttempts)
        })
        if (!ok) return@withContext "Kampagne konnte nicht gespeichert werden."

        // Hotbox-Kontakte aus Supabase ziehen (dort ist der Sync-Stand)
        val filter = if (c.hotboxListName != null)
            "&hot_box_list_name=eq.${java.net.URLEncoder.encode(c.hotboxListName, "UTF-8")}" else ""
        val kontakte = get(context,
            "contacts?select=id,name,phone&is_hot_box=eq.true$filter&limit=500")
            ?: return@withContext "Kontakte konnten nicht geladen werden."
        if (kontakte.length() == 0) return@withContext "Keine Hotbox-Kontakte in dieser Liste."

        val batch = JSONArray()
        for (i in 0 until kontakte.length()) {
            val k = kontakte.getJSONObject(i)
            val tel = k.optString("phone", "")
            if (tel.isBlank()) continue
            batch.put(JSONObject()
                .put("user_id", uid).put("campaign_id", c.id)
                .put("contact_id", k.getString("id"))
                .put("contact_name", k.optString("name", ""))
                .put("phone", tel))
        }
        val h = headers(context) ?: return@withContext "Nicht angemeldet."
        try {
            val rb = Request.Builder().url("$SUPABASE_URL/rest/v1/agent_campaign_calls")
                .post(batch.toString().toRequestBody(JSON))
                .addHeader("Prefer", "resolution=ignore-duplicates")
                .addHeader("Content-Type", "application/json")
            h.forEach { (k, v) -> rb.addHeader(k, v) }
            client.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext "Anrufliste fehlgeschlagen (${r.code})."
            }
        } catch (e: Exception) { return@withContext "Anrufliste fehlgeschlagen." }
        null
    }

    suspend fun setCampaignStatus(context: Context, id: String, status: String): Boolean =
        patch(context, "agent_campaigns?id=eq.$id", JSONObject().put("status", status))

    suspend fun deleteCampaign(context: Context, id: String): Boolean =
        delete(context, "agent_campaigns?id=eq.$id")

    /** Nächsten offenen Kampagnen-Anruf holen und auf 'laeuft' setzen. */
    suspend fun claimNextCampaignCall(context: Context, c: Campaign): CampaignCall? =
        withContext(Dispatchers.IO) {
            val arr = get(context, "agent_campaign_calls?select=*" +
                "&campaign_id=eq.${c.id}&status=eq.offen&attempts=lt.${c.maxAttempts}" +
                "&order=created_at.asc&limit=1") ?: return@withContext null
            if (arr.length() == 0) return@withContext null
            val o = arr.getJSONObject(0)
            val call = CampaignCall(
                id = o.getString("id"), contactId = o.getString("contact_id"),
                contactName = o.optString("contact_name", "").orNullIfEmpty(),
                phone = o.getString("phone"),
                attempts = o.optInt("attempts", 0), status = "laeuft"
            )
            val ok = patch(context, "agent_campaign_calls?id=eq.${call.id}",
                JSONObject().put("status", "laeuft").put("attempts", call.attempts + 1)
                    .put("last_attempt_at", iso(System.currentTimeMillis())))
            if (ok) call else null
        }

    suspend fun finishCampaignCall(
        context: Context, callId: String, sessionId: String,
        erledigt: Boolean, attempts: Int, maxAttempts: Int
    ) {
        val status = when {
            erledigt -> "erledigt"
            attempts >= maxAttempts -> "nicht_erreicht"
            else -> "offen"
        }
        patch(context, "agent_campaign_calls?id=eq.$callId",
            JSONObject().put("status", status).put("last_session_id", sessionId))
    }

    suspend fun hotboxListNames(context: Context): List<String> = withContext(Dispatchers.IO) {
        val arr = get(context,
            "contacts?select=hot_box_list_name&is_hot_box=eq.true&limit=1000")
            ?: return@withContext emptyList()
        (0 until arr.length())
            .mapNotNull { arr.getJSONObject(it).optString("hot_box_list_name", "").orNullIfEmpty() }
            .distinct().sorted()
    }
}
