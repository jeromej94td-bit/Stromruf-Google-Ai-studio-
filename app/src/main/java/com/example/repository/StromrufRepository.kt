package com.example.repository

import android.content.Context
import com.example.database.CallLogEntity
import com.example.database.ContactEntity
import com.example.database.FollowUpEntity
import com.example.database.AiCallEntity
import com.example.database.AnnahmeEntity
import com.example.database.StromrufDao
import com.example.util.SupabaseDbClient
import kotlinx.coroutines.flow.Flow

class StromrufRepository(private val context: Context, private val dao: StromrufDao) {

    fun getContext(): Context = context

    val allContacts: Flow<List<ContactEntity>> = dao.getAllContacts()
    val activeFollowUps: Flow<List<FollowUpEntity>> = dao.getActiveFollowUps()
    val allCallLogs: Flow<List<CallLogEntity>> = dao.getAllCallLogs()
    val allAiCalls: Flow<List<AiCallEntity>> = dao.getAllAiCalls()
    val allAnnahmen: Flow<List<AnnahmeEntity>> = dao.getAllAnnahmen()
    val allAnnahmeDokumente: Flow<List<com.example.database.AnnahmeDokumentEntity>> = dao.getAllAnnahmeDokumente()
    val allNeukunden: Flow<List<com.example.database.NeukundeEntity>> = dao.getAllNeukunden()
    val allHeisseAngebote: Flow<List<com.example.database.HeissAngebotEntity>> = dao.getAllHeisseAngebote()

    suspend fun getAllCallLogsList(): List<CallLogEntity> {
        return dao.getAllCallLogsList()
    }

    suspend fun getActiveFollowUpsList(): List<FollowUpEntity> {
        return dao.getActiveFollowUpsList()
    }

    suspend fun getContactByPhone(phone: String): ContactEntity? {
        val normalized = phone.replace("[^\\d+]".toRegex(), "")
        val direct = dao.getContactByPhone(normalized)
        if (direct != null) return direct

        var alt: ContactEntity? = null
        if (normalized.startsWith("+49")) {
            val alternate = "0" + normalized.substring(3)
            alt = dao.getContactByPhone(alternate)
        } else if (normalized.startsWith("0")) {
            val alternate = "+49" + normalized.substring(1)
            alt = dao.getContactByPhone(alternate)
        }
        if (alt != null) return alt

        try {
            val all = dao.getAllContactsList()
            return all.firstOrNull { com.example.util.ContactsUtil.arePhoneNumbersMatching(it.phone, phone) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun getContactById(id: String): ContactEntity? {
        return dao.getContactById(id)
    }

    suspend fun getAllContactsList(): List<ContactEntity> = dao.getAllContactsList()

    suspend fun insertContact(contact: ContactEntity) {
        dao.insertContact(contact)
        if (com.example.util.ContactsUtil.hasWriteContactsPermission(context) && contact.name.isNotBlank() && contact.phone.isNotBlank()) {
            runCatching {
                com.example.util.ContactsUtil.saveContactToSystemDirectly(context, contact.name, contact.phone)
            }
        }
        SupabaseDbClient.upsertContact(context, contact)
    }

    /** Internal automation update without writing to the Android address book again. */
    suspend fun upsertContactForAutomation(contact: ContactEntity) {
        dao.insertContact(contact)
        SupabaseDbClient.upsertContact(context, contact)
    }

    suspend fun resetHotCycle() {
        dao.resetHotCycle()
        val activeHotContacts = dao.getAllContactsList().filter { it.isHotBox }
        activeHotContacts.forEach {
            SupabaseDbClient.upsertContact(context, it.copy(hasBeenCalledInHotCycle = false))
        }
    }

    suspend fun deleteContactById(id: String) {
        dao.deleteContactById(id)
        SupabaseDbClient.deleteContact(context, id)
    }

    suspend fun insertFollowUp(followUp: FollowUpEntity, preserveExactTime: Boolean = false, syncImmediately: Boolean = true): FollowUpEntity {
        val activeFollowUps = dao.getActiveFollowUpsList()
        var currentDueAt = followUp.dueAt

        var clashFound = !preserveExactTime
        while (clashFound) {
            clashFound = false
            for (existing in activeFollowUps) {
                if (existing.id != followUp.id && Math.abs(existing.dueAt - currentDueAt) < 60000) {
                    clashFound = true
                    currentDueAt += 10 * 60 * 1000
                    break
                }
            }
        }

        val finalizedFollowUp = if (currentDueAt != followUp.dueAt) {
            followUp.copy(dueAt = currentDueAt)
        } else {
            followUp
        }
        dao.insertFollowUp(finalizedFollowUp)
        if (syncImmediately) SupabaseDbClient.upsertFollowUp(context, finalizedFollowUp)
        return finalizedFollowUp
    }

    suspend fun syncFollowUp(id: String): Boolean {
        val item = dao.getFollowUpById(id) ?: return false
        return SupabaseDbClient.upsertFollowUp(context, item)
    }

    suspend fun getFollowUpById(id: String): FollowUpEntity? {
        return dao.getFollowUpById(id)
    }

    suspend fun updateFollowUpStatus(id: String, isCompleted: Boolean) {
        dao.updateFollowUpStatus(id, isCompleted)
        val f = dao.getFollowUpById(id)
        if (f != null) {
            SupabaseDbClient.upsertFollowUp(context, f)
        }
    }

    suspend fun deleteFollowUpById(id: String) {
        dao.deleteFollowUpById(id)
        SupabaseDbClient.deleteFollowUp(context, id)
    }

    suspend fun insertCallLog(callLog: CallLogEntity) {
        dao.insertCallLog(callLog)
        SupabaseDbClient.upsertCallLog(context, callLog)
        // A single central hook means normal dialer, Hotbox and system call-log reconciliation all
        // share the same bounded Smart Call retry policy. The manager ignores unrelated calls.
        runCatching {
            com.example.smartretry.SmartRetryManager.onCallLog(context, this, callLog)
        }
    }

    suspend fun insertAiCall(aiCall: AiCallEntity) {
        dao.insertAiCall(aiCall)
        SupabaseDbClient.upsertAiCall(context, aiCall)
    }

    suspend fun deleteAiCallById(id: String) {
        dao.deleteAiCallById(id)
        SupabaseDbClient.deleteAiCall(context, id)
    }

    suspend fun insertAnnahme(annahme: AnnahmeEntity) {
        dao.insertAnnahme(annahme)
        SupabaseDbClient.upsertAnnahme(context, annahme)
    }

    suspend fun deleteAnnahmeById(id: String) {
        dao.deleteAnnahmeById(id)
        SupabaseDbClient.deleteAnnahme(context, id)
    }

    val allPromisedAnnahmen: Flow<List<com.example.database.PromisedAnnahmeEntity>> = dao.getAllPromisedAnnahmen()

    suspend fun getPromisedAnnahmenList(): List<com.example.database.PromisedAnnahmeEntity> {
        return dao.getPromisedAnnahmenList()
    }

    suspend fun insertPromisedAnnahme(item: com.example.database.PromisedAnnahmeEntity) {
        dao.insertPromisedAnnahme(item)
        SupabaseDbClient.upsertPromisedAnnahme(context, item)
    }

    suspend fun updatePromisedAnnahmeStatus(id: String, isCalled: Boolean) {
        dao.updatePromisedAnnahmeStatus(id, isCalled)
        val item = dao.getPromisedAnnahmenList().firstOrNull { it.id == id }
        if (item != null) {
            SupabaseDbClient.upsertPromisedAnnahme(context, item)
        }
    }

    suspend fun resetPromisedAnnahmenCalled() {
        dao.resetPromisedAnnahmenCalled()
        val allPromised = dao.getPromisedAnnahmenList()
        allPromised.forEach {
            SupabaseDbClient.upsertPromisedAnnahme(context, it)
        }
    }

    suspend fun deletePromisedAnnahmeById(id: String) {
        dao.deletePromisedAnnahmeById(id)
        SupabaseDbClient.deletePromisedAnnahme(context, id)
    }

    suspend fun insertAnnahmeDokument(doc: com.example.database.AnnahmeDokumentEntity) {
        dao.insertAnnahmeDokument(doc)
    }

    suspend fun deleteAnnahmeDokumentById(id: String) {
        dao.deleteAnnahmeDokumentById(id)
    }

    suspend fun getAllNeukundenList(): List<com.example.database.NeukundeEntity> {
        return dao.getAllNeukundenList()
    }

    suspend fun insertNeukunde(item: com.example.database.NeukundeEntity) {
        dao.insertNeukunde(item)
        com.example.leads.LeadAutomation.schedule(context, item)
        com.example.leads.LeadAutomation.enqueueCloudSync(context, item.id)
    }

    suspend fun insertNeukundeWithContact(item: com.example.database.NeukundeEntity, contact: ContactEntity) {
        dao.insertNeukundeAndContact(item, contact)
        com.example.leads.LeadAutomation.schedule(context, item)
        com.example.leads.LeadAutomation.enqueueCloudSync(context, item.id)
    }

    suspend fun deleteNeukundeById(id: String) {
        com.example.leads.LeadAutomation.cancel(context, id)
        dao.deleteNeukundeById(id)
        SupabaseDbClient.deleteNeukunde(context, id)
    }

    suspend fun getAllHeisseAngeboteList(): List<com.example.database.HeissAngebotEntity> {
        return dao.getAllHeisseAngeboteList()
    }

    suspend fun insertHeissAngebot(item: com.example.database.HeissAngebotEntity) {
        dao.insertHeissAngebot(item)
        SupabaseDbClient.upsertHeissAngebot(context, item)
    }

    suspend fun deleteHeissAngebotById(id: String) {
        dao.deleteHeissAngebotById(id)
        SupabaseDbClient.deleteHeissAngebot(context, id)
    }

    suspend fun syncAnnahmeDokumenteFromSupabase(): List<com.example.database.AnnahmeDokumentEntity> {
        val remote = SupabaseDbClient.fetchAnnahmeDokumente(context)
        remote.forEach { dokument -> dao.insertAnnahmeDokument(dokument) }
        val remoteIds = remote.map { it.id }
        if (remoteIds.isEmpty()) dao.deleteAllAnnahmeDokumente()
        else dao.deleteAnnahmeDokumenteNotIn(remoteIds)
        return remote
    }

    suspend fun downloadAnnahmeDokument(doc: com.example.database.AnnahmeDokumentEntity): ByteArray? {
        return SupabaseDbClient.downloadAnnahmeDokument(context, doc)
    }

    val allCustomerMessages: kotlinx.coroutines.flow.Flow<List<com.example.database.CustomerMessageEntity>> = dao.getAllCustomerMessages()

    fun getCustomerMessagesForContact(contactId: String): kotlinx.coroutines.flow.Flow<List<com.example.database.CustomerMessageEntity>> {
        return dao.getCustomerMessagesForContact(contactId)
    }

    suspend fun insertCustomerMessage(message: com.example.database.CustomerMessageEntity) {
        dao.insertCustomerMessage(message)
        try {
            val payload = org.json.JSONObject().apply {
                put("id", message.id)
                put("contact_id", message.contactId ?: org.json.JSONObject.NULL)
                put("contact_name", message.contactName)
                put("contact_email", message.contactEmail ?: org.json.JSONObject.NULL)
                put("contact_phone", message.contactPhone ?: org.json.JSONObject.NULL)
                put("raw_note", message.rawNote)
                put("transcript", message.transcript ?: org.json.JSONObject.NULL)
                put("subject", message.subject)
                put("body", message.body)
                put("provider", message.provider ?: org.json.JSONObject.NULL)
                put("status", message.status)
                put("created_at_ms", message.createdAt)
                put("sent_at_ms", message.sentAt ?: org.json.JSONObject.NULL)
                put("error_message", message.errorMessage ?: org.json.JSONObject.NULL)
            }
            SupabaseDbClient.upsertTableRow(context, "customer_messages", payload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markCustomerMessageSent(id: String) {
        dao.updateCustomerMessageStatus(
            id = id,
            status = "sent",
            sentAt = System.currentTimeMillis(),
            errorMessage = null
        )
        try {
            val payload = org.json.JSONObject().apply {
                put("id", id)
                put("status", "sent")
                put("sent_at_ms", System.currentTimeMillis())
            }
            SupabaseDbClient.upsertTableRow(context, "customer_messages", payload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markCustomerMessageFailed(id: String, error: String) {
        dao.updateCustomerMessageStatus(
            id = id,
            status = "failed",
            sentAt = null,
            errorMessage = error
        )
        try {
            val payload = org.json.JSONObject().apply {
                put("id", id)
                put("status", "failed")
                put("error_message", error)
            }
            SupabaseDbClient.upsertTableRow(context, "customer_messages", payload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
