package com.example.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.database.AppDatabase
import com.example.repository.StromrufRepository
import com.example.service.AgentCallService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Calendar

/**
 * Zentrale Laufzeit: lädt Konfiguration + Agenten aus Supabase, registriert
 * den SIP-Trunk, verteilt eingehende Anrufe EINER Nummer auf mehrere Agenten,
 * startet ausgehende Anrufe und arbeitet Kampagnen (Hotbox) ab.
 */
object AgentRuntime {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sessions = MutableStateFlow<List<AgentSession>>(emptyList())
    val sessions: StateFlow<List<AgentSession>> = _sessions
    private val _agents = MutableStateFlow<List<AgentProfile>>(emptyList())
    val agents: StateFlow<List<AgentProfile>> = _agents
    private val _config = MutableStateFlow(RuntimeConfig())
    val config: StateFlow<RuntimeConfig> = _config
    private val _geladen = MutableStateFlow(false)
    val geladen: StateFlow<Boolean> = _geladen
    private val _campaigns = MutableStateFlow<List<Campaign>>(emptyList())
    val campaigns: StateFlow<List<Campaign>> = _campaigns

    private val callMap = mutableMapOf<SipCall, AgentSession>()
    private var rrIndex = 0
    private var tickerLaeuft = false

    fun init(context: Context) {
        appContext = context.applicationContext
        SipEngine.init(appContext)
        SipEngine.onIncomingCall = { call -> routeEingehend(call) }
        SipEngine.onCallEnded = { call -> callMap.remove(call)?.stop(); serviceStoppenWennLeer() }
        aktualisieren()
        starteKampagnenTicker()
    }

    fun aktualisieren(onFertig: (() -> Unit)? = null) {
        scope.launch {
            AgentBackend.fetchConfig(appContext)?.let { cfg ->
                _config.value = cfg
                if (cfg.sipUser.isNotBlank() && cfg.sipDomain.isNotBlank())
                    withContext(Dispatchers.Main) { SipEngine.register(cfg) }
            }
            AgentBackend.fetchAgents(appContext)?.let { _agents.value = it }
            _campaigns.value = AgentBackend.fetchCampaigns(appContext)
            _geladen.value = true
            onFertig?.invoke()
        }
    }

    fun speichereKonfiguration(cfg: RuntimeConfig, onFertig: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = AgentBackend.saveConfig(appContext, cfg)
            if (ok) {
                _config.value = cfg
                withContext(Dispatchers.Main) { SipEngine.register(cfg) }
            }
            withContext(Dispatchers.Main) { onFertig(ok) }
        }
    }

    fun speichereAgent(a: AgentProfile, onFertig: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = AgentBackend.upsertAgent(appContext, a)
            if (ok) {
                val list = _agents.value.toMutableList()
                val i = list.indexOfFirst { it.id == a.id }
                if (i >= 0) list[i] = a else list += a
                _agents.value = list
            }
            withContext(Dispatchers.Main) { onFertig(ok) }
        }
    }

    fun loescheAgent(id: String) {
        scope.launch {
            if (AgentBackend.deleteAgent(appContext, id))
                _agents.value = _agents.value.filterNot { it.id == id }
        }
    }

    fun ladeKampagnen() {
        scope.launch { _campaigns.value = AgentBackend.fetchCampaigns(appContext) }
    }

    // ---------------- Kapazität ----------------
    private fun aktiveFuer(agentId: String) =
        _sessions.value.count { it.agent.id == agentId && it.status.value.aktiv }
    private fun aktiveFuerKampagne(campaignId: String) =
        _sessions.value.count { it.campaignId == campaignId && it.status.value.aktiv }
    private fun freieAgenten(richtung: String) = _agents.value.filter {
        it.isActive && (it.direction == "beide" || it.direction == richtung) &&
                aktiveFuer(it.id) < it.maxParallel
    }

    // ---------------- Eingehend: eine Nummer, mehrere Agenten ----------------
    private fun routeEingehend(call: SipCall) {
        val cfg = _config.value
        val kandidaten = freieAgenten("eingehend")
        val agent = when (cfg.routingStrategy) {
            "fester_agent" -> kandidaten.find { it.id == cfg.fixedAgentId } ?: kandidaten.firstOrNull()
            "geringste_auslastung" -> kandidaten.minByOrNull { aktiveFuer(it.id) }
            else -> if (kandidaten.isEmpty()) null else kandidaten[(rrIndex++) % kandidaten.size]
        }
        if (agent == null) { SipEngine.beenden(call); return }
        val nummer = call.remoteNumber.ifBlank { "Unbekannt" }
        serviceStarten()
        scope.launch {
            val kontakt = findeKontakt(nummer)
            val wissen = if (agent.useKnowledge)
                AgentBackend.knowledgeTextFor(appContext, agent.id) else ""
            val session = AgentSession(
                context = appContext, agent = agent, mode = SessionMode.SIP,
                direction = "eingehend", remoteNumber = nummer,
                contactId = kontakt?.first, contactName = kontakt?.second,
                wissen = wissen, cfg = cfg, call = call, scope = scope
            )
            callMap[call] = session
            registriere(session)
            withContext(Dispatchers.Main) {
                SipEngine.accept(call,
                    if (cfg.recordingEnabled) session.recordFile.absolutePath else null)
            }
            session.start()
        }
    }

    // ---------------- Ausgehend ----------------
    fun rufeAn(
        agent: AgentProfile, nummer: String,
        kontaktName: String? = null, kontaktId: String? = null,
        campaign: Campaign? = null, campaignCall: CampaignCall? = null,
        onFehler: (String?) -> Unit = {}
    ) {
        val cfg = _config.value
        if (aktiveFuer(agent.id) >= agent.maxParallel) {
            onFehler("${agent.name} ist ausgelastet."); return
        }
        if (SipEngine.aktiveCalls() >= cfg.sipMaxLines) {
            onFehler("Alle ${cfg.sipMaxLines} SIP-Leitungen belegt."); return
        }
        if (SipEngine.status.value != "Registriert") {
            onFehler("SIP-Trunk nicht registriert – Einrichtung prüfen."); return
        }
        serviceStarten()
        scope.launch {
            val kontakt = if (kontaktId != null) kontaktId to (kontaktName ?: nummer)
                          else findeKontakt(nummer)
            val wissen = if (agent.useKnowledge)
                AgentBackend.knowledgeTextFor(appContext, agent.id) else ""
            val tmp = File(appContext.cacheDir, "out_${System.nanoTime()}.wav")
            val call = withContext(Dispatchers.Main) {
                SipEngine.invite(nummer, if (cfg.recordingEnabled) tmp.absolutePath else null)
            }
            if (call == null) {
                withContext(Dispatchers.Main) { onFehler("Anruf konnte nicht aufgebaut werden.") }
                return@launch
            }
            val session = AgentSession(
                context = appContext, agent = agent, mode = SessionMode.SIP,
                direction = "ausgehend", remoteNumber = nummer,
                contactId = kontakt?.first, contactName = kontakt?.second,
                campaignId = campaign?.id, campaignCallId = campaignCall?.id,
                campaignAttempts = campaignCall?.attempts?.plus(1) ?: 0,
                campaignMaxAttempts = campaign?.maxAttempts ?: 2,
                campaignAnlass = campaign?.let { "Kampagne '${it.name}' (Hotbox-Rückruf)" },
                wissen = wissen, cfg = cfg, call = call, scope = scope
            )
            callMap[call] = session
            registriere(session)
            session.status.value = SessionStatus.KLINGELT
            session.start()
            withContext(Dispatchers.Main) { onFehler(null) }
        }
    }

    /** Gerätetest: komplettes Gespräch über Mikro/Lautsprecher – ohne SIP. */
    fun starteGeraetetest(agent: AgentProfile) {
        scope.launch {
            val wissen = if (agent.useKnowledge)
                AgentBackend.knowledgeTextFor(appContext, agent.id) else ""
            val session = AgentSession(
                context = appContext, agent = agent, mode = SessionMode.GERAETETEST,
                direction = "geraetetest", remoteNumber = "Gerätetest",
                contactId = null, contactName = null,
                wissen = wissen, cfg = _config.value, call = null, scope = scope
            )
            registriere(session)
            session.start()
        }
    }

    // ---------------- Kampagnen-Ticker ----------------
    private fun starteKampagnenTicker() {
        if (tickerLaeuft) return
        tickerLaeuft = true
        scope.launch {
            while (true) {
                delay(20_000)
                runCatching { kampagnenTick() }
            }
        }
    }

    private suspend fun kampagnenTick() {
        if (!_geladen.value || SipEngine.status.value != "Registriert") return
        val stunde = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val cfg = _config.value
        for (c in _campaigns.value.filter { it.status == "aktiv" }) {
            if (stunde < c.startHour || stunde >= c.endHour) continue
            val agent = _agents.value.find { it.id == c.agentId } ?: continue
            if (!agent.isActive) continue
            while (aktiveFuerKampagne(c.id) < c.maxParallel &&
                   aktiveFuer(agent.id) < agent.maxParallel &&
                   SipEngine.aktiveCalls() < cfg.sipMaxLines) {
                val next = AgentBackend.claimNextCampaignCall(appContext, c) ?: run {
                    if (aktiveFuerKampagne(c.id) == 0) {
                        AgentBackend.setCampaignStatus(appContext, c.id, "fertig")
                        ladeKampagnen()
                    }
                    return@run null
                } ?: break
                Log.d("AgentRuntime", "Kampagne ${c.name}: rufe ${next.contactName} an")
                rufeAn(agent, next.phone, next.contactName, next.contactId, c, next)
                delay(2_000)
            }
        }
    }

    fun beende(session: AgentSession) {
        session.stop()
        callMap.entries.removeAll { it.value == session }
        serviceStoppenWennLeer()
    }

    fun meldeFertig(session: AgentSession) { serviceStoppenWennLeer() }

    fun raeumeAuf() { _sessions.value = _sessions.value.filter { it.status.value.aktiv } }

    private fun registriere(s: AgentSession) { _sessions.value = _sessions.value + s }

    /** Rufnummer gegen die STROMRUF-Kontakte matchen (id to name). */
    private suspend fun findeKontakt(nummer: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = AppDatabase.getDatabase(appContext).stromrufDao()
                val repo = StromrufRepository(appContext, dao)
                repo.getContactByPhone(nummer)?.let { it.id to it.name }
            }.getOrNull()
        }

    private fun serviceStarten() = runCatching {
        appContext.startForegroundService(Intent(appContext, AgentCallService::class.java))
    }
    private fun serviceStoppenWennLeer() {
        if (_sessions.value.none { it.status.value.aktiv })
            runCatching { appContext.stopService(Intent(appContext, AgentCallService::class.java)) }
    }
}
