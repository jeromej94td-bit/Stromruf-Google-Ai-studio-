package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object AiAgentClient {
    private const val EDGE_FUNCTION_URL = 
        "https://yepluyipizbbrgoffqdq.supabase.co/functions/v1/ai-agent"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Safely retrieves the API key using reflection from BuildConfig.
     * Fallback to a default secure key if not set.
     */
    private fun getApiKey(): String {
        return try {
            val field = Class.forName("com.example.BuildConfig").getField("AI_AGENT_API_KEY")
            field.get(null) as? String ?: "geheim123"
        } catch (_: Throwable) {
            "geheim123"
        }
    }

    /**
     * Sende eine Anweisung an den KI-Agent
     * @param context Android Context
     * @param instruction Anweisung in Deutsch (z.B. "Erstelle einen neuen Kontakt namens Max Müller mit Nummer 0123456789")
     * @return Agent-Antwort als String
     */
    suspend fun executeInstruction(
        context: Context,
        instruction: String
    ): String = withContext(Dispatchers.IO) {
        val token = SupabaseAuthClient.getSessionToken(context) ?: run {
            Log.e("AiAgentClient", "No session token available")
            return@withContext "Fehler: Nicht authentifiziert. Bitte melde dich an."
        }

        val userId = SupabaseDbClient.getUserIdFromToken(token) ?: run {
            Log.e("AiAgentClient", "Could not extract user ID from token")
            return@withContext "Fehler: Benutzer-Sitzung konnte nicht extrahiert werden."
        }

        try {
            val requestBody = JSONObject().apply {
                put("user_id", userId)
                put("api_key", getApiKey())
                put("instruction", instruction)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(EDGE_FUNCTION_URL)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "{}"
                    val jsonResponse = JSONObject(responseBody)
                    
                    if (jsonResponse.has("response")) {
                        jsonResponse.getString("response")
                    } else {
                        "Fehler: Keine Antwort vom Agent"
                    }
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e("AiAgentClient", "Error: ${response.code} - $errorBody")
                    "Fehler (${response.code}): $errorBody"
                }
            }
        } catch (e: Exception) {
            Log.e("AiAgentClient", "Exception in executeInstruction", e)
            "Fehler bei der Verbindung zum KI-Agenten: ${e.message}"
        }
    }

    /**
     * Batch-Verarbeitung mehrerer Anweisungen
     */
    suspend fun executeBatch(
        context: Context,
        instructions: List<String>
    ): List<String> = withContext(Dispatchers.IO) {
        instructions.map { instruction ->
            executeInstruction(context, instruction)
        }
    }

    // --- Developer Helper Functions ---

    suspend fun createContact(
        context: Context,
        name: String,
        phone: String,
        company: String = "",
        isHotBox: Boolean = false
    ): String {
        val prompt = StringBuilder("Erstelle einen Kontakt für")
        if (company.isNotEmpty()) prompt.append(" Firma $company mit")
        prompt.append(" Name '$name' und Telefon '$phone'")
        if (isHotBox) prompt.append(" und markiere ihn als Hot-Box Kontakt")
        return executeInstruction(context, prompt.toString())
    }

    suspend fun createFollowup(
        context: Context,
        contactName: String,
        daysFromNow: Int,
        note: String = ""
    ): String {
        val prompt = "Erstelle eine Wiedervorlage für den Kontakt '$contactName' in $daysFromNow Tagen" +
                if (note.isNotEmpty()) " mit der Notiz '$note'" else ""
        return executeInstruction(context, prompt)
    }

    suspend fun logCall(
        context: Context,
        phone: String,
        durationSeconds: Int,
        outcome: String = "",
        notes: String = ""
    ): String {
        val prompt = StringBuilder("Protokolliere einen Anruf für '$phone' mit Dauer $durationSeconds Sekunden")
        if (outcome.isNotEmpty()) prompt.append(" und Ergebnis '$outcome'")
        if (notes.isNotEmpty()) prompt.append(" mit der Notiz '$notes'")
        return executeInstruction(context, prompt.toString())
    }

    suspend fun listContacts(context: Context, searchQuery: String = ""): String {
        val prompt = if (searchQuery.isNotEmpty()) "Zeige mir Kontakte mit Suchfilter '$searchQuery'" else "Zeige mir alle Kontakte"
        return executeInstruction(context, prompt)
    }

    suspend fun listFollowups(context: Context): String {
        return executeInstruction(context, "Zeige mir alle aktiven Wiedervorlagen")
    }

    suspend fun getAnalytics(context: Context): String {
        return executeInstruction(context, "Gib mir eine Zusammenfassung und Statistiken über meine Kontakte und Wiedervorlagen")
    }

    private fun getOpenAiToolsJson(): JSONArray {
        val tools = JSONArray()

        // get_contacts
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_contacts")
                put("description", "Liefert die Liste aller Kontakte und Kunden in der App.")
            })
        })

        // create_contact
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_contact")
                put("description", "Erstellt einen neuen Kontakt/Kunden in der App.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply { put("type", "string"); put("description", "Name des Kontakts") })
                        put("phone", JSONObject().apply { put("type", "string"); put("description", "Telefonnummer des Kontakts") })
                        put("company", JSONObject().apply { put("type", "string"); put("description", "Optional: Firmenname") })
                        put("email", JSONObject().apply { put("type", "string"); put("description", "Optional: E-Mail") })
                        put("call_reason", JSONObject().apply { put("type", "string"); put("description", "Optional: Anrufgrund") })
                        put("is_hot_box", JSONObject().apply { put("type", "boolean"); put("description", "Optional: Markiere als Hot-Box") })
                    })
                    put("required", JSONArray().apply { put("name"); put("phone") })
                })
            })
        })

        // update_contact
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "update_contact")
                put("description", "Aktualisiert einen bestehenden Kontakt/Kunden.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply { put("type", "string"); put("description", "ID des Kontakts") })
                        put("name", JSONObject().apply { put("type", "string") })
                        put("phone", JSONObject().apply { put("type", "string") })
                        put("company", JSONObject().apply { put("type", "string") })
                        put("email", JSONObject().apply { put("type", "string") })
                        put("call_reason", JSONObject().apply { put("type", "string") })
                        put("is_hot_box", JSONObject().apply { put("type", "boolean") })
                    })
                    put("required", JSONArray().apply { put("id") })
                })
            })
        })

        // delete_contact
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_contact")
                put("description", "Löscht einen Kontakt/Kunden anhand der ID.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("id") })
                })
            })
        })

        // get_followups
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_followups")
                put("description", "Liefert alle ausstehenden Termine/Wiedervorlagen.")
            })
        })

        // create_followup
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_followup")
                put("description", "Erstellt einen neuen Termin / eine Wiedervorlage.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("contact_name", JSONObject().apply { put("type", "string") })
                        put("contact_phone", JSONObject().apply { put("type", "string") })
                        put("due_at_timestamp", JSONObject().apply { put("type", "integer"); put("description", "Datum und Uhrzeit als Epoch-Millisekunden (Long)") })
                        put("note", JSONObject().apply { put("type", "string") })
                        put("call_reason", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("contact_name"); put("contact_phone"); put("due_at_timestamp") })
                })
            })
        })

        // complete_followup
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "complete_followup")
                put("description", "Markiert eine Wiedervorlage als erledigt.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("id") })
                })
            })
        })

        // delete_followup
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_followup")
                put("description", "Löscht eine Wiedervorlage anhand der ID.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("id") })
                })
            })
        })

        // get_neukunden
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_neukunden")
                put("description", "Liefert die Liste aller Neukunden.")
            })
        })

        // create_neukunde
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_neukunde")
                put("description", "Erstellt einen neuen Neukunden.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("customer_number", JSONObject().apply { put("type", "string") })
                        put("phone", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("customer_number"); put("phone") })
                })
            })
        })

        // delete_neukunde
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_neukunde")
                put("description", "Löscht einen Neukunden anhand der ID.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("id") })
                })
            })
        })

        // get_heisse_angebote
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_heisse_angebote")
                put("description", "Liefert die Liste aller Heißen Angebote (Hot Deals).")
            })
        })

        // create_heiss_angebot
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_heiss_angebot")
                put("description", "Erstellt ein neues Heißes Angebot (Hot Deal).")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("customer_number", JSONObject().apply { put("type", "string") })
                        put("phone", JSONObject().apply { put("type", "string") })
                        put("notes", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("customer_number"); put("phone") })
                })
            })
        })

        // delete_heiss_angebot
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_heiss_angebot")
                put("description", "Löscht ein Heißes Angebot anhand der ID.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("id", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("id") })
                })
            })
        })

        // get_hotbox_lists
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_hotbox_lists")
                put("description", "Liefert die Namen aller Kampagnen/Hotbox-Listen.")
            })
        })

        // create_hotbox_list
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_hotbox_list")
                put("description", "Erstellt eine neue Hotbox-Liste.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("name") })
                })
            })
        })

        // delete_hotbox_list
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_hotbox_list")
                put("description", "Löscht eine Hotbox-Liste.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("name", JSONObject().apply { put("type", "string") })
                    })
                    put("required", JSONArray().apply { put("name") })
                })
            })
        })

        // get_call_logs
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "get_call_logs")
                put("description", "Liefert die letzten 20 geführten Anrufe (Call Logs).")
            })
        })

        return tools
    }

    suspend fun executeOpenAiInstruction(
        context: Context,
        viewModel: com.example.viewmodel.StromrufViewModel,
        instruction: String,
        openAiApiKey: String
    ): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        
        val currentDateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())
        val systemPrompt = """
            Du bist der STROMRUF KI-Assistent. Du hast die volle Kontrolle über die Daten der App (Kontakte, Wiedervorlagen/Termine, Neukunden, Heiße Angebote, Hotbox-Listen).
            Du steuerst die App durch Funktionsaufrufe (Tools).
            Führe alle vom Benutzer gewünschten Aktionen präzise aus. Wenn eine Aktion unvollständige Daten hat, frage den Benutzer danach oder verwende sinnvolle Standardwerte.

            WICHTIG FÜR ZEITEN UND TERMINE:
            Das aktuelle Datum und Uhrzeit ist: $currentDateTime.
            Wenn der Benutzer Termine oder Wiedervorlagen für relative Angaben (z.B. "morgen um 15 Uhr", "am Montag", "in 2 Tagen") erstellt, berechne den genauen Epoch-Millisekunden-Zeitstempel (Long) basierend auf diesem aktuellen Datum und übergib ihn an das Tool.

            Antworte immer auf Deutsch, professionell und präzise.
        """.trimIndent()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", instruction)
        })

        val toolsJson = getOpenAiToolsJson()
        val dao = com.example.database.AppDatabase.getDatabase(context).stromrufDao()

        var maxIterations = 5
        var currentIteration = 0
        var lastAssistantMessage: String? = null

        while (currentIteration < maxIterations) {
            currentIteration++

            val requestBodyJson = JSONObject().apply {
                put("model", "gpt-4o")
                put("messages", messages)
                put("tools", toolsJson)
                put("tool_choice", "auto")
            }

            val requestBody = requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer ${openAiApiKey.replace("\\s".toRegex(), "")}")
                .addHeader("Content-Type", "application/json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body?.string() ?: "Unknown error"
                        Log.e("AiAgentClient", "OpenAI Error: ${response.code} - $err")
                        return@withContext "OpenAI API Fehler (${response.code}): $err"
                    }

                    val responseBody = response.body?.string() ?: "{}"
                    val root = JSONObject(responseBody)
                    val choices = root.getJSONArray("choices")
                    if (choices.length() == 0) {
                        return@withContext "Keine Antwort von OpenAI erhalten."
                    }

                    val messageObj = choices.getJSONObject(0).getJSONObject("message")
                    lastAssistantMessage = messageObj.optString("content", null)

                    val hasToolCalls = messageObj.has("tool_calls") && !messageObj.isNull("tool_calls")
                    
                    if (hasToolCalls) {
                        val toolCalls = messageObj.getJSONArray("tool_calls")
                        
                        // We must append the assistant's message that requested tools to the message list
                        val assistantMessageToAppend = JSONObject().apply {
                            put("role", "assistant")
                            if (lastAssistantMessage != null) put("content", lastAssistantMessage)
                            put("tool_calls", toolCalls)
                        }
                        messages.put(assistantMessageToAppend)

                        for (i in 0 until toolCalls.length()) {
                            val toolCall = toolCalls.getJSONObject(i)
                            val callId = toolCall.getString("id")
                            val functionObj = toolCall.getJSONObject("function")
                            val toolName = functionObj.getString("name")
                            val argumentsStr = functionObj.getString("arguments")
                            val args = JSONObject(argumentsStr)

                            Log.d("AiAgentClient", "Executing OpenAI Tool: $toolName with args: $argumentsStr")

                            val resultStr = executeLocalTool(context, dao, viewModel, toolName, args)

                            val toolResponse = JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", callId)
                                put("name", toolName)
                                put("content", resultStr)
                            }
                            messages.put(toolResponse)
                        }
                    } else {
                        // No tool calls, we are finished!
                        return@withContext lastAssistantMessage ?: "Keine Textantwort erhalten."
                    }
                }
            } catch (e: Exception) {
                Log.e("AiAgentClient", "Error in OpenAI ReAct loop", e)
                return@withContext "Fehler bei der Verbindung zu OpenAI: ${e.message}"
            }
        }

        return@withContext lastAssistantMessage ?: "Schleife beendet ohne Antwort."
    }

    private suspend fun executeLocalTool(
        context: Context,
        dao: com.example.database.StromrufDao,
        viewModel: com.example.viewmodel.StromrufViewModel,
        toolName: String,
        args: JSONObject
    ): String = withContext(Dispatchers.IO) {
        try {
            when (toolName) {
                "get_contacts" -> {
                    val list = dao.getAllContactsList()
                    val arr = JSONArray()
                    list.forEach {
                        arr.put(JSONObject().apply {
                            put("id", it.id)
                            put("name", it.name)
                            put("phone", it.phone)
                            put("company", it.company ?: "")
                            put("email", it.email ?: "")
                            put("call_reason", it.callReason ?: "")
                            put("is_hot_box", it.isHotBox)
                            put("hot_box_list_name", it.hotBoxListName ?: "")
                        })
                    }
                    arr.toString()
                }
                "create_contact" -> {
                    val name = args.getString("name")
                    val phone = args.getString("phone")
                    val company = args.optString("company", "")
                    val email = args.optString("email", "")
                    val callReason = args.optString("call_reason", "")
                    val isHotBox = args.optBoolean("is_hot_box", false)
                    withContext(Dispatchers.Main) {
                        viewModel.addManualContact(
                            name = name,
                            phone = phone,
                            company = company,
                            email = email,
                            isHotBox = isHotBox,
                            callReason = if (callReason.isEmpty()) null else callReason
                        )
                    }
                    "{\"success\":true}"
                }
                "update_contact" -> {
                    val id = args.getString("id")
                    val existing = dao.getContactById(id)
                    if (existing != null) {
                        val name = if (args.has("name")) args.getString("name") else existing.name
                        val phone = if (args.has("phone")) args.getString("phone") else existing.phone
                        val company = if (args.has("company")) args.getString("company") else existing.company
                        val email = if (args.has("email")) args.getString("email") else existing.email
                        val callReason = if (args.has("call_reason")) args.getString("call_reason") else existing.callReason
                        val isHotBox = if (args.has("is_hot_box")) args.getBoolean("is_hot_box") else existing.isHotBox
                        val hotBoxListName = if (args.has("hot_box_list_name")) args.getString("hot_box_list_name") else existing.hotBoxListName

                        val updated = existing.copy(
                            name = name,
                            phone = phone,
                            company = company,
                            email = email,
                            callReason = callReason,
                            isHotBox = isHotBox,
                            hotBoxListName = hotBoxListName
                        )
                        withContext(Dispatchers.Main) {
                            viewModel.editContact(updated)
                        }
                        "{\"success\":true}"
                    } else {
                        "{\"success\":false,\"error\":\"Contact not found\"}"
                    }
                }
                "delete_contact" -> {
                    val id = args.getString("id")
                    withContext(Dispatchers.Main) {
                        viewModel.deleteContact(id)
                    }
                    "{\"success\":true}"
                }
                "get_followups" -> {
                    val list = dao.getActiveFollowUpsList()
                    val arr = JSONArray()
                    list.forEach {
                        arr.put(JSONObject().apply {
                            put("id", it.id)
                            put("contact_id", it.contactId ?: "")
                            put("contact_name", it.contactName)
                            put("contact_phone", it.contactPhone)
                            put("note", it.note ?: "")
                            put("due_at", it.dueAt)
                            put("is_completed", it.isCompleted)
                            put("call_reason", it.callReason ?: "")
                        })
                    }
                    arr.toString()
                }
                "create_followup" -> {
                    val contactName = args.getString("contact_name")
                    val contactPhone = args.getString("contact_phone")
                    val dueAt = args.getLong("due_at_timestamp")
                    val note = args.optString("note", "")
                    val callReason = args.optString("call_reason", "")
                    withContext(Dispatchers.Main) {
                        viewModel.addManualFollowUp(contactName, contactPhone, note, dueAt, callReason)
                    }
                    "{\"success\":true}"
                }
                "complete_followup" -> {
                    val id = args.getString("id")
                    withContext(Dispatchers.Main) {
                        viewModel.completeFollowUp(id)
                    }
                    "{\"success\":true}"
                }
                "delete_followup" -> {
                    val id = args.getString("id")
                    withContext(Dispatchers.Main) {
                        viewModel.deleteFollowUp(id)
                    }
                    "{\"success\":true}"
                }
                "get_neukunden" -> {
                    val list = dao.getAllNeukundenList()
                    val arr = JSONArray()
                    list.forEach {
                        arr.put(JSONObject().apply {
                            put("id", it.id)
                            put("date_created", it.dateCreated)
                            put("customer_number", it.customerNumber)
                            put("phone", it.phone)
                            put("call_attempts", it.callAttempts)
                            put("status", it.status)
                        })
                    }
                    arr.toString()
                }
                "create_neukunde" -> {
                    val customerNumber = args.getString("customer_number")
                    val phone = args.getString("phone")
                    withContext(Dispatchers.Main) {
                        viewModel.saveNeukunde(customerNumber, phone)
                    }
                    "{\"success\":true}"
                }
                "delete_neukunde" -> {
                    val id = args.getString("id")
                    withContext(Dispatchers.Main) {
                        viewModel.deleteNeukunde(id)
                    }
                    "{\"success\":true}"
                }
                "get_heisse_angebote" -> {
                    val list = dao.getAllHeisseAngeboteList()
                    val arr = JSONArray()
                    list.forEach {
                        arr.put(JSONObject().apply {
                            put("id", it.id)
                            put("date_created", it.dateCreated)
                            put("customer_number", it.customerNumber)
                            put("phone", it.phone)
                            put("call_attempts", it.callAttempts)
                            put("notes", it.notes)
                        })
                    }
                    arr.toString()
                }
                "create_heiss_angebot" -> {
                    val customerNumber = args.getString("customer_number")
                    val phone = args.getString("phone")
                    val notes = args.optString("notes", "")
                    withContext(Dispatchers.Main) {
                        viewModel.saveHeissAngebot(customerNumber, phone, notes)
                    }
                    "{\"success\":true}"
                }
                "delete_heiss_angebot" -> {
                    val id = args.getString("id")
                    withContext(Dispatchers.Main) {
                        viewModel.deleteHeissAngebot(id)
                    }
                    "{\"success\":true}"
                }
                "get_hotbox_lists" -> {
                    val list = SupabaseDbClient.fetchHotBoxLists(context)
                    val arr = JSONArray()
                    list.forEach { arr.put(it) }
                    arr.toString()
                }
                "create_hotbox_list" -> {
                    val name = args.getString("name")
                    withContext(Dispatchers.Main) {
                        viewModel.addHotBoxList(name)
                    }
                    "{\"success\":true}"
                }
                "delete_hotbox_list" -> {
                    val name = args.getString("name")
                    withContext(Dispatchers.Main) {
                        viewModel.removeHotBoxList(name)
                    }
                    "{\"success\":true}"
                }
                "get_call_logs" -> {
                    val list = dao.getAllCallLogsList()
                    val arr = JSONArray()
                    list.take(20).forEach {
                        arr.put(JSONObject().apply {
                            put("id", it.id)
                            put("phone", it.phone)
                            put("contact_name", it.contactName ?: "")
                            put("outcome", it.outcome)
                            put("note", it.note ?: "")
                            put("timestamp", it.timestamp)
                            put("duration_seconds", it.durationSeconds)
                            put("call_reason", it.callReason ?: "")
                            put("call_type", it.callType)
                        })
                    }
                    arr.toString()
                }
                else -> "{\"error\":\"Unknown tool $toolName\"}"
            }
        } catch (e: Exception) {
            Log.e("AiAgentClient", "Error executing tool $toolName", e)
            "{\"error\":\"${e.localizedMessage}\"}"
        }
    }

    private fun getGeminiToolsJson(): JSONArray {
        val functionDeclarations = JSONArray()

        functionDeclarations.put(JSONObject().apply {
            put("name", "get_contacts")
            put("description", "Liefert die Liste aller Kontakte und Kunden in der App.")
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "create_contact")
            put("description", "Erstellt einen neuen Kontakt/Kunden in der App.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "STRING"); put("description", "Name des Kontakts") })
                    put("phone", JSONObject().apply { put("type", "STRING"); put("description", "Telefonnummer des Kontakts") })
                    put("company", JSONObject().apply { put("type", "STRING"); put("description", "Optional: Firmenname") })
                    put("email", JSONObject().apply { put("type", "STRING"); put("description", "Optional: E-Mail") })
                    put("call_reason", JSONObject().apply { put("type", "STRING"); put("description", "Optional: Anrufgrund") })
                    put("is_hot_box", JSONObject().apply { put("type", "BOOLEAN"); put("description", "Optional: Markiere als Hot-Box") })
                })
                put("required", JSONArray().apply { put("name"); put("phone") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "update_contact")
            put("description", "Aktualisiert einen bestehenden Kontakt/Kunden.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "STRING"); put("description", "ID des Kontakts") })
                    put("name", JSONObject().apply { put("type", "STRING") })
                    put("phone", JSONObject().apply { put("type", "STRING") })
                    put("company", JSONObject().apply { put("type", "STRING") })
                    put("email", JSONObject().apply { put("type", "STRING") })
                    put("call_reason", JSONObject().apply { put("type", "STRING") })
                    put("is_hot_box", JSONObject().apply { put("type", "BOOLEAN") })
                })
                put("required", JSONArray().apply { put("id") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "delete_contact")
            put("description", "Löscht einen Anrufkontakt/Kunden anhand der ID.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("id") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "get_followups")
            put("description", "Liefert alle ausstehenden Termine/Wiedervorlagen.")
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "create_followup")
            put("description", "Erstellt einen neuen Termin / eine Wiedervorlage.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("contact_name", JSONObject().apply { put("type", "STRING") })
                    put("contact_phone", JSONObject().apply { put("type", "STRING") })
                    put("due_at_timestamp", JSONObject().apply { put("type", "INTEGER"); put("description", "Datum und Uhrzeit als Epoch-Millisekunden (Long)") })
                    put("note", JSONObject().apply { put("type", "STRING") })
                    put("call_reason", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("contact_name"); put("contact_phone"); put("due_at_timestamp") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "complete_followup")
            put("description", "Markiert eine Wiedervorlage als erledigt.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("id") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "delete_followup")
            put("description", "Löscht eine Wiedervorlage anhand der ID.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("id") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "get_neukunden")
            put("description", "Liefert die Liste aller Neukunden.")
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "create_neukunde")
            put("description", "Erstellt einen neuen Neukunden.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("customer_number", JSONObject().apply { put("type", "STRING") })
                    put("phone", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("customer_number"); put("phone") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "delete_neukunde")
            put("description", "Löscht einen Neukunden anhand der ID.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("id") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "get_heisse_angebote")
            put("description", "Liefert die Liste aller Heißen Angebote (Hot Deals).")
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "create_heiss_angebot")
            put("description", "Erstellt ein neues Heißes Angebot (Hot Deal).")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("customer_number", JSONObject().apply { put("type", "STRING") })
                    put("phone", JSONObject().apply { put("type", "STRING") })
                    put("notes", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("customer_number"); put("phone") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "delete_heiss_angebot")
            put("description", "Löscht ein Heißes Angebot anhand der ID.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("id", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("id") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "get_hotbox_lists")
            put("description", "Liefert die Namen aller Kampagnen/Hotbox-Listen.")
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "create_hotbox_list")
            put("description", "Erstellt eine neue Hotbox-Liste.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("name") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "delete_hotbox_list")
            put("description", "Löscht eine Hotbox-Liste.")
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("name", JSONObject().apply { put("type", "STRING") })
                })
                put("required", JSONArray().apply { put("name") })
            })
        })

        functionDeclarations.put(JSONObject().apply {
            put("name", "get_call_logs")
            put("description", "Liefert die letzten 20 geführten Anrufe (Call Logs).")
        })

        val tools = JSONArray()
        tools.put(JSONObject().apply {
            put("functionDeclarations", functionDeclarations)
        })
        return tools
    }

    suspend fun executeGeminiInstruction(
        context: Context,
        viewModel: com.example.viewmodel.StromrufViewModel,
        instruction: String,
        geminiApiKey: String
    ): String = withContext(Dispatchers.IO) {
        val currentDateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())
        val systemPrompt = """
            Du bist der STROMRUF KI-Assistent. Du hast die volle Kontrolle über die Daten der App (Kontakte, Wiedervorlagen/Termine, Neukunden, Heiße Angebote, Hotbox-Listen).
            Du steuerst die App durch Funktionsaufrufe (Tools).
            Führe alle vom Benutzer gewünschten Aktionen präzise aus. Wenn eine Aktion unvollständige Daten hat, frage den Benutzer danach oder verwende sinnvolle Standardwerte.

            WICHTIG FÜR ZEITEN UND TERMINE:
            Das aktuelle Datum und Uhrzeit ist: $currentDateTime.
            Wenn der Benutzer Termine oder Wiedervorlagen für relative Angaben (z.B. "morgen um 15 Uhr", "am Montag", "in 2 Tagen") erstellt, berechne den genauen Epoch-Millisekunden-Zeitstempel (Long) basierend auf diesem aktuellen Datum und übergib ihn an das Tool.

            Antworte immer auf Deutsch, professionell und präzise.
        """.trimIndent()

        val contents = JSONArray()
        contents.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply {
                    put("text", instruction)
                })
            })
        })

        val dao = com.example.database.AppDatabase.getDatabase(context).stromrufDao()
        val toolsJson = getGeminiToolsJson()
        var maxIterations = 5
        var lastAssistantText = "Keine Antwort von Gemini erhalten."

        while (maxIterations > 0) {
            maxIterations--

            val requestBody = JSONObject().apply {
                put("contents", contents)
                put("tools", toolsJson)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$geminiApiKey")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val err = response.body?.string() ?: "Unknown error"
                        Log.e("AiAgentClient", "Gemini Error: ${response.code} - $err")
                        return@withContext "Gemini API Fehler (${response.code}): $err"
                    }

                    val responseBody = response.body?.string() ?: "{}"
                    val root = JSONObject(responseBody)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates == null || candidates.length() == 0) {
                        return@withContext "Keine Antwort von Gemini erhalten (keine Kandidaten)."
                    }

                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.optJSONObject("content") ?: return@withContext "Kein Inhalt in Gemini-Kandidat gefunden."
                    val parts = contentObj.optJSONArray("parts") ?: JSONArray()

                    var hasToolCall = false
                    var toolCallObj: JSONObject? = null
                    var textResponse: String? = null

                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("text")) {
                            textResponse = part.getString("text")
                        }
                        if (part.has("functionCall")) {
                            hasToolCall = true
                            toolCallObj = part.getJSONObject("functionCall")
                        }
                    }

                    if (textResponse != null) {
                        lastAssistantText = textResponse
                    }

                    if (hasToolCall && toolCallObj != null) {
                        val toolName = toolCallObj.getString("name")
                        val args = toolCallObj.optJSONObject("args") ?: JSONObject()

                        Log.d("AiAgentClient", "Executing Gemini Tool: $toolName with args: $args")
                        val resultStr = executeLocalTool(context, dao, viewModel, toolName, args)

                        // For Gemini, we append the model's functionCall turn directly
                        contents.put(contentObj)

                        // Then, append the functionResponse turn under role user
                        val userTurn = JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("functionResponse", JSONObject().apply {
                                        put("name", toolName)
                                        put("response", JSONObject().apply {
                                            put("output", resultStr)
                                        })
                                    })
                                })
                            })
                        }
                        contents.put(userTurn)
                    } else {
                        // No tool calls, we are finished!
                        return@withContext lastAssistantText
                    }
                }
            } catch (e: Exception) {
                Log.e("AiAgentClient", "Error in Gemini loop", e)
                return@withContext "Fehler bei der Verbindung zu Gemini: ${e.message}"
            }
        }

        return@withContext lastAssistantText
    }

    fun getGeminiApiKey(context: Context): String {
        val prefs = context.getSharedPreferences("stromruf_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        val cleaned = savedKey.replace("\\s".toRegex(), "")
        if (cleaned.isNotBlank()) {
            return cleaned
        }
        val defaultKey = "AQ.Ab8RN6Ky691PKx30IW9i3nVD-CaBWLQ0TQlTGEwRrhuoxLs9cQ".replace("\\s".toRegex(), "")
        return defaultKey
    }
}
