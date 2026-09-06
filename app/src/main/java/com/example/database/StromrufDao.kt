package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StromrufDao {

    // --- Contacts ---
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsList(): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactById(id: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE phone = :phone LIMIT 1")
    suspend fun getContactByPhone(phone: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("UPDATE contacts SET hasBeenCalledInHotCycle = 0 WHERE isHotBox = 1")
    suspend fun resetHotCycle()

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: String)
    
    @Query("DELETE FROM contacts WHERE id NOT IN (:ids)")
    suspend fun deleteContactsNotIn(ids: List<String>)

    // --- Follow-ups ---
    @Query("SELECT * FROM followups WHERE isCompleted = 0 ORDER BY dueAt ASC")
    fun getActiveFollowUps(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM followups WHERE isCompleted = 0")
    suspend fun getActiveFollowUpsList(): List<FollowUpEntity>

    @Query("SELECT * FROM followups WHERE id = :id LIMIT 1")
    suspend fun getFollowUpById(id: String): FollowUpEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUp(followUp: FollowUpEntity)

    @Query("UPDATE followups SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateFollowUpStatus(id: String, isCompleted: Boolean)

    @Query("DELETE FROM followups WHERE id = :id")
    suspend fun deleteFollowUpById(id: String)
    
    @Query("DELETE FROM followups WHERE id NOT IN (:ids)")
    suspend fun deleteFollowUpsNotIn(ids: List<String>)

    // --- Call Logs ---
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs")
    suspend fun getAllCallLogsList(): List<CallLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)
    
    @Query("DELETE FROM call_logs WHERE id NOT IN (:ids) AND id NOT LIKE 'system_%'")
    suspend fun deleteCallLogsNotIn(ids: List<String>)

    // --- AI Calls ---
    @Query("SELECT * FROM ai_calls ORDER BY timestamp DESC")
    fun getAllAiCalls(): Flow<List<AiCallEntity>>

    @Query("SELECT * FROM ai_calls")
    suspend fun getAllAiCallsList(): List<AiCallEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiCall(aiCall: AiCallEntity)

    @Query("DELETE FROM ai_calls WHERE id = :id")
    suspend fun deleteAiCallById(id: String)
    
    @Query("DELETE FROM ai_calls WHERE id NOT IN (:ids)")
    suspend fun deleteAiCallsNotIn(ids: List<String>)

    // --- Annahmen ---
    @Query("SELECT * FROM annahmen ORDER BY timestamp DESC")
    fun getAllAnnahmen(): Flow<List<AnnahmeEntity>>

    @Query("SELECT * FROM annahmen")
    suspend fun getAllAnnahmenList(): List<AnnahmeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnahme(annahme: AnnahmeEntity)

    @Query("DELETE FROM annahmen WHERE id = :id")
    suspend fun deleteAnnahmeById(id: String)
    
    @Query("DELETE FROM annahmen WHERE id NOT IN (:ids)")
    suspend fun deleteAnnahmenNotIn(ids: List<String>)

    // --- Promised Annahmen ---
    @Query("SELECT * FROM promised_annahmen ORDER BY timestamp DESC")
    fun getAllPromisedAnnahmen(): Flow<List<PromisedAnnahmeEntity>>

    @Query("SELECT * FROM promised_annahmen")
    suspend fun getPromisedAnnahmenList(): List<PromisedAnnahmeEntity>

    @Query("SELECT * FROM promised_annahmen WHERE id = :id LIMIT 1")
    suspend fun getPromisedAnnahmeById(id: String): PromisedAnnahmeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromisedAnnahme(item: PromisedAnnahmeEntity)

    @Query("UPDATE promised_annahmen SET isCalled = :isCalled WHERE id = :id")
    suspend fun updatePromisedAnnahmeStatus(id: String, isCalled: Boolean)

    @Query("UPDATE promised_annahmen SET isCalled = 0")
    suspend fun resetPromisedAnnahmenCalled()

    @Query("DELETE FROM promised_annahmen WHERE id = :id")
    suspend fun deletePromisedAnnahmeById(id: String)
    
    @Query("DELETE FROM promised_annahmen WHERE id NOT IN (:ids)")
    suspend fun deletePromisedAnnahmenNotIn(ids: List<String>)

    // --- Annahme Dokumente ---
    @Query("SELECT * FROM annahme_dokumente ORDER BY timestamp DESC")
    fun getAllAnnahmeDokumente(): Flow<List<AnnahmeDokumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnahmeDokument(doc: AnnahmeDokumentEntity)

    @Query("DELETE FROM annahme_dokumente WHERE id = :id")
    suspend fun deleteAnnahmeDokumentById(id: String)

    @Query("DELETE FROM annahme_dokumente WHERE id NOT IN (:ids)")
    suspend fun deleteAnnahmeDokumenteNotIn(ids: List<String>)

    @Query("DELETE FROM annahme_dokumente")
    suspend fun deleteAllAnnahmeDokumente()

    // --- Neukunden ---
    @Query("SELECT * FROM neukunden ORDER BY dateCreated DESC")
    fun getAllNeukunden(): Flow<List<NeukundeEntity>>

    @Query("SELECT * FROM neukunden")
    suspend fun getAllNeukundenList(): List<NeukundeEntity>

    @Query("SELECT * FROM neukunden WHERE id = :id LIMIT 1")
    suspend fun getNeukundeById(id: String): NeukundeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNeukunde(neukunde: NeukundeEntity)

    @Transaction
    suspend fun insertNeukundeAndContact(neukunde: NeukundeEntity, contact: ContactEntity) {
        insertContact(contact)
        insertNeukunde(neukunde)
    }

    @Query("DELETE FROM neukunden WHERE id = :id")
    suspend fun deleteNeukundeById(id: String)
    
    @Query("DELETE FROM neukunden WHERE id NOT IN (:ids)")
    suspend fun deleteNeukundenNotIn(ids: List<String>)

    // --- Heiße Angebote ---
    @Query("SELECT * FROM heisse_angebote ORDER BY dateCreated DESC")
    fun getAllHeisseAngebote(): Flow<List<HeissAngebotEntity>>

    @Query("SELECT * FROM heisse_angebote")
    suspend fun getAllHeisseAngeboteList(): List<HeissAngebotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeissAngebot(item: HeissAngebotEntity)

    @Query("DELETE FROM heisse_angebote WHERE id = :id")
    suspend fun deleteHeissAngebotById(id: String)
    
    @Query("DELETE FROM heisse_angebote WHERE id NOT IN (:ids)")
    suspend fun deleteHeisseAngeboteNotIn(ids: List<String>)

    // --- Customer Messages ---
    @Query("SELECT * FROM customer_messages ORDER BY createdAt DESC")
    fun getAllCustomerMessages(): Flow<List<CustomerMessageEntity>>

    @Query("SELECT * FROM customer_messages")
    suspend fun getAllCustomerMessagesList(): List<CustomerMessageEntity>

    @Query("SELECT * FROM customer_messages WHERE contactId = :contactId ORDER BY createdAt DESC")
    fun getCustomerMessagesForContact(contactId: String): Flow<List<CustomerMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomerMessage(message: CustomerMessageEntity)
    
    @Query("DELETE FROM customer_messages WHERE id NOT IN (:ids)")
    suspend fun deleteCustomerMessagesNotIn(ids: List<String>)

    @Query("UPDATE customer_messages SET status = :status, sentAt = :sentAt, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateCustomerMessageStatus(
        id: String,
        status: String,
        sentAt: Long?,
        errorMessage: String?
    )
}
