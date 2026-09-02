package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.CallLogEntity
import com.example.database.ContactEntity
import com.example.database.FollowUpEntity
import com.example.database.AiCallEntity
import com.example.database.AnnahmeEntity
import com.example.repository.StromrufRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import android.net.Uri
import com.example.util.OpenAiClient
import com.example.util.CustomerMailSender
import com.example.database.CustomerMessageEntity
import kotlinx.coroutines.delay
import java.util.*

data class CustomerMessageDraftState(
    val rawNote: String = "",
    val transcript: String = "",
    val subject: String = "",
    val body: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ClipboardBubbleState(
    val text: String,
    val isCustomerNumber: Boolean
)

data class QuickSaveDialogState(
    val phone: String = "",
    val customerNo: String = "",
    val name: String = ""
)

data class ActiveCall(
    val name: String?,
    val phone: String,
    val contactId: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val callType: String = "einwaehlen"
)

data class IncomingAlert(
    val id: String,
    val name: String,
    val phone: String
)

data class WrapUpData(
    val phone: String = "",
    val name: String = "",
    val company: String = "",
    val customerNumber: String = "",
    val email: String = "",
    val note: String = "",
    val outcome: String = "",
    val saveContact: Boolean = false,
    val isHotBox: Boolean = false,
    val hotBoxStartHour: Int? = null,
    val hotBoxEndHour: Int? = null,
    val hotBoxWeekdays: String? = null,
    val selectedOffsets: Set<String> = emptySet(),
    val customDates: List<Long> = emptyList(),
    val existingContact: ContactEntity? = null,
    val durationSeconds: Long = 0L,
    val callReason: String? = null,
    val callType: String = "einwaehlen"
)

class StromrufViewModel(private val repository: StromrufRepository) : ViewModel() {


    // Callback handlers injected from MainActivity/Application to handle alarm scheduling elegantly
    var onScheduleAlarm: ((id: String, name: String, phone: String, dueAt: Long) -> Unit)? = null
    var onCancelAlarm: ((id: String) -> Unit)? = null

    private val _newAnnahmeDocumentAlert =
        MutableStateFlow<com.example.database.AnnahmeDokumentEntity?>(null)

    val newAnnahmeDocumentAlert:
        StateFlow<com.example.database.AnnahmeDokumentEntity?> =
        _newAnnahmeDocumentAlert.asStateFlow()

    init {
        viewModelScope.launch {
            var initialized = false
            var knownIds = emptySet<String>()
            try {
                knownIds = repository.allAnnahmeDokumente.first().map { it.id }.toSet()
                if (knownIds.isNotEmpty()) {
                    initialized = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            while (true) {
                try {
                    val remote = repository.syncAnnahmeDokumenteFromSupabase()
                    val currentIds = remote.map { it.id }.toSet()

                    // Beim ersten Start keine Benachrichtigung zeigen.
                    // Erst spätere neue Dateien auslösen.
                    if (initialized) {
                        val newItems = remote.filter { it.id !in knownIds }
                        if (newItems.isNotEmpty()) {
                            _newAnnahmeDocumentAlert.value = newItems.first()
                        }
                    }

                    knownIds = currentIds
                    initialized = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Alle 15 Sekunden Supabase prüfen
                delay(15_000)
            }
        }
    }

    // --- Core states from DB ---
    val contacts: StateFlow<List<ContactEntity>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeFollowUps: StateFlow<List<FollowUpEntity>> = repository.activeFollowUps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callLogs: StateFlow<List<CallLogEntity>> = repository.allCallLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiCalls: StateFlow<List<AiCallEntity>> = repository.allAiCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val annahmen: StateFlow<List<AnnahmeEntity>> = repository.allAnnahmen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val promisedAnnahmen: StateFlow<List<com.example.database.PromisedAnnahmeEntity>> = repository.allPromisedAnnahmen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val annahmeDokumente: StateFlow<List<com.example.database.AnnahmeDokumentEntity>> = repository.allAnnahmeDokumente
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val neukunden: StateFlow<List<com.example.database.NeukundeEntity>> = repository.allNeukunden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heisseAngebote: StateFlow<List<com.example.database.HeissAngebotEntity>> = repository.allHeisseAngebote
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPromisedThroughCallActive = MutableStateFlow(false)
    val promisedThroughCallCountdown = MutableStateFlow<Int?>(null)

    fun saveAiCall(aiCall: AiCallEntity) {
        viewModelScope.launch {
            repository.insertAiCall(aiCall)
        }
    }

    fun deleteAiCall(id: String) {
        viewModelScope.launch {
            repository.deleteAiCallById(id)
        }
    }

    fun saveAnnahme(type: String, customerType: String, consumption: Long, termYears: Int, customerNumber: String) {
        viewModelScope.launch {
            val annahme = AnnahmeEntity(
                id = java.util.UUID.randomUUID().toString(),
                type = type,
                customerType = customerType,
                consumption = consumption,
                termYears = termYears,
                customerNumber = customerNumber,
                timestamp = System.currentTimeMillis()
            )
            repository.insertAnnahme(annahme)
        }
    }

    fun deleteAnnahme(id: String) {
        viewModelScope.launch {
            repository.deleteAnnahmeById(id)
        }
    }

    fun saveAnnahmeDokument(customerNumber: String, fileName: String, fileType: String, fileContentString: String, localFilePath: String) {
        viewModelScope.launch {
            val doc = com.example.database.AnnahmeDokumentEntity(
                id = java.util.UUID.randomUUID().toString(),
                customerNumber = customerNumber,
                fileName = fileName,
                fileType = fileType,
                fileContentString = fileContentString,
                localFilePath = localFilePath,
                timestamp = System.currentTimeMillis()
            )
            repository.insertAnnahmeDokument(doc)
        }
    }

    fun deleteAnnahmeDokument(id: String) {
        viewModelScope.launch {
            repository.deleteAnnahmeDokumentById(id)
        }
    }

    fun saveNeukunde(
        customerNumber: String,
        phone: String,
        customerName: String? = null,
        company: String? = null,
        email: String? = null,
        deliveryAddress: String? = null,
        meterNumber: String? = null,
        consumption: Long? = null,
        energyType: String? = null,
        routine: String = "Keine"
    ) {
        viewModelScope.launch {
            val item = com.example.database.NeukundeEntity(
                id = java.util.UUID.randomUUID().toString(),
                dateCreated = System.currentTimeMillis(),
                customerNumber = customerNumber,
                phone = phone,
                callAttempts = 0,
                status = "Anrufen",
                customerName = customerName,
                company = company,
                email = email,
                deliveryAddress = deliveryAddress,
                meterNumber = meterNumber,
                consumption = consumption,
                energyType = energyType
            )
            repository.insertNeukunde(item)

            if (routine == "Nicht erreicht") {
                val dueAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
                addManualFollowUp(
                    name = customerName ?: "Kd. $customerNumber",
                    phone = phone,
                    note = "Wiedervorlage nach Anrufversuch (Routine: Angerufen - nicht erreicht)",
                    dueAt = dueAt,
                    callReason = "Anrufen"
                )
            } else if (routine == "Datenmail") {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 14)
                calendar.set(java.util.Calendar.MINUTE, 55)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                addManualFollowUp(
                    name = customerName ?: "Kd. $customerNumber",
                    phone = phone,
                    note = "Angebot schicken (Routine: Datenmail)",
                    dueAt = calendar.timeInMillis,
                    callReason = "Angebot erstellen"
                )
            }
        }
    }

    private fun handleCallAttemptRoutineInternal(name: String, phone: String, newAttempts: Int) {
        // Disabled to prevent automatic follow-ups after call
    }

    fun incrementNeukundeCallAttempts(neukunde: com.example.database.NeukundeEntity) {
        viewModelScope.launch {
            val newAttempts = neukunde.callAttempts + 1
            repository.insertNeukunde(neukunde.copy(callAttempts = newAttempts))
        }
    }

    fun advanceNeukundeStatus(neukunde: com.example.database.NeukundeEntity, onRemoved: () -> Unit = {}) {
        viewModelScope.launch {
            val nextStatus = when (neukunde.status) {
                "Anrufen" -> "Datenmail schreiben"
                "Datenmail schreiben" -> "Angebot erstellen"
                "Angebot erstellen" -> "Zum Stand fragen"
                else -> "Zum Stand fragen"
            }
            if (nextStatus == "Zum Stand fragen") {
                repository.deleteNeukundeById(neukunde.id)
                onRemoved()
            } else {
                repository.insertNeukunde(neukunde.copy(status = nextStatus))
            }
        }
    }

    fun deleteNeukunde(id: String) {
        viewModelScope.launch {
            repository.deleteNeukundeById(id)
        }
    }

    fun saveHeissAngebot(customerNumber: String, phone: String, notes: String = "") {
        viewModelScope.launch {
            val item = com.example.database.HeissAngebotEntity(
                id = java.util.UUID.randomUUID().toString(),
                dateCreated = System.currentTimeMillis(),
                customerNumber = customerNumber,
                phone = phone,
                callAttempts = 0,
                notes = notes
            )
            repository.insertHeissAngebot(item)
        }
    }

    fun incrementHeissAngebotCallAttempts(item: com.example.database.HeissAngebotEntity) {
        viewModelScope.launch {
            repository.insertHeissAngebot(item.copy(callAttempts = item.callAttempts + 1))
        }
    }

    fun deleteHeissAngebot(id: String) {
        viewModelScope.launch {
            repository.deleteHeissAngebotById(id)
        }
    }

    // --- Search & UI Filters ---
    private val _contactSearchQuery = MutableStateFlow("")
    val contactSearchQuery: StateFlow<String> = _contactSearchQuery.asStateFlow()

    fun searchContacts(query: String) {
        _contactSearchQuery.value = query
    }

    // --- Active Calls & Wrap-up States ---
    private val _activeCall = MutableStateFlow<ActiveCall?>(null)
    val activeCall: StateFlow<ActiveCall?> = _activeCall.asStateFlow()

    private val _showWrapUpDialog = MutableStateFlow(false)
    val showWrapUpDialog: StateFlow<Boolean> = _showWrapUpDialog.asStateFlow()

    private val _wrapUpData = MutableStateFlow(WrapUpData())
    val wrapUpData: StateFlow<WrapUpData> = _wrapUpData.asStateFlow()

    private val _selectedHotBoxListName = MutableStateFlow<String>("")
    val selectedHotBoxListName: StateFlow<String> = _selectedHotBoxListName.asStateFlow()

    private val _selectedHotBoxListNames = MutableStateFlow<Set<String>>(emptySet())
    val selectedHotBoxListNames: StateFlow<Set<String>> = _selectedHotBoxListNames.asStateFlow()

    private val _hotBoxLists = MutableStateFlow<Set<String>>(emptySet())
    val hotBoxLists: StateFlow<Set<String>> = _hotBoxLists.asStateFlow()

    private val _lastCalledHotBoxContactId = MutableStateFlow<String?>(null)
    val lastCalledHotBoxContactId: StateFlow<String?> = _lastCalledHotBoxContactId.asStateFlow()

    private val _nextHotBoxContactId = MutableStateFlow<String?>(null)
    val nextHotBoxContactId: StateFlow<String?> = _nextHotBoxContactId.asStateFlow()

    private val _clipboardBubbleState = MutableStateFlow<ClipboardBubbleState?>(null)
    val clipboardBubbleState: StateFlow<ClipboardBubbleState?> = _clipboardBubbleState.asStateFlow()

    fun showClipboardBubble(text: String, isCustomerNumber: Boolean) {
        _clipboardBubbleState.value = ClipboardBubbleState(text, isCustomerNumber)
    }

    fun clearClipboardBubble() {
        _clipboardBubbleState.value = null
    }

    private val _saveNumberBubblePhone = MutableStateFlow<String?>(null)
    val saveNumberBubblePhone: StateFlow<String?> = _saveNumberBubblePhone.asStateFlow()

    fun showSaveNumberBubble(phone: String) {
        _saveNumberBubblePhone.value = phone
    }

    fun clearSaveNumberBubble() {
        _saveNumberBubblePhone.value = null
    }

    private val _quickSaveDialogState = MutableStateFlow<QuickSaveDialogState?>(null)
    val quickSaveDialogState: StateFlow<QuickSaveDialogState?> = _quickSaveDialogState.asStateFlow()

    fun openQuickSaveDialog(phone: String = "", customerNo: String = "", name: String = "") {
        _quickSaveDialogState.value = QuickSaveDialogState(phone, customerNo, name)
    }

    fun updateQuickSaveDialog(customerNo: String? = null, name: String? = null, phone: String? = null) {
        val current = _quickSaveDialogState.value ?: return
        _quickSaveDialogState.value = current.copy(
            customerNo = customerNo ?: current.customerNo,
            name = name ?: current.name,
            phone = phone ?: current.phone
        )
    }

    fun closeQuickSaveDialog() {
        _quickSaveDialogState.value = null
    }

    init {
        checkAndMovePassiveFollowUpsToHotBox()
        setupHotBoxQueueObservers()
    }

    fun getEffectiveHotBoxListName(name: String?): String {
        val lists = _hotBoxLists.value
        val defaultList = lists.firstOrNull() ?: "Hotbox"
        if (name.isNullOrBlank() || name == "Standard Hotbox") {
            return defaultList
        }
        if (name == "Passive Hotbox") {
            return if ("Passive" in lists) "Passive" else defaultList
        }
        return name
    }

    fun initializeHotBoxLists(lists: Set<String>, selected: Set<String>) {
        val cleanLists = lists.filter { it != "Standard Hotbox" && it != "Passive Hotbox" }.toSet()
        val finalLists = if (cleanLists.isEmpty()) setOf("Hotbox") else cleanLists
        _hotBoxLists.value = finalLists
        
        val cleanSelected = selected.filter { it != "Standard Hotbox" && it != "Passive Hotbox" && it in finalLists }.toSet()
        val finalSelected = if (cleanSelected.isEmpty()) setOf(finalLists.first()) else cleanSelected
        _selectedHotBoxListNames.value = finalSelected
        _selectedHotBoxListName.value = finalSelected.first()
    }

    fun selectHotBoxList(name: String) {
        _selectedHotBoxListNames.value = setOf(name)
        _selectedHotBoxListName.value = name
    }

    fun toggleHotBoxListSelection(name: String) {
        val current = _selectedHotBoxListNames.value
        val next = if (current.contains(name)) {
            if (current.size > 1) {
                current - name
            } else {
                current
            }
        } else {
            current + name
        }
        _selectedHotBoxListNames.value = next
        _selectedHotBoxListName.value = next.firstOrNull() ?: name
    }

    private fun setupHotBoxQueueObservers() {
        viewModelScope.launch {
            combine(contacts, _selectedHotBoxListNames) { allContacts, activeLists ->
                val hotContacts = allContacts.filter { contact ->
                    contact.isHotBox && getEffectiveHotBoxListName(contact.hotBoxListName) in activeLists
                }
                val cal = Calendar.getInstance()
                val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                val activeHotContacts = hotContacts.filter { contact ->
                    val weekdaysStr = contact.hotBoxWeekdays
                    if (!weekdaysStr.isNullOrEmpty()) {
                        val daysList = weekdaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                        if (daysList.isNotEmpty() && currentDay !in daysList) {
                            return@filter false
                        }
                    }

                    val start = contact.hotBoxStartHour
                    val end = contact.hotBoxEndHour
                    if (start != null && end != null) {
                        if (start <= end) {
                            currentHour in start..end
                        } else {
                            currentHour >= start || currentHour <= end
                        }
                    } else {
                        true
                    }
                }
                val contactsToUse = if (activeHotContacts.isNotEmpty()) {
                    activeHotContacts
                } else {
                    hotContacts
                }
                val uncalled = contactsToUse.filter { !it.hasBeenCalledInHotCycle }
                val nextId = _nextHotBoxContactId.value
                val stillValid = uncalled.any { it.id == nextId }
                if (!stillValid) {
                    if (uncalled.isNotEmpty()) {
                        _nextHotBoxContactId.value = uncalled.random().id
                    } else if (contactsToUse.isNotEmpty()) {
                        _nextHotBoxContactId.value = contactsToUse.random().id
                    } else {
                        _nextHotBoxContactId.value = null
                    }
                }
            }.collect {}
        }
    }

    fun skipNextHotBoxContact() {
        viewModelScope.launch {
            val activeLists = _selectedHotBoxListNames.value
            val hotContacts = (contacts.value ?: emptyList()).filter { contact ->
                contact.isHotBox && getEffectiveHotBoxListName(contact.hotBoxListName) in activeLists
            }
            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentDay = cal.get(Calendar.DAY_OF_WEEK)
            val activeHotContacts = hotContacts.filter { contact ->
                val weekdaysStr = contact.hotBoxWeekdays
                if (!weekdaysStr.isNullOrEmpty()) {
                    val daysList = weekdaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (daysList.isNotEmpty() && currentDay !in daysList) {
                        return@filter false
                    }
                }

                val start = contact.hotBoxStartHour
                val end = contact.hotBoxEndHour
                if (start != null && end != null) {
                    if (start <= end) {
                        currentHour in start..end
                    } else {
                        currentHour >= start || currentHour <= end
                    }
                } else {
                    true
                }
            }
            val contactsToUse = if (activeHotContacts.isNotEmpty()) activeHotContacts else hotContacts
            val uncalled = contactsToUse.filter { !it.hasBeenCalledInHotCycle }
            
            val currentNextId = _nextHotBoxContactId.value
            val candidates = if (uncalled.isNotEmpty()) uncalled else contactsToUse
            val filteredCandidates = candidates.filter { it.id != currentNextId }
            
            val chosen = if (filteredCandidates.isNotEmpty()) {
                filteredCandidates.random()
            } else if (candidates.isNotEmpty()) {
                candidates.random()
            } else {
                null
            }
            
            _nextHotBoxContactId.value = chosen?.id
        }
    }

    private var isFirstListLoad = true

    fun setHotBoxLists(lists: Set<String>) {
        val cleanLists = lists.filter { it != "Standard Hotbox" && it != "Passive Hotbox" }.toSet()
        if (cleanLists.isNotEmpty()) {
            val current = _hotBoxLists.value
            val merged = LinkedHashSet<String>()
            merged.addAll(current)
            merged.addAll(cleanLists)
            merged.retainAll(cleanLists)
            
            if (merged.isEmpty()) {
                merged.addAll(cleanLists)
            }
            
            _hotBoxLists.value = merged
            
            val currentSelected = _selectedHotBoxListNames.value
            val validSelected = currentSelected.filter { it in merged }.toSet()
            if (validSelected.isEmpty() || isFirstListLoad) {
                val finalSelection = if (validSelected.isNotEmpty()) {
                    validSelected
                } else {
                    setOf(merged.firstOrNull() ?: "Hotbox")
                }
                _selectedHotBoxListNames.value = finalSelection
                _selectedHotBoxListName.value = finalSelection.first()
                isFirstListLoad = false
            } else {
                _selectedHotBoxListNames.value = validSelected
                _selectedHotBoxListName.value = validSelected.first()
            }
        }
    }

    fun addHotBoxList(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank() && trimmed != "Standard Hotbox" && trimmed != "Passive Hotbox") {
            val current = _hotBoxLists.value
            val newList = LinkedHashSet<String>()
            newList.add(trimmed)
            newList.addAll(current)
            _hotBoxLists.value = newList
            _selectedHotBoxListName.value = trimmed
            _selectedHotBoxListNames.value = setOf(trimmed)
            
            viewModelScope.launch {
                com.example.util.SupabaseDbClient.upsertHotBoxList(repository.getContext(), trimmed)
            }
        }
    }

    fun removeHotBoxList(name: String) {
        val currentLists = _hotBoxLists.value
        if (currentLists.size <= 1) return
        val updatedLists = LinkedHashSet<String>()
        updatedLists.addAll(currentLists)
        updatedLists.remove(name)
        _hotBoxLists.value = updatedLists
        
        val currentSelected = _selectedHotBoxListNames.value
        val nextSelected = currentSelected - name
        if (nextSelected.isEmpty()) {
            val first = updatedLists.firstOrNull() ?: "Hotbox"
            _selectedHotBoxListName.value = first
            _selectedHotBoxListNames.value = setOf(first)
        } else {
            _selectedHotBoxListNames.value = nextSelected
            _selectedHotBoxListName.value = nextSelected.first()
        }
        
        viewModelScope.launch {
            contacts.value.forEach { contact ->
                if (contact.isHotBox && contact.hotBoxListName == name) {
                    val updated = contact.copy(isHotBox = false, hotBoxListName = null)
                    repository.insertContact(updated)
                }
            }
            com.example.util.SupabaseDbClient.deleteHotBoxList(repository.getContext(), name)
        }
    }

    fun resetCurrentHotBoxCycle() {
        viewModelScope.launch {
            val activeLists = _selectedHotBoxListNames.value
            contacts.value.forEach { contact ->
                val isMatchingList = getEffectiveHotBoxListName(contact.hotBoxListName) in activeLists
                if (contact.isHotBox && isMatchingList && contact.hasBeenCalledInHotCycle) {
                    val updated = contact.copy(hasBeenCalledInHotCycle = false)
                    repository.insertContact(updated)
                }
            }
        }
    }

    companion object {
        val isAutoCallActiveGlobal = MutableStateFlow(false)
        val isAutoCallPausedGlobal = MutableStateFlow(false)
    }

    val isAutoCallActive: StateFlow<Boolean> = isAutoCallActiveGlobal.asStateFlow()
    val isAutoCallPaused: StateFlow<Boolean> = isAutoCallPausedGlobal.asStateFlow()

    // Preferences and Simulation Mode (Defaults to true for emulators)
    private val prefs = repository.getContext().getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE)
    val isSimulationModeEnabled = MutableStateFlow(prefs.getBoolean("call_simulation_mode", false))

    fun setSimulationModeEnabled(enabled: Boolean) {
        isSimulationModeEnabled.value = enabled
        prefs.edit().putBoolean("call_simulation_mode", enabled).apply()
    }

    private var simulationJob: kotlinx.coroutines.Job? = null

    private fun startSimulatedCall(phone: String, name: String) {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            setWrapUpNote("[Simulierter Anruf gestartet] 📞\n")
            delay(1500)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("Kunde: Hallo? Ja, hier spricht $name.\n")
            delay(3000)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("Stromruf AI: Guten Tag Herr/Frau $name. Ich rufe Sie bezüglich Ihres Stromvertrags an. Wir haben aktuell ein sehr günstiges Ökostrom-Angebot.\n")
            delay(4000)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("Kunde: Ah ja, mein jetziger Vertrag läuft in drei Monaten ab und ich zahle viel zu viel. Was bieten Sie denn an?\n")
            delay(4500)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("Stromruf AI: Wir bieten Ihnen Ökostrom für 28,5 Cent pro kWh mit einer Preisgarantie von 12 Monaten. Wie viel verbrauchen Sie denn ungefähr im Jahr?\n")
            delay(5000)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("Kunde: Wir verbrauchen ca. 3.500 Kilowattstunden im Jahr. Können Sie mir dieses Angebot bitte per E-Mail schicken?\n")
            delay(5000)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("Stromruf AI: Natürlich! Ich habe Ihre E-Mail-Adresse als info@stromruf-test.de vorliegen. Passt das so?\n")
            delay(4000)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("Kunde: Ja, genau, info@stromruf-test.de ist richtig. Vielen Dank, ich warte auf Ihre E-Mail. Auf Wiederhören!\n")
            delay(3000)
            if (_activeCall.value == null) return@launch

            appendWrapUpNote("[Gespräch beendet. Sie können jetzt auflegen.] ✅\n")
        }
    }

    private fun appendWrapUpNote(text: String) {
        val current = _wrapUpData.value.note
        _wrapUpData.value = _wrapUpData.value.copy(note = current + text)
    }

    private val _autoCallDelaySeconds = MutableStateFlow(5)
    val autoCallDelaySeconds: StateFlow<Int> = _autoCallDelaySeconds.asStateFlow()

    fun setAutoCallDelaySeconds(seconds: Int) {
        _autoCallDelaySeconds.value = seconds.coerceIn(1, 60)
    }

    private val _autoCallCountdown = MutableStateFlow<Int?>(null)
    val autoCallCountdown: StateFlow<Int?> = _autoCallCountdown.asStateFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    // Temp state to trigger Android Dialer intents in UI
    private val _dialIntentTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val dialIntentTrigger = _dialIntentTrigger.asSharedFlow()

    // Trigger clipboard copy for hotbox numbers
    private val _copyToClipboardTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val copyToClipboardTrigger = _copyToClipboardTrigger.asSharedFlow()

    // Trigger toast messages from VM or commands
    private val _showToastTrigger = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val showToastTrigger = _showToastTrigger.asSharedFlow()

    fun triggerToast(message: String) {
        viewModelScope.launch {
            _showToastTrigger.emit(message)
        }
    }

    // Active incoming reminder alert state for bottom overlay
    val activeIncomingAlert = MutableStateFlow<IncomingAlert?>(null)

    // Tracks if this app is the default phone dialer
    val isDefaultDialer = MutableStateFlow(false)

    // --- KPI calculations ---
    val totalCallsToday: StateFlow<Int> = callLogs.map { logs ->
        val startOfToday = getStartOfToday()
        logs.count { it.timestamp >= startOfToday }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingFollowUpsCount: StateFlow<Int> = activeFollowUps.map { followups ->
        val now = System.currentTimeMillis()
        followups.count { !it.isCompleted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val reachabilityRateToday: StateFlow<Int> = callLogs.map { logs ->
        val startOfToday = getStartOfToday()
        val logsToday = logs.filter { it.timestamp >= startOfToday }
        if (logsToday.isEmpty()) return@map 0
        
        val reached = logsToday.count {
            it.outcome == "erreicht_interesse" || 
            it.outcome == "erreicht_abschluss" || 
            it.outcome == "erreicht_kein_interesse"
        }
        (reached * 100) / logsToday.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Operations ---

    // Trigger phone calling
    fun initiateCall(phone: String, name: String? = null, contactId: String? = null, callType: String = "einwaehlen") {
        if (phone.isBlank()) return
        val normalized = normalizePhone(phone)
        
        val shouldTrackActiveCall = isSimulationModeEnabled.value || isDefaultDialer.value

        if (shouldTrackActiveCall) {
            _activeCall.value = ActiveCall(
                name = name ?: "",
                phone = normalized,
                contactId = contactId,
                startTime = System.currentTimeMillis(),
                callType = callType
            )
            initializeWrapUpForActiveCall(normalized, name)
        }

        // Clipboard copy when customer number is found or when in HotBox / Auto-Call / Dialer
        val matchingContact = if (contactId != null) {
            contacts.value.firstOrNull { it.id == contactId }
        } else {
            contacts.value.firstOrNull { it.phone == normalized || it.phone == phone || (name != null && it.name == name) }
        }

        val isHotBoxContact = matchingContact?.isHotBox == true || (callType == "hotbox") || isAutoCallActiveGlobal.value

        val neukundeMatch = neukunden.value.firstOrNull { it.phone == normalized || it.phone == phone }
        val angebotMatch = heisseAngebote.value.firstOrNull { it.phone == normalized || it.phone == phone }
        val promisedMatch = promisedAnnahmen.value.firstOrNull { it.phone == normalized || it.phone == phone }

        // Extract customer number (Kundennummer) - pure digits (typically 6-digit, starting with 9 or 7, or leading numbers)
        val extractedCustomerNumber = com.example.util.CustomerNumberExtractor.extractCustomerNumber(
            neukundeMatch?.customerNumber,
            angebotMatch?.customerNumber,
            promisedMatch?.customerNumber,
            matchingContact?.company,
            name,
            matchingContact?.name,
            matchingContact?.callReason
        )

        if (!extractedCustomerNumber.isNullOrBlank()) {
            viewModelScope.launch {
                _copyToClipboardTrigger.emit(extractedCustomerNumber)
            }
        }

        if (isHotBoxContact) {
            val idToSet = contactId ?: matchingContact?.id ?: contacts.value.firstOrNull { it.phone == normalized || it.phone == phone }?.id
            if (idToSet != null) {
                _lastCalledHotBoxContactId.value = idToSet
            }
        }

        if (isSimulationModeEnabled.value) {
            // Simulated in-app call
            startSimulatedCall(normalized, name ?: "Kunde")
        } else {
            // Push intent trigger to MainActivity to launch native phone call
            viewModelScope.launch {
                _dialIntentTrigger.emit(normalized)
            }
        }
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "Nicht erreicht"
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hrs > 0 -> {
                if (mins > 0) "${hrs} Std. ${mins} Min." else "${hrs} Std."
            }
            mins > 0 -> {
                if (secs > 0) "${mins} Min. ${secs} Sek." else "${mins} Min."
            }
            else -> {
                "${secs} Sek."
            }
        }
    }

    fun autoRecordActiveCall(durationSeconds: Long, wasAnswered: Boolean? = null) {
        val call = _activeCall.value ?: return
        val now = System.currentTimeMillis()
        
        viewModelScope.launch {
            val existing = repository.getContactByPhone(call.phone)
            val isHot = (call.callType == "hotbox") || isAutoCallActiveGlobal.value || (existing?.isHotBox == true)
            
            if (isHot) {
                autoSaveHotBoxCall(call.phone, call.name ?: existing?.name, durationSeconds)
                return@launch
            }
            
            // Determine outcome based on whether it was answered or call duration
            val finalOutcome = when {
                wasAnswered == true -> "erreicht_interesse"
                wasAnswered == false -> "nicht_erreicht"
                durationSeconds > 10 -> "erreicht_interesse"
                else -> "nicht_erreicht"
            }
            
            val durationText = if (durationSeconds > 0) "Dauer: ${formatDuration(durationSeconds)}" else "Nicht erreicht"
            val finalContactName = existing?.name ?: call.name?.takeIf { it.isNotBlank() } ?: "Anonym"
            
            // 1. Update existing contact's last call status
            if (existing != null) {
                val updatedContact = existing.copy(
                    lastCallAt = now,
                    lastOutcome = finalOutcome
                )
                repository.insertContact(updatedContact)
            } else {
                showSaveNumberBubble(call.phone)
            }
            
            // 2. Insert call log
            val callLog = CallLogEntity(
                id = UUID.randomUUID().toString(),
                phone = call.phone,
                contactName = finalContactName,
                outcome = finalOutcome,
                note = "Automatisch erfasst ($durationText)",
                timestamp = now,
                durationSeconds = durationSeconds,
                callType = call.callType
            )
            safeInsertCallLog(callLog)
            
            // Clear active call
            _activeCall.value = null
        }
    }

    fun autoSaveHotBoxCall(phone: String, name: String?, durationSeconds: Long) {
        simulationJob?.cancel()
        viewModelScope.launch {
            val normalized = normalizePhone(phone)
            val recentLogs = callLogs.value.filter { phoneNumbersMatch(it.phone, normalized) }
            val latestLog = recentLogs.maxByOrNull { it.timestamp }
            val effectiveDuration = if (durationSeconds > 0) {
                durationSeconds
            } else if (latestLog != null && (System.currentTimeMillis() - latestLog.timestamp < 300_000L) && latestLog.durationSeconds > 0) {
                latestLog.durationSeconds
            } else {
                0L
            }
            
            val existing = repository.getContactByPhone(normalized)
            val now = System.currentTimeMillis()
            
            val wrapData = _wrapUpData.value
            val isMatching = (wrapData.phone == normalized)
            val enteredName = if (isMatching && wrapData.name.isNotBlank()) wrapData.name else null
            val enteredCustNo = if (isMatching && wrapData.customerNumber.isNotBlank()) wrapData.customerNumber else null
            val enteredCompany = if (isMatching && wrapData.company.isNotBlank()) wrapData.company else (if (!enteredCustNo.isNullOrBlank()) "Kd.-Nr: $enteredCustNo" else null)

            val finalContactName = enteredName ?: name ?: existing?.name ?: "Anonym ($normalized)"
            val finalCompany = enteredCompany ?: existing?.company
            val finalOutcome = if (effectiveDuration > 10) "erreicht_interesse" else "nicht_erreicht"
            
            val userReason = if (isMatching) wrapData.callReason else null
            val userNote = if (isMatching && wrapData.note.isNotBlank()) wrapData.note else null
            val finalNote = userNote ?: "Automatischer Hotbox-Anruf (${formatDuration(effectiveDuration)})"
            
            // 1. Update existing contact or create if new
            val contactId = if (existing != null) {
                val updatedContact = existing.copy(
                    name = enteredName ?: existing.name,
                    company = finalCompany ?: existing.company,
                    lastCallAt = now,
                    lastOutcome = finalOutcome,
                    isHotBox = true,
                    hotBoxListName = existing.hotBoxListName ?: _selectedHotBoxListName.value,
                    callReason = userReason ?: existing.callReason,
                    hasBeenCalledInHotCycle = true
                )
                repository.insertContact(updatedContact)
                existing.id
            } else {
                showSaveNumberBubble(normalized)
                val newId = UUID.randomUUID().toString()
                val newContact = ContactEntity(
                    id = newId,
                    name = finalContactName,
                    phone = normalized,
                    company = finalCompany,
                    email = if (isMatching) wrapData.email.takeIf { it.isNotBlank() } else null,
                    lastCallAt = now,
                    lastOutcome = finalOutcome,
                    isHotBox = true,
                    hotBoxListName = _selectedHotBoxListName.value,
                    hasBeenCalledInHotCycle = true,
                    callReason = userReason
                )
                repository.insertContact(newContact)
                newId
            }
            
            // 2. Record Call Log
            val callLog = CallLogEntity(
                id = UUID.randomUUID().toString(),
                phone = normalized,
                contactName = finalContactName,
                outcome = finalOutcome,
                note = finalNote,
                timestamp = now,
                durationSeconds = effectiveDuration,
                callReason = userReason,
                callType = "hotbox"
            )
            safeInsertCallLog(callLog)

            // 3. Save any follow ups scheduled during the call
            if (isMatching) {
                wrapData.selectedOffsets.forEach { offset ->
                    val dueTime = calculateOffsetTime(offset)
                    val fId = UUID.randomUUID().toString()
                    val followup = FollowUpEntity(
                        id = fId,
                        contactId = contactId,
                        contactName = finalContactName,
                        contactPhone = normalized,
                        note = userNote,
                        dueAt = dueTime,
                        isCompleted = false,
                        callReason = userReason
                    )
                    val saved = repository.insertFollowUp(followup)
                    onScheduleAlarm?.invoke(fId, finalContactName, normalized, saved.dueAt)
                }
                wrapData.customDates.forEach { timestamp ->
                    val fId = UUID.randomUUID().toString()
                    val followup = FollowUpEntity(
                        id = fId,
                        contactId = contactId,
                        contactName = finalContactName,
                        contactPhone = normalized,
                        note = userNote,
                        dueAt = timestamp,
                        isCompleted = false,
                        callReason = userReason
                    )
                    val saved = repository.insertFollowUp(followup)
                    onScheduleAlarm?.invoke(fId, finalContactName, normalized, saved.dueAt)
                }
            }
            
            // 4. Clear states
            _showWrapUpDialog.value = false
            _activeCall.value = null
            _wrapUpData.value = WrapUpData()
            
            // 5. Trigger next call countdown if autopilot is still active and not paused
            if (isAutoCallActiveGlobal.value && !isAutoCallPausedGlobal.value) {
                startAutoCallCountdown()
            } else if (isPromisedThroughCallActive.value) {
                startPromisedThroughCallCountdown()
            }
        }
    }

    fun initializeWrapUpForActiveCall(phone: String, name: String?) {
        viewModelScope.launch {
            val normalized = normalizePhone(phone)
            val existing = repository.getContactByPhone(normalized)
            val currentCallType = _activeCall.value?.callType ?: "einwaehlen"

            val neukundeMatch = neukunden.value.firstOrNull { phoneNumbersMatch(it.phone, normalized) }
            val angebotMatch = heisseAngebote.value.firstOrNull { phoneNumbersMatch(it.phone, normalized) }
            val promisedMatch = promisedAnnahmen.value.firstOrNull { phoneNumbersMatch(it.phone, normalized) }

            val initialCustNo = com.example.util.CustomerNumberExtractor.extractCustomerNumber(
                neukundeMatch?.customerNumber,
                angebotMatch?.customerNumber,
                promisedMatch?.customerNumber,
                existing?.company,
                existing?.name,
                name
            ) ?: ""

            val initialName = when {
                !name.isNullOrBlank() && !name.all { it.isDigit() || it == '+' || it == ' ' } -> name
                !existing?.name.isNullOrBlank() && !existing.name.all { it.isDigit() || it == '+' || it == ' ' } -> existing.name
                !neukundeMatch?.customerName.isNullOrBlank() -> neukundeMatch.customerName
                !promisedMatch?.name.isNullOrBlank() -> promisedMatch.name
                else -> ""
            }

            val initialCompany = existing?.company ?: (if (initialCustNo.isNotBlank()) "Kd.-Nr: $initialCustNo" else "")

            _wrapUpData.value = WrapUpData(
                phone = normalized,
                name = initialName,
                company = initialCompany,
                customerNumber = initialCustNo,
                email = existing?.email ?: neukundeMatch?.email ?: "",
                outcome = "",
                existingContact = existing,
                saveContact = existing == null,
                isHotBox = existing?.isHotBox ?: false,
                callReason = existing?.callReason,
                callType = currentCallType
            )
        }
    }

    fun startWrapUpForDirectCall(phone: String, name: String?, durationSeconds: Long) {
        simulationJob?.cancel()
        viewModelScope.launch {
            val normalized = normalizePhone(phone)
            val recentLogs = callLogs.value.filter { phoneNumbersMatch(it.phone, normalized) }
            val latestLog = recentLogs.maxByOrNull { it.timestamp }
            val effectiveDuration = if (durationSeconds > 0) {
                durationSeconds
            } else if (latestLog != null && (System.currentTimeMillis() - latestLog.timestamp < 300_000L) && latestLog.durationSeconds > 0) {
                latestLog.durationSeconds
            } else {
                0L
            }
            
            val existing = repository.getContactByPhone(normalized)
            val isHot = (existing?.isHotBox == true) || isAutoCallActiveGlobal.value
            
            if (isHot) {
                autoSaveHotBoxCall(normalized, name ?: existing?.name, effectiveDuration)
            } else {
                val current = _wrapUpData.value
                val isMatching = (current.phone == normalized)
                val enteredName = if (isMatching && current.name.isNotBlank()) current.name else null
                val enteredCustNo = if (isMatching && current.customerNumber.isNotBlank()) current.customerNumber else null
                val enteredCompany = if (isMatching && current.company.isNotBlank()) current.company else (if (!enteredCustNo.isNullOrBlank()) "Kd.-Nr: $enteredCustNo" else null)
                
                val finalOutcome = if (effectiveDuration >= 60) "erreicht_interesse" else "nicht_erreicht"
                val currentCallType = _activeCall.value?.callType ?: "einwaehlen"
                
                val finalContactName = enteredName ?: name ?: existing?.name ?: current.name.takeIf { it.isNotBlank() } ?: "Kunde ($normalized)"
                val finalCompany = enteredCompany ?: existing?.company
                val userReason = if (isMatching) current.callReason else null
                val userNote = if (isMatching && current.note.isNotBlank()) current.note else null
                val finalNote = userNote ?: "Automatischer Anruf (${formatDuration(effectiveDuration)})"

                val now = System.currentTimeMillis()

                // 1. Update existing contact or create if new
                val contactId = if (existing != null) {
                    val updatedContact = existing.copy(
                        name = enteredName ?: existing.name,
                        company = finalCompany ?: existing.company,
                        email = if (isMatching && current.email.isNotBlank()) current.email else existing.email,
                        lastCallAt = now,
                        lastOutcome = finalOutcome,
                        callReason = userReason ?: existing.callReason
                    )
                    repository.insertContact(updatedContact)
                    existing.id
                } else {
                    val newId = UUID.randomUUID().toString()
                    val newContact = ContactEntity(
                        id = newId,
                        name = finalContactName,
                        phone = normalized,
                        company = finalCompany,
                        email = if (isMatching) current.email.takeIf { it.isNotBlank() } else null,
                        lastCallAt = now,
                        lastOutcome = finalOutcome,
                        isHotBox = false,
                        callReason = userReason
                    )
                    repository.insertContact(newContact)
                    newId
                }
                
                // 2. Record Call Log
                val callLog = CallLogEntity(
                    id = UUID.randomUUID().toString(),
                    phone = normalized,
                    contactName = finalContactName,
                    outcome = finalOutcome,
                    note = finalNote,
                    timestamp = now,
                    durationSeconds = effectiveDuration,
                    callReason = userReason,
                    callType = currentCallType
                )
                safeInsertCallLog(callLog)

                // If this was an AI-assisted call, also save as an AiCallEntity
                if ((currentCallType == "ai_anruf" || currentCallType == "ai") && finalNote.isNotBlank()) {
                    val aiCallEntity = com.example.database.AiCallEntity(
                        id = UUID.randomUUID().toString(),
                        phone = normalized.ifBlank { "Unbekannt" },
                        contactName = finalContactName,
                        timestamp = now,
                        audioFilePath = null,
                        transcript = finalNote,
                        durationSeconds = effectiveDuration,
                        notes = "AI Anrufs-Notiz"
                    )
                    repository.insertAiCall(aiCallEntity)
                }

                // 3. Save any follow ups scheduled during the call
                if (isMatching) {
                    current.selectedOffsets.forEach { offset ->
                        val dueTime = calculateOffsetTime(offset)
                        val fId = UUID.randomUUID().toString()
                        val followup = FollowUpEntity(
                            id = fId,
                            contactId = contactId,
                            contactName = finalContactName,
                            contactPhone = normalized,
                            note = userNote,
                            dueAt = dueTime,
                            isCompleted = false,
                            callReason = userReason
                        )
                        val saved = repository.insertFollowUp(followup)
                        onScheduleAlarm?.invoke(fId, finalContactName, normalized, saved.dueAt)
                    }
                    current.customDates.forEach { timestamp ->
                        val fId = UUID.randomUUID().toString()
                        val followup = FollowUpEntity(
                            id = fId,
                            contactId = contactId,
                            contactName = finalContactName,
                            contactPhone = normalized,
                            note = userNote,
                            dueAt = timestamp,
                            isCompleted = false,
                            callReason = userReason
                        )
                        val saved = repository.insertFollowUp(followup)
                        onScheduleAlarm?.invoke(fId, finalContactName, normalized, saved.dueAt)
                    }
                }
                
                // 4. Clear states
                _showWrapUpDialog.value = false
                _activeCall.value = null
                _wrapUpData.value = WrapUpData()

                checkAndMovePassiveFollowUpsToHotBox()

                if (isAutoCallActiveGlobal.value && !isAutoCallPausedGlobal.value) {
                    startAutoCallCountdown()
                } else if (isPromisedThroughCallActive.value) {
                    startPromisedThroughCallCountdown()
                }
            }
        }
    }

    fun cancelWrapUp() {
        simulationJob?.cancel()
        _showWrapUpDialog.value = false
        _activeCall.value = null
        if (isAutoCallActiveGlobal.value && !isAutoCallPausedGlobal.value) {
            startAutoCallCountdown()
        } else if (isPromisedThroughCallActive.value) {
            startPromisedThroughCallCountdown()
        }
    }

    fun clearActiveCall() {
        simulationJob?.cancel()
        _activeCall.value = null
        _showWrapUpDialog.value = false
    }

    // Outcome options operations
    fun setWrapUpOutcome(outcome: String) {
        _wrapUpData.value = _wrapUpData.value.copy(outcome = outcome)
    }

    fun setWrapUpSaveContact(save: Boolean) {
        _wrapUpData.value = _wrapUpData.value.copy(saveContact = save)
    }

    fun setWrapUpNote(note: String) {
        _wrapUpData.value = _wrapUpData.value.copy(note = note)
    }

    fun setWrapUpCallReason(reason: String?) {
        _wrapUpData.value = _wrapUpData.value.copy(callReason = reason)
    }

    fun setWrapUpName(name: String) {
        _wrapUpData.value = _wrapUpData.value.copy(name = name)
    }

    fun setWrapUpCustomerNumber(customerNumber: String) {
        val currentCompany = _wrapUpData.value.company
        val updatedCompany = if (currentCompany.isBlank() || currentCompany.startsWith("Kd.-Nr")) {
            if (customerNumber.isNotBlank()) "Kd.-Nr: $customerNumber" else ""
        } else {
            currentCompany
        }
        _wrapUpData.value = _wrapUpData.value.copy(customerNumber = customerNumber, company = updatedCompany)
    }

    fun setWrapUpCompany(company: String) {
        _wrapUpData.value = _wrapUpData.value.copy(company = company)
    }

    fun updateWrapUpFields(name: String, company: String, email: String, customerNumber: String = "") {
        val finalCompany = if (company.isNotBlank()) {
            company
        } else if (customerNumber.isNotBlank()) {
            "Kd.-Nr: $customerNumber"
        } else {
            ""
        }
        _wrapUpData.value = _wrapUpData.value.copy(
            name = name,
            company = finalCompany,
            email = email,
            customerNumber = if (customerNumber.isNotBlank()) customerNumber else _wrapUpData.value.customerNumber
        )
    }

    fun toggleWrapUpOffset(offset: String) {
        val currentOffsets = _wrapUpData.value.selectedOffsets.toMutableSet()
        if (currentOffsets.contains(offset)) {
            currentOffsets.remove(offset)
        } else {
            currentOffsets.add(offset)
        }
        _wrapUpData.value = _wrapUpData.value.copy(selectedOffsets = currentOffsets)
    }

    fun addCustomFollowUpDate(timestamp: Long) {
        val currentDates = _wrapUpData.value.customDates.toMutableList()
        currentDates.add(timestamp)
        _wrapUpData.value = _wrapUpData.value.copy(customDates = currentDates)
    }

    fun removeCustomFollowUpDate(timestamp: Long) {
        val currentDates = _wrapUpData.value.customDates.filter { it != timestamp }
        _wrapUpData.value = _wrapUpData.value.copy(customDates = currentDates)
    }

    fun saveWrapUp() {
        val data = _wrapUpData.value
        val now = System.currentTimeMillis()
        val enteredCustNo = data.customerNumber.takeIf { it.isNotBlank() }
        val finalCompany = data.company.takeIf { it.isNotBlank() } ?: (if (enteredCustNo != null) "Kd.-Nr: $enteredCustNo" else null)
        val finalContactName = if (data.name.isNotBlank()) {
            data.name
        } else if (data.existingContact != null && data.existingContact.name.isNotBlank()) {
            data.existingContact.name
        } else {
            "Anonym (${data.phone})"
        }

        viewModelScope.launch {
            try {
                // 1. Save or Update Contact
                val contactId = if (data.existingContact != null) {
                    // Update existing contact details or just lastCallInfo
                    val updatedContact = data.existingContact.copy(
                        name = if (data.name.isNotBlank()) data.name else data.existingContact.name,
                        company = finalCompany ?: data.existingContact.company,
                        email = if (data.email.isNotBlank()) data.email else data.existingContact.email,
                        lastCallAt = now,
                        lastOutcome = data.outcome,
                        isHotBox = data.isHotBox,
                        hotBoxListName = if (data.isHotBox) (data.existingContact.hotBoxListName ?: _selectedHotBoxListName.value) else null,
                        hotBoxStartHour = if (data.isHotBox) data.hotBoxStartHour else null,
                        hotBoxEndHour = if (data.isHotBox) data.hotBoxEndHour else null,
                        hotBoxWeekdays = if (data.isHotBox) data.hotBoxWeekdays else null,
                        callReason = data.callReason
                    )
                    repository.insertContact(updatedContact)
                    data.existingContact.id
                } else if (data.saveContact && data.name.isNotBlank()) {
                    val newId = UUID.randomUUID().toString()
                    val newContact = ContactEntity(
                        id = newId,
                        name = data.name,
                        phone = data.phone,
                        company = finalCompany,
                        email = data.email.takeIf { it.isNotBlank() },
                        lastCallAt = now,
                        lastOutcome = data.outcome,
                        isHotBox = data.isHotBox,
                        hotBoxListName = if (data.isHotBox) _selectedHotBoxListName.value else null,
                        hotBoxStartHour = if (data.isHotBox) data.hotBoxStartHour else null,
                        hotBoxEndHour = if (data.isHotBox) data.hotBoxEndHour else null,
                        hotBoxWeekdays = if (data.isHotBox) data.hotBoxWeekdays else null,
                        callReason = data.callReason
                    )
                    repository.insertContact(newContact)
                    newId
                } else {
                    null
                }

                // 2. Record Call Log
                val callLog = CallLogEntity(
                    id = UUID.randomUUID().toString(),
                    phone = data.phone,
                    contactName = finalContactName,
                    outcome = data.outcome,
                    note = data.note.takeIf { it.isNotBlank() },
                    timestamp = now,
                    durationSeconds = data.durationSeconds,
                    callReason = data.callReason,
                    callType = data.callType
                )
                safeInsertCallLog(callLog)

                // If this was an AI-assisted call, also save as an AiCallEntity (AI Call Note)
                if ((data.callType == "ai_anruf" || data.callType == "ai") && data.note.isNotBlank()) {
                    val aiCallEntity = com.example.database.AiCallEntity(
                        id = UUID.randomUUID().toString(),
                        phone = data.phone.ifBlank { "Unbekannt" },
                        contactName = finalContactName,
                        timestamp = now,
                        audioFilePath = null,
                        transcript = data.note,
                        durationSeconds = data.durationSeconds,
                        notes = "AI Anrufs-Notiz"
                    )
                    repository.insertAiCall(aiCallEntity)
                }

                // 3. Generate individual Follow-ups for all chosen offsets
                data.selectedOffsets.forEach { offset ->
                    val dueTime = calculateOffsetTime(offset)
                    val fId = UUID.randomUUID().toString()
                    val followup = FollowUpEntity(
                        id = fId,
                        contactId = contactId,
                        contactName = finalContactName,
                        contactPhone = data.phone,
                        note = data.note.takeIf { it.isNotBlank() },
                        dueAt = dueTime,
                        isCompleted = false,
                        callReason = data.callReason
                    )
                    val saved = repository.insertFollowUp(followup)
                    onScheduleAlarm?.invoke(fId, finalContactName, data.phone, saved.dueAt)
                }

                // 4. Generate follow-ups for custom dates
                data.customDates.forEach { timestamp ->
                    val fId = UUID.randomUUID().toString()
                    val followup = FollowUpEntity(
                        id = fId,
                        contactId = contactId,
                        contactName = finalContactName,
                        contactPhone = data.phone,
                        note = data.note.takeIf { it.isNotBlank() },
                        dueAt = timestamp,
                        isCompleted = false,
                        callReason = data.callReason
                    )
                    val saved = repository.insertFollowUp(followup)
                    onScheduleAlarm?.invoke(fId, finalContactName, data.phone, saved.dueAt)
                }
            } catch (e: Exception) {
                android.util.Log.e("StromrufViewModel", "Fehler in saveWrapUp()", e)
            } finally {
                // Clean up States
                _showWrapUpDialog.value = false
                _activeCall.value = null
                _wrapUpData.value = WrapUpData()

                checkAndMovePassiveFollowUpsToHotBox()

                if (isAutoCallActiveGlobal.value && !isAutoCallPausedGlobal.value) {
                    startAutoCallCountdown()
                } else if (isPromisedThroughCallActive.value) {
                    startPromisedThroughCallCountdown()
                }
            }
        }
    }

    // --- Direct Manual Contact management ---
    fun addManualFollowUp(name: String, phone: String, note: String?, dueAt: Long, callReason: String? = null) {
        if (name.isBlank() || phone.isBlank()) return
        val normalized = normalizePhone(phone)
        val fId = UUID.randomUUID().toString()
        viewModelScope.launch {
            val existing = repository.getContactByPhone(normalized)
            val finalContactId = if (existing != null) {
                existing.id
            } else {
                val newId = UUID.randomUUID().toString()
                val newContact = ContactEntity(
                    id = newId,
                    name = name,
                    phone = normalized,
                    company = null,
                    email = null,
                    lastCallAt = null,
                    lastOutcome = null,
                    isHotBox = false
                )
                repository.insertContact(newContact)
                newId
            }
            val followup = FollowUpEntity(
                id = fId,
                contactId = finalContactId,
                contactName = name,
                contactPhone = normalized,
                note = note,
                dueAt = dueAt,
                isCompleted = false,
                callReason = callReason
            )
            val saved = repository.insertFollowUp(followup)
            onScheduleAlarm?.invoke(fId, name, normalized, saved.dueAt)
        }
    }

    fun addManualContact(
        name: String,
        phone: String,
        company: String,
        email: String,
        isHotBox: Boolean = false,
        hotBoxStartHour: Int? = null,
        hotBoxEndHour: Int? = null,
        hotBoxWeekdays: String? = null,
        callReason: String? = null,
        hotBoxListName: String? = null,
        consumption: Long? = null,
        zipCode: String? = null,
        energyType: String? = null,
        routine: String = "Keine"
    ) {
        if (name.isBlank() || phone.isBlank()) return
        val normalized = normalizePhone(phone)
        viewModelScope.launch {
            val existing = repository.getContactByPhone(normalized)
            val listToUse = hotBoxListName ?: if (isHotBox) _selectedHotBoxListName.value else null
            if (existing != null) {
                val updated = existing.copy(
                    name = if (name.isNotBlank()) name else existing.name,
                    company = if (company.isNotBlank()) company else existing.company,
                    email = if (email.isNotBlank()) email else existing.email,
                    isHotBox = isHotBox,
                    hotBoxListName = listToUse,
                    hasBeenCalledInHotCycle = if (isHotBox && !existing.isHotBox) false else existing.hasBeenCalledInHotCycle,
                    hotBoxStartHour = if (isHotBox) hotBoxStartHour else null,
                    hotBoxEndHour = if (isHotBox) hotBoxEndHour else null,
                    hotBoxWeekdays = if (isHotBox) hotBoxWeekdays else null,
                    callReason = callReason ?: existing.callReason,
                    consumption = consumption ?: existing.consumption,
                    zipCode = zipCode ?: existing.zipCode,
                    energyType = energyType ?: existing.energyType
                )
                repository.insertContact(updated)
            } else {
                val contact = ContactEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    phone = normalized,
                    company = company.takeIf { it.isNotBlank() },
                    email = email.takeIf { it.isNotBlank() },
                    lastCallAt = null,
                    lastOutcome = null,
                    isHotBox = isHotBox,
                    hotBoxListName = listToUse,
                    hotBoxStartHour = if (isHotBox) hotBoxStartHour else null,
                    hotBoxEndHour = if (isHotBox) hotBoxEndHour else null,
                    hotBoxWeekdays = if (isHotBox) hotBoxWeekdays else null,
                    callReason = callReason,
                    dateCreated = System.currentTimeMillis(),
                    consumption = consumption,
                    zipCode = zipCode,
                    energyType = energyType
                )
                repository.insertContact(contact)
            }

            if (routine == "Nicht erreicht") {
                val dueAt = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
                addManualFollowUp(
                    name = name,
                    phone = phone,
                    note = "Wiedervorlage nach Anrufversuch (Routine: Angerufen - nicht erreicht)",
                    dueAt = dueAt,
                    callReason = "Anrufen"
                )
            } else if (routine == "Datenmail") {
                val calendar = java.util.Calendar.getInstance()
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 14)
                calendar.set(java.util.Calendar.MINUTE, 55)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                addManualFollowUp(
                    name = name,
                    phone = phone,
                    note = "Angebot schicken (Routine: Datenmail)",
                    dueAt = calendar.timeInMillis,
                    callReason = "Angebot erstellen"
                )
            }
        }
    }

    fun updateContactCallReason(contact: ContactEntity, reason: String?) {
        viewModelScope.launch {
            repository.insertContact(contact.copy(callReason = reason))
        }
    }

    fun importContactFromSystem(name: String, phone: String, onResult: (Boolean, String) -> Unit) {
        if (name.isBlank() || phone.isBlank()) {
            onResult(false, "Name oder Nummer ist leer.")
            return
        }
        val normalized = normalizePhone(phone)
        viewModelScope.launch {
            val existing = repository.getContactByPhone(normalized)
            if (existing != null) {
                val updated = existing.copy(
                    isHotBox = true,
                    hotBoxListName = _selectedHotBoxListName.value,
                    hasBeenCalledInHotCycle = false,
                    name = if (name.isNotBlank() && (existing.name.startsWith("Lead ") || existing.name.isBlank())) name else existing.name
                )
                repository.insertContact(updated)
                onResult(true, "Kontakt existierte bereits, wurde zur Hotbox hinzugefügt! 🔥")
            } else {
                val contact = ContactEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    phone = normalized,
                    company = null,
                    email = null,
                    lastCallAt = null,
                    lastOutcome = null,
                    isHotBox = true,
                    hotBoxListName = _selectedHotBoxListName.value,
                    hotBoxStartHour = null,
                    hotBoxEndHour = null
                )
                repository.insertContact(contact)
                onResult(true, "Kontakt erfolgreich zur Hotbox hinzugefügt! 🚀")
            }
        }
    }

    fun editContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.insertContact(contact)
        }
    }

    fun toggleHotBox(contactId: String) {
        viewModelScope.launch {
            val contact = repository.getContactById(contactId) ?: return@launch
            val nextVal = !contact.isHotBox
            val updated = contact.copy(
                isHotBox = nextVal,
                hotBoxListName = if (nextVal) _selectedHotBoxListName.value else null
            )
            repository.insertContact(updated)
        }
    }

    fun addToHotBoxList(contactId: String, listName: String) {
        viewModelScope.launch {
            val contact = repository.getContactById(contactId) ?: return@launch
            val updated = contact.copy(
                isHotBox = true,
                hotBoxListName = listName,
                hasBeenCalledInHotCycle = false
            )
            repository.insertContact(updated)
        }
    }

    fun setWrapUpHotBox(isHot: Boolean) {
        _wrapUpData.value = _wrapUpData.value.copy(isHotBox = isHot)
    }

    fun callRandomHotContact(onNoHotContacts: () -> Unit, onCycleReset: () -> Unit) {
        viewModelScope.launch {
            val allCurrentContacts = contacts.value
            val activeLists = _selectedHotBoxListNames.value
            val hotContacts = allCurrentContacts.filter { contact ->
                contact.isHotBox && getEffectiveHotBoxListName(contact.hotBoxListName) in activeLists
            }
            if (hotContacts.isEmpty()) {
                onNoHotContacts()
                return@launch
            }

            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentDay = cal.get(Calendar.DAY_OF_WEEK)
            val activeHotContacts = hotContacts.filter { contact ->
                val weekdaysStr = contact.hotBoxWeekdays
                if (!weekdaysStr.isNullOrEmpty()) {
                    val daysList = weekdaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (daysList.isNotEmpty() && currentDay !in daysList) {
                        return@filter false
                    }
                }

                val start = contact.hotBoxStartHour
                val end = contact.hotBoxEndHour
                if (start != null && end != null) {
                    if (start <= end) {
                        currentHour in start..end
                    } else {
                        currentHour >= start || currentHour <= end
                    }
                } else {
                    true
                }
            }

            val contactsToUse = if (activeHotContacts.isNotEmpty()) {
                activeHotContacts
            } else {
                hotContacts
            }

            var uncalledHotContacts = contactsToUse.filter { !it.hasBeenCalledInHotCycle }
            if (uncalledHotContacts.isEmpty()) {
                repository.resetHotCycle()
                onCycleReset()
                // Fetch again or map locally
                val resetContacts = contactsToUse.map { it.copy(hasBeenCalledInHotCycle = false) }
                uncalledHotContacts = resetContacts
            }
            
            if (uncalledHotContacts.isNotEmpty()) {
                val nextId = _nextHotBoxContactId.value
                val chosenContact = uncalledHotContacts.firstOrNull { it.id == nextId } ?: uncalledHotContacts.random()
                val updated = chosenContact.copy(hasBeenCalledInHotCycle = true)
                repository.insertContact(updated)
                initiateCall(chosenContact.phone, chosenContact.name, chosenContact.id, callType = "hotbox")
            }
        }
    }

    fun setAutoCallActive(active: Boolean) {
        isAutoCallActiveGlobal.value = active
        if (active) {
            isAutoCallPausedGlobal.value = false
            if (_activeCall.value == null && !_showWrapUpDialog.value) {
                startAutoCallCountdown()
            }
        } else {
            countdownJob?.cancel()
            _autoCallCountdown.value = null
            isAutoCallPausedGlobal.value = false
        }
    }

    fun triggerNextAutoCall() {
        if (!isAutoCallActiveGlobal.value) return
        callRandomHotContact(
            onNoHotContacts = {
                isAutoCallActiveGlobal.value = false
            },
            onCycleReset = {
                // reset is handled
            }
        )
    }

    fun pauseAutoCall() {
        isAutoCallPausedGlobal.value = true
        countdownJob?.cancel()
        _autoCallCountdown.value = null
    }

    fun resumeAutoCall() {
        isAutoCallPausedGlobal.value = false
        if (_activeCall.value == null && !_showWrapUpDialog.value) {
            startAutoCallCountdown()
        }
    }

    fun startAutoCallCountdown() {
        if (!isAutoCallActiveGlobal.value || isAutoCallPausedGlobal.value) return
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            val delaySecs = _autoCallDelaySeconds.value
            for (i in delaySecs downTo 1) {
                _autoCallCountdown.value = i
                delay(1000)
                if (!isAutoCallActiveGlobal.value || isAutoCallPausedGlobal.value) {
                    _autoCallCountdown.value = null
                    return@launch
                }
            }
            _autoCallCountdown.value = null
            triggerNextAutoCall()
        }
    }

    fun skipCountdownAndCallNow() {
        countdownJob?.cancel()
        _autoCallCountdown.value = null
        triggerNextAutoCall()
    }

    fun importNumbersToHotBox(rawText: String, hotBoxListName: String? = null, onResult: (importedCount: Int, createdCount: Int, skippedCount: Int) -> Unit) {
        viewModelScope.launch {
            val lines = rawText.split("\n")
            val phoneRegex = Regex("""\+?[0-9][0-9\s\-()/]{5,18}[0-9]""")
            
            var imported = 0
            var created = 0
            var skipped = 0
            val processedNumbers = mutableSetOf<String>()
            val targetList = hotBoxListName ?: _selectedHotBoxListName.value
            
            lines.forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) return@forEach
                
                // Find all phone numbers in this line
                val match = phoneRegex.find(trimmedLine)
                if (match != null) {
                    val rawPhone = match.value
                    val normalized = normalizePhone(rawPhone)
                    if (normalized.length >= 6) {
                        if (processedNumbers.contains(normalized)) {
                            skipped++
                            return@forEach
                        }
                        processedNumbers.add(normalized)

                        val existing = repository.getContactByPhone(normalized)
                        if (existing != null) {
                            skipped++
                            return@forEach
                        }
                        
                        // Extract any name / customer number from the line
                        // Remove the phone number from the line to see what's left.
                        val remainingTextRaw = trimmedLine.replace(rawPhone, "").replace(Regex("[,;\t|]+"), " ").trim()
                        val custNo = com.example.util.CustomerNumberExtractor.extractCustomerNumber(remainingTextRaw)
                        
                        val parsedName = if (remainingTextRaw.isNotEmpty()) {
                            remainingTextRaw
                        } else {
                            "Lead $normalized"
                        }
                        
                        val companyField = if (!custNo.isNullOrBlank()) "Kd.-Nr: $custNo" else null
                        
                        val newContact = ContactEntity(
                            id = UUID.randomUUID().toString(),
                            name = parsedName,
                            phone = normalized,
                            company = companyField,
                            email = null,
                            lastCallAt = null,
                            lastOutcome = null,
                            isHotBox = true,
                            hotBoxListName = targetList,
                            hasBeenCalledInHotCycle = false
                        )
                        repository.insertContact(newContact)
                        created++
                    }
                }
            }
            onResult(imported, created, skipped)
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch {
            repository.deleteContactById(id)
        }
    }

    // --- Follow-up Actions ---
    fun completeFollowUp(id: String) {
        viewModelScope.launch {
            repository.updateFollowUpStatus(id, true)
            onCancelAlarm?.invoke(id)
        }
    }

    fun deleteFollowUp(id: String) {
        viewModelScope.launch {
            repository.deleteFollowUpById(id)
            onCancelAlarm?.invoke(id)
        }
    }

    fun rescheduleFollowUp(id: String, newDueAt: Long) {
        viewModelScope.launch {
            val existing = repository.getFollowUpById(id)
            if (existing != null) {
                val updated = existing.copy(dueAt = newDueAt)
                repository.insertFollowUp(updated)
                // Reschedule system alarm
                onCancelAlarm?.invoke(id)
                onScheduleAlarm?.invoke(id, existing.contactName, existing.contactPhone, newDueAt)
            }
        }
    }

    private val recentlyLoggedCalls = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun isDuplicateCall(phone: String, timestamp: Long): Boolean {
        val normalized = normalizePhone(phone)
        val timeBucket = timestamp / 10000 // 10 second buckets
        val key = "${normalized}_$timeBucket"
        val keyBefore = "${normalized}_${timeBucket - 1}"
        val keyAfter = "${normalized}_${timeBucket + 1}"
        
        synchronized(recentlyLoggedCalls) {
            if (recentlyLoggedCalls.contains(key) || recentlyLoggedCalls.contains(keyBefore) || recentlyLoggedCalls.contains(keyAfter)) {
                return true
            }
            recentlyLoggedCalls.add(key)
            if (recentlyLoggedCalls.size > 100) {
                recentlyLoggedCalls.clear()
                recentlyLoggedCalls.add(key)
            }
            return false
        }
    }

    private fun phoneNumbersMatch(p1: String, p2: String): Boolean {
        val n1 = p1.replace("[^\\d]".toRegex(), "")
        val n2 = p2.replace("[^\\d]".toRegex(), "")
        if (n1.isEmpty() || n2.isEmpty()) return false
        val len = minOf(n1.length, n2.length, 9)
        if (len < 7) {
            return n1 == n2
        }
        return n1.takeLast(len) == n2.takeLast(len)
    }

    private suspend fun safeInsertCallLog(callLog: CallLogEntity) {
        if (callLog.durationSeconds < 0) {
            android.util.Log.d("StromrufViewModel", "Call log ignored due to negative duration: ${callLog.durationSeconds}s for ${callLog.phone}")
            return
        }
        if (!isDuplicateCall(callLog.phone, callLog.timestamp)) {
            repository.insertCallLog(callLog)

            // Vom Nutzer geschriebene Gesprächsnotizen automatisch im
            // Hintergrund an Telegram weiterleiten (+ Supabase-Sync).
            if (com.example.util.TelegramForwarder.isUserNote(callLog.note)) {
                com.example.util.TelegramForwarder.forwardNote(
                    context = repository.getContext(),
                    contactName = callLog.contactName ?: callLog.phone,
                    phone = callLog.phone,
                    note = callLog.note.orEmpty(),
                    source = "anrufnotiz"
                )
            }
            try {
                val allNeukunden = repository.getAllNeukundenList()
                val normalizedLogPhone = normalizePhone(callLog.phone)
                allNeukunden.forEach { neukunde ->
                    val normalizedNeukundePhone = normalizePhone(neukunde.phone)
                    if (phoneNumbersMatch(normalizedLogPhone, normalizedNeukundePhone)) {
                        val newAttempts = neukunde.callAttempts + 1
                        repository.insertNeukunde(neukunde.copy(callAttempts = newAttempts))
                        android.util.Log.d("StromrufViewModel", "Automatically incremented call attempts for Neukunde ${neukunde.customerNumber}")
                    }
                }
                val allHeisseAngebote = repository.getAllHeisseAngeboteList()
                allHeisseAngebote.forEach { item ->
                    val normalizedItemPhone = normalizePhone(item.phone)
                    if (phoneNumbersMatch(normalizedLogPhone, normalizedItemPhone)) {
                        repository.insertHeissAngebot(item.copy(callAttempts = item.callAttempts + 1))
                        android.util.Log.d("StromrufViewModel", "Automatically incremented call attempts for HeissAngebot ${item.customerNumber}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            android.util.Log.d("StromrufViewModel", "Duplicate call log ignored for ${callLog.phone}")
        }
    }

    // --- Helper Functions ---
    private fun getStartOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun normalizePhone(p: String): String {
        var clean = p.replace("[^\\d+]".toRegex(), "")
        if (clean.startsWith("0049")) {
            clean = "+49" + clean.substring(4)
        } else if (clean.startsWith("00")) {
            clean = "+" + clean.substring(2)
        } else if (clean.startsWith("049")) {
            clean = "+49" + clean.substring(3)
        } else if (clean.startsWith("0") && !clean.startsWith("00")) {
            clean = "+49" + clean.substring(1)
        } else if (!clean.startsWith("+") && clean.startsWith("49")) {
            clean = "+$clean"
        } else if (!clean.startsWith("+") && clean.isNotEmpty()) {
            clean = "+49$clean"
        }
        
        // Remove redundant leading zero inside the German country code if present (e.g., +490172... -> +49172...)
        if (clean.startsWith("+490") && clean.length > 4) {
            clean = "+49" + clean.substring(4)
        }
        
        // Remove double country code if present (e.g., +4949172... -> +49172...)
        if (clean.startsWith("+4949") && clean.length > 5) {
            clean = "+49" + clean.substring(5)
        }
        return clean
    }

    private fun calculateOffsetTime(offset: String): Long {
        val cal = Calendar.getInstance()
        when (offset) {
            "3h" -> cal.add(Calendar.HOUR_OF_DAY, 3)
            "1d" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "1m" -> cal.add(Calendar.MONTH, 1)
            "1y" -> cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun syncSystemCallLogs(context: android.content.Context) {
        viewModelScope.launch {
            if (!com.example.util.ContactsUtil.hasCallLogPermission(context)) {
                android.util.Log.d("StromrufViewModel", "No call log permission, skipping system sync.")
                return@launch
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Define target timestamp for 01.01.2025 (widening the window for better coverage)
                val targetCal = java.util.Calendar.getInstance()
                targetCal.set(2025, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                targetCal.set(java.util.Calendar.MILLISECOND, 0)
                val limitTime = targetCal.timeInMillis

                try {
                    val existingLogs = repository.getAllCallLogsList()
                    val existingIds = existingLogs.map { it.id }.toSet()
                    val existingByPhone = existingLogs.groupBy { normalizePhone(it.phone) }

                    val contentResolver = context.contentResolver
                    val uri = android.provider.CallLog.Calls.CONTENT_URI
                    val projection = arrayOf(
                        android.provider.CallLog.Calls.NUMBER,
                        android.provider.CallLog.Calls.CACHED_NAME,
                        android.provider.CallLog.Calls.DATE,
                        android.provider.CallLog.Calls.DURATION,
                        android.provider.CallLog.Calls.TYPE
                    )
                    val selection = "${android.provider.CallLog.Calls.DATE} >= ?"
                    val selectionArgs = arrayOf(limitTime.toString())

                    val cursor = contentResolver.query(
                        uri,
                        projection,
                        selection,
                        selectionArgs,
                        "${android.provider.CallLog.Calls.DATE} ASC"
                    )

                    cursor?.use { c ->
                        val numIdx = c.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                        val nameIdx = c.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                        val dateIdx = c.getColumnIndex(android.provider.CallLog.Calls.DATE)
                        val durIdx = c.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                        val typeIdx = c.getColumnIndex(android.provider.CallLog.Calls.TYPE)

                        while (c.moveToNext()) {
                            val number = if (numIdx != -1) c.getString(numIdx) ?: "" else ""
                            if (number.isBlank()) continue

                            val name = if (nameIdx != -1) c.getString(nameIdx) else null
                            val date = if (dateIdx != -1) c.getLong(dateIdx) else 0L
                            val durationSeconds = if (durIdx != -1) c.getLong(durIdx) else 0L
                            if (durationSeconds < 0) {
                                android.util.Log.d("StromrufViewModel", "System call log ignored due to negative duration: ${durationSeconds}s for ${number}")
                                continue
                            }
                            val callLogType = if (typeIdx != -1) c.getInt(typeIdx) else android.provider.CallLog.Calls.OUTGOING_TYPE

                            val isIncoming = callLogType == android.provider.CallLog.Calls.INCOMING_TYPE
                            val isOutgoing = callLogType == android.provider.CallLog.Calls.OUTGOING_TYPE
                            val isMissed = callLogType == android.provider.CallLog.Calls.MISSED_TYPE ||
                                           callLogType == android.provider.CallLog.Calls.REJECTED_TYPE ||
                                           (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && callLogType == android.provider.CallLog.Calls.BLOCKED_TYPE)

                            if (!isIncoming && !isOutgoing && !isMissed) continue

                            val normalized = normalizePhone(number)

                            // Skip duplicate call logs (e.g. if an app call was logged for the same phone within 30 seconds)
                            val systemCallLogId = "system_${date}_${normalized}_${durationSeconds}"
                            if (existingIds.contains(systemCallLogId)) {
                                continue
                            }

                            val samePhoneLogs = existingByPhone[normalized] ?: emptyList()
                            val matchingAppLog = samePhoneLogs.find { appLog ->
                                val diffStart = Math.abs(appLog.timestamp - date)
                                val diffEnd = Math.abs(appLog.timestamp - (date + durationSeconds * 1000))
                                diffStart < 90000 || diffEnd < 90000 || (date <= appLog.timestamp && date >= appLog.timestamp - 600000)
                            }

                            if (matchingAppLog != null) {
                                if (durationSeconds > 0 && (matchingAppLog.durationSeconds == 0L || durationSeconds > matchingAppLog.durationSeconds)) {
                                    val updatedOutcome = if (durationSeconds >= 60 && matchingAppLog.outcome == "nicht_erreicht") "erreicht_interesse" else matchingAppLog.outcome
                                    val updatedLog = matchingAppLog.copy(
                                        durationSeconds = durationSeconds,
                                        outcome = updatedOutcome
                                    )
                                    repository.insertCallLog(updatedLog)
                                    android.util.Log.d("StromrufViewModel", "Updated app call log duration to ${durationSeconds}s for $normalized")
                                } else {
                                    android.util.Log.d("StromrufViewModel", "Skipping system call log duplicate for $normalized at $date")
                                }
                                continue
                            }

                            val existingContact = repository.getContactByPhone(normalized)
                            val finalContactName = name ?: existingContact?.name ?: "Kunde ($normalized)"

                            val outcome = if (isMissed || durationSeconds < 60) {
                                "nicht_erreicht"
                            } else {
                                "erreicht_interesse"
                            }

                            val typeStr = when (callLogType) {
                                android.provider.CallLog.Calls.INCOMING_TYPE -> "Eingehend"
                                android.provider.CallLog.Calls.OUTGOING_TYPE -> "Ausgehend"
                                android.provider.CallLog.Calls.MISSED_TYPE -> "Entgangen"
                                android.provider.CallLog.Calls.REJECTED_TYPE -> "Abgewiesen"
                                else -> "Systemanruf"
                            }

                            val callLog = CallLogEntity(
                                id = systemCallLogId,
                                phone = normalized,
                                contactName = finalContactName,
                                outcome = outcome,
                                note = "Systemanruf ($typeStr, ${durationSeconds}s)",
                                timestamp = date,
                                durationSeconds = durationSeconds,
                                callType = if (existingContact?.isHotBox == true) "hotbox" else "einwaehlen"
                            )

                            repository.insertCallLog(callLog)
                        }
                    }
                    android.util.Log.d("StromrufViewModel", "System call logs sync completed.")
                } catch (e: Exception) {
                    android.util.Log.e("StromrufViewModel", "Error syncing system call logs: ${e.localizedMessage}")
                }
            }
        }
    }

    fun checkAndMovePassiveFollowUpsToHotBox() {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val activeFollowUpsList = repository.getActiveFollowUpsList()
                    val now = System.currentTimeMillis()
                    // Two days in milliseconds: 2 * 24 * 60 * 60 * 1000 = 172800000
                    val twoDaysAgo = now - 2L * 24L * 60L * 60L * 1000L
                    
                    // Filter follow-ups where dueAt is at least 2 days ago
                    val qualifyingFollowUps = activeFollowUpsList.filter { it.dueAt <= twoDaysAgo }
                    if (qualifyingFollowUps.isEmpty()) return@withContext
                    
                    // Ensure "Passive" is registered in campaign lists
                    if ("Passive" !in _hotBoxLists.value) {
                        _hotBoxLists.value = _hotBoxLists.value + "Passive"
                    }

                    val allLogs = repository.getAllCallLogsList()
                    val logsByPhone = allLogs.groupBy { normalizePhone(it.phone) }
                    
                    for (followUp in qualifyingFollowUps) {
                        val phone = followUp.contactPhone
                        val dueAt = followUp.dueAt
                        val normFollowUpPhone = normalizePhone(phone)
                        
                        // Check if there was any call log since dueAt with duration > 120 seconds (2 mins)
                        val samePhoneLogs = logsByPhone[normFollowUpPhone] ?: emptyList()
                        val hasSuccessfulCall = samePhoneLogs.any { log ->
                            (log.timestamp >= dueAt) && (log.durationSeconds > 120)
                        }
                        
                        if (!hasSuccessfulCall) {
                            // Move to Passive list
                            val contact = repository.getContactByPhone(phone)
                            if (contact != null) {
                                val updatedContact = contact.copy(
                                    isHotBox = true,
                                    hotBoxListName = "Passive"
                                )
                                repository.insertContact(updatedContact)
                            } else {
                                val newId = UUID.randomUUID().toString()
                                val newContact = ContactEntity(
                                    id = newId,
                                    name = followUp.contactName,
                                    phone = normFollowUpPhone,
                                    company = null,
                                    email = null,
                                    lastCallAt = null,
                                    lastOutcome = null,
                                    isHotBox = true,
                                    hotBoxListName = "Passive"
                                )
                                repository.insertContact(newContact)
                            }
                        }
                        
                        // Mark the followup as completed
                        repository.updateFollowUpStatus(followUp.id, isCompleted = true)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StromrufViewModel", "Error in checkAndMovePassiveFollowUpsToHotBox", e)
                }
            }
        }
    }

    fun savePromisedAnnahme(customerNumber: String, name: String, phone: String) {
        viewModelScope.launch {
            val item = com.example.database.PromisedAnnahmeEntity(
                id = java.util.UUID.randomUUID().toString(),
                customerNumber = customerNumber,
                name = name,
                phone = phone,
                timestamp = System.currentTimeMillis()
            )
            repository.insertPromisedAnnahme(item)
        }
    }

    fun deletePromisedAnnahme(id: String) {
        viewModelScope.launch {
            repository.deletePromisedAnnahmeById(id)
        }
    }

    fun updatePromisedAnnahmeStatus(id: String, isCalled: Boolean) {
        viewModelScope.launch {
            repository.updatePromisedAnnahmeStatus(id, isCalled)
        }
    }

    fun resetPromisedAnnahmenCalled() {
        viewModelScope.launch {
            repository.resetPromisedAnnahmenCalled()
        }
    }

    private var promisedCountdownJob: kotlinx.coroutines.Job? = null

    fun setPromisedThroughCallActive(active: Boolean) {
        isPromisedThroughCallActive.value = active
        if (active) {
            if (_activeCall.value == null && !_showWrapUpDialog.value) {
                startPromisedThroughCallCountdown()
            }
        } else {
            promisedCountdownJob?.cancel()
            promisedThroughCallCountdown.value = null
        }
    }

    fun startPromisedThroughCallCountdown() {
        if (!isPromisedThroughCallActive.value) return
        promisedCountdownJob?.cancel()
        promisedCountdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                promisedThroughCallCountdown.value = i
                kotlinx.coroutines.delay(1000)
                if (!isPromisedThroughCallActive.value) {
                    promisedThroughCallCountdown.value = null
                    return@launch
                }
            }
            promisedThroughCallCountdown.value = null
            triggerNextPromisedCall()
        }
    }

    fun triggerNextPromisedCall() {
        if (!isPromisedThroughCallActive.value) return
        viewModelScope.launch {
            val list = repository.getPromisedAnnahmenList().sortedBy { it.timestamp }
            val next = list.find { !it.isCalled }
            if (next != null) {
                repository.updatePromisedAnnahmeStatus(next.id, true)
                initiateCall(next.phone, next.name, callType = "promised_annahme")
            } else {
                isPromisedThroughCallActive.value = false
            }
        }
    }

    fun dismissNewAnnahmeDocumentAlert() {
        _newAnnahmeDocumentAlert.value = null
    }

    fun syncAnnahmeDokumenteNow() {
        viewModelScope.launch {
            try {
                repository.syncAnnahmeDokumenteFromSupabase()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun downloadAnnahmeDokument(
        doc: com.example.database.AnnahmeDokumentEntity,
        onResult: (ByteArray?) -> Unit
    ) {
        viewModelScope.launch {
            val bytes = repository.downloadAnnahmeDokument(doc)
            onResult(bytes)
        }
    }

    private val _customerMessageDraft = MutableStateFlow(CustomerMessageDraftState())
    val customerMessageDraft: StateFlow<CustomerMessageDraftState> = _customerMessageDraft.asStateFlow()

    fun setCustomerMessageNote(note: String) {
        _customerMessageDraft.value = _customerMessageDraft.value.copy(rawNote = note, error = null)
    }
    
    fun setGeneratedCustomerSubject(subject: String) {
        _customerMessageDraft.value = _customerMessageDraft.value.copy(
            subject = subject,
            error = null
        )
    }

    fun setGeneratedCustomerBody(body: String) {
        _customerMessageDraft.value = _customerMessageDraft.value.copy(
            body = body,
            error = null
        )
    }

    fun transcribeCustomerAudio(context: android.content.Context, audioUri: Uri) {
        viewModelScope.launch {
            _customerMessageDraft.value = _customerMessageDraft.value.copy(isLoading = true, error = null)
            try {
                val text = OpenAiClient(context).transcribeAudio(audioUri)
                _customerMessageDraft.value = _customerMessageDraft.value.copy(
                    transcript = text,
                    isLoading = false
                )
            } catch (e: Exception) {
                _customerMessageDraft.value = _customerMessageDraft.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Transkription fehlgeschlagen"
                )
            }
        }
    }

    fun generateCustomerMessage(
        context: android.content.Context,
        contact: ContactEntity?,
        nextAppointment: String?
    ) {
        viewModelScope.launch {
            val current = _customerMessageDraft.value
            _customerMessageDraft.value = current.copy(isLoading = true, error = null)
            try {
                val generated = OpenAiClient(context).generateCustomerMessage(
                    customerName = contact?.name ?: "Kunde",
                    customerEmail = contact?.email,
                    rawNote = current.rawNote,
                    transcript = current.transcript.takeIf { it.isNotBlank() },
                    nextAppointment = nextAppointment
                )
                _customerMessageDraft.value = current.copy(
                    subject = generated.subject,
                    body = generated.body,
                    isLoading = false
                )
            } catch (e: Exception) {
                _customerMessageDraft.value = current.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "KI-Erstellung fehlgeschlagen"
                )
            }
        }
    }

    fun saveCustomerMessageDraft(contact: ContactEntity?, audioFile: java.io.File? = null) {
        viewModelScope.launch {
            val draft = _customerMessageDraft.value
            val messageId = UUID.randomUUID().toString()
            repository.insertCustomerMessage(
                CustomerMessageEntity(
                    id = messageId,
                    contactId = contact?.id,
                    contactName = contact?.name ?: "Unbekannter Kunde",
                    contactEmail = contact?.email,
                    contactPhone = contact?.phone,
                    rawNote = draft.rawNote,
                    transcript = draft.transcript.takeIf { it.isNotBlank() },
                    subject = draft.subject,
                    body = draft.body,
                    provider = null,
                    status = "draft",
                    createdAt = System.currentTimeMillis()
                )
            )

            // Notiz (inkl. Sprachaufnahme) automatisch im Hintergrund
            // an Telegram weiterleiten + Supabase synchron halten.
            if (draft.rawNote.isNotBlank() || draft.transcript.isNotBlank() || audioFile != null) {
                com.example.util.TelegramForwarder.forwardNote(
                    context = repository.getContext(),
                    contactName = contact?.name ?: "Unbekannter Kunde",
                    company = null,
                    phone = contact?.phone,
                    note = draft.rawNote.ifBlank { draft.transcript },
                    transcript = draft.transcript.takeIf { it.isNotBlank() },
                    audioFile = audioFile,
                    messageId = messageId,
                    source = "gespraechsnotiz"
                )
            }
        }
    }

    fun sendCustomerMessage(
        context: android.content.Context,
        contact: ContactEntity?,
        provider: String
    ) {
        viewModelScope.launch {
            val draft = _customerMessageDraft.value
            val email = contact?.email
            if (email.isNullOrBlank()) {
                _customerMessageDraft.value = draft.copy(error = "Keine E-Mail-Adresse beim Kunden hinterlegt.")
                return@launch
            }

            val messageId = UUID.randomUUID().toString()
            val entity = CustomerMessageEntity(
                id = messageId,
                contactId = contact.id,
                contactName = contact.name,
                contactEmail = email,
                contactPhone = contact.phone,
                rawNote = draft.rawNote,
                transcript = draft.transcript.takeIf { it.isNotBlank() },
                subject = draft.subject,
                body = draft.body,
                provider = provider,
                status = "draft",
                createdAt = System.currentTimeMillis()
            )
            repository.insertCustomerMessage(entity)

            try {
                val sender = CustomerMailSender(context)
                if (provider == "gmail") {
                    sender.sendViaGmail(email, draft.subject, draft.body)
                } else {
                    sender.sendViaOutlook(email, draft.subject, draft.body)
                }
                repository.markCustomerMessageSent(messageId)
            } catch (e: Exception) {
                repository.markCustomerMessageFailed(messageId, e.localizedMessage ?: "Versand fehlgeschlagen")
                _customerMessageDraft.value = draft.copy(error = e.localizedMessage ?: "Versand fehlgeschlagen")
            }
        }
    }

    private var commandPollingJob: kotlinx.coroutines.Job? = null
    private val executedCommands = mutableSetOf<String>()

    fun startCommandPolling(context: android.content.Context) {
        if (commandPollingJob != null) return
        commandPollingJob = viewModelScope.launch {
            while (isActive) {
                // 1. Primary path: Fetch from dedicated app_commands table
                try {
                    val commands = com.example.util.SupabaseDbClient.getPendingCommands(context)
                    for (i in 0 until commands.length()) {
                        val cmd = commands.getJSONObject(i)
                        val id = cmd.optString("id")
                        val command = cmd.optString("command")
                        val createdAt = cmd.optString("created_at")
                        
                        if (id.isBlank() || executedCommands.contains(id)) continue
                        
                        try {
                            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                            val createdDate = format.parse(createdAt)
                            if (createdDate != null) {
                                val ageMs = System.currentTimeMillis() - createdDate.time
                                if (ageMs > 2 * 60 * 1000) {
                                    continue // Too old
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("StromrufViewModel", "Error parsing date: $createdAt", e)
                        }
                        
                        executedCommands.add(id)
                        com.example.util.SupabaseDbClient.markCommandDone(context, id)
                        
                        android.util.Log.d("StromrufViewModel", "Processing app_command: $command")
                        
                        when (command) {
                            "start_call" -> {
                                val payload = cmd.optJSONObject("payload")
                                val phone = payload?.optString("phone")
                                if (!phone.isNullOrBlank()) {
                                    val cName = payload.optString("name").takeIf { it.isNotBlank() }
                                    val isCallActive = com.example.service.DialerInCallService.activeCall.value != null || _activeCall.value != null
                                    if (isCallActive) {
                                        android.util.Log.w("StromrufViewModel", "Skipping remote call to $phone because a call is already active")
                                    } else {
                                        initiateCall(phone, cName, callType = "remote_command")
                                        kotlinx.coroutines.delay(1000)
                                    }
                                }
                            }
                            "open_quick_save" -> {
                                val payload = cmd.optJSONObject("payload")
                                val phone = payload?.optString("phone") ?: ""
                                val customerNo = payload?.optString("customer_no") ?: ""
                                val name = payload?.optString("name") ?: ""
                                openQuickSaveDialog(phone, customerNo, name)
                            }
                            "show_toast" -> {
                                val payload = cmd.optJSONObject("payload")
                                val message = payload?.optString("message") ?: ""
                                if (message.isNotBlank()) {
                                    triggerToast(message)
                                }
                            }
                            "save_customer" -> {
                                val payload = cmd.optJSONObject("payload")
                                val phone = payload?.optString("phone") ?: ""
                                val customerNo = payload?.optString("customer_no") ?: ""
                                val name = payload?.optString("name") ?: ""
                                if (customerNo.isNotBlank() && name.isNotBlank()) {
                                    addManualContact(
                                        name = name,
                                        phone = phone,
                                        company = "Kd.-Nr: $customerNo",
                                        email = ""
                                    )
                                    saveNeukunde(
                                        customerNumber = customerNo,
                                        phone = phone,
                                        customerName = name
                                    )
                                    triggerToast("Kunde $name ($customerNo) von Claude gespeichert! 💾")
                                }
                            }
                            "show_clipboard_bubble" -> {
                                val payload = cmd.optJSONObject("payload")
                                val text = payload?.optString("text") ?: ""
                                val isCustomerNumber = payload?.optBoolean("is_customer_number", false) ?: false
                                if (text.isNotBlank()) {
                                    showClipboardBubble(text, isCustomerNumber)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StromrufViewModel", "Error in primary command polling", e)
                }

                // 2. Secondary fallback path: Fetch from customer_messages table with status = 'pending_command'
                try {
                    val messagesJson = com.example.util.SupabaseDbClient.fetchTableRows(context, "customer_messages")
                    for (i in 0 until messagesJson.length()) {
                        val msg = messagesJson.getJSONObject(i)
                        val id = msg.optString("id")
                        val status = msg.optString("status")
                        if (status == "pending_command" && !id.isBlank() && !executedCommands.contains(id)) {
                            executedCommands.add(id)
                            
                            val command = msg.optString("subject") // subject -> command name
                            val bodyText = msg.optString("body") // body -> argument JSON/string
                            
                            // Mark message as completed in Supabase by patching status
                            val updatePayload = org.json.JSONObject().apply {
                                put("id", id)
                                put("status", "done_command")
                            }
                            com.example.util.SupabaseDbClient.upsertTableRow(context, "customer_messages", updatePayload)
                            
                            android.util.Log.d("StromrufViewModel", "Processing fallback customer_messages command: $command")
                            
                            when (command) {
                                "start_call", "initiate_call" -> {
                                    val payload = try { org.json.JSONObject(bodyText) } catch (e: Exception) { null }
                                    val phone = payload?.optString("phone") ?: bodyText
                                    if (phone.isNotBlank()) {
                                        val cName = payload?.optString("name")?.takeIf { it.isNotBlank() }
                                        initiateCall(phone, cName, callType = "remote_command")
                                        kotlinx.coroutines.delay(1000)
                                    }
                                }
                                "open_quick_save" -> {
                                    val payload = try { org.json.JSONObject(bodyText) } catch (e: Exception) { null }
                                    val phone = payload?.optString("phone") ?: ""
                                    val customerNo = payload?.optString("customer_no") ?: ""
                                    val name = payload?.optString("name") ?: ""
                                    openQuickSaveDialog(phone, customerNo, name)
                                }
                                "show_toast" -> {
                                    val payload = try { org.json.JSONObject(bodyText) } catch (e: Exception) { null }
                                    val message = payload?.optString("message") ?: bodyText
                                    if (message.isNotBlank()) {
                                        triggerToast(message)
                                    }
                                }
                                "save_customer" -> {
                                    val payload = try { org.json.JSONObject(bodyText) } catch (e: Exception) { null }
                                    val phone = payload?.optString("phone") ?: ""
                                    val customerNo = payload?.optString("customer_no") ?: ""
                                    val name = payload?.optString("name") ?: ""
                                    if (customerNo.isNotBlank() && name.isNotBlank()) {
                                        addManualContact(
                                            name = name,
                                            phone = phone,
                                            company = "Kd.-Nr: $customerNo",
                                            email = ""
                                        )
                                        saveNeukunde(
                                            customerNumber = customerNo,
                                            phone = phone,
                                            customerName = name
                                        )
                                        triggerToast("Kunde $name ($customerNo) von Claude gespeichert! 💾")
                                    }
                                }
                                "show_clipboard_bubble" -> {
                                    val payload = try { org.json.JSONObject(bodyText) } catch (e: Exception) { null }
                                    val text = payload?.optString("text") ?: bodyText
                                    val isCustomerNumber = payload?.optBoolean("is_customer_number", false) ?: false
                                    if (text.isNotBlank()) {
                                        showClipboardBubble(text, isCustomerNumber)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("StromrufViewModel", "Error in fallback command polling", e)
                }
                
                kotlinx.coroutines.delay(5000) // Poll every 5 seconds for responsive Claude API action
            }
        }
    }
}

class StromrufViewModelFactory(private val repository: StromrufRepository) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StromrufViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StromrufViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}
