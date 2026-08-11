package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val company: String?,
    val email: String?,
    val lastCallAt: Long?,
    val lastOutcome: String?,
    val isHotBox: Boolean = false,
    val hasBeenCalledInHotCycle: Boolean = false,
    val hotBoxStartHour: Int? = null,
    val hotBoxEndHour: Int? = null,
    val hotBoxWeekdays: String? = null,
    val callReason: String? = null,
    val hotBoxListName: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val consumption: Long? = null,
    val zipCode: String? = null,
    val energyType: String? = null // "Strom", "Gas", or null/empty
) {
    fun isReachableNow(cal: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        val currentDay = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val weekdaysStr = hotBoxWeekdays
        if (!weekdaysStr.isNullOrEmpty()) {
            val daysList = weekdaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (daysList.isNotEmpty() && currentDay !in daysList) {
                return false
            }
        }
        val startRaw = hotBoxStartHour
        val endRaw = hotBoxEndHour
        if (startRaw != null && endRaw != null) {
            val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val start = if (startRaw in 0..24) startRaw * 60 else startRaw
            val end = if (endRaw in 0..24) endRaw * 60 else endRaw
            return if (start <= end) {
                currentMinutes in start..end
            } else {
                currentMinutes >= start || currentMinutes <= end
            }
        }
        return true
    }
}

@Entity(tableName = "followups")
data class FollowUpEntity(
    @PrimaryKey val id: String,
    val contactId: String?,
    val contactName: String,
    val contactPhone: String,
    val note: String?,
    val dueAt: Long,
    val isCompleted: Boolean = false,
    val callReason: String? = null
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val contactName: String?,
    val outcome: String,
    val note: String?,
    val timestamp: Long,
    val durationSeconds: Long = 0L,
    val callReason: String? = null,
    val callType: String = "einwaehlen"
)

@Entity(tableName = "ai_calls")
data class AiCallEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val contactName: String?,
    val timestamp: Long,
    val audioFilePath: String?,
    val transcript: String,
    val durationSeconds: Long = 0L,
    val notes: String = ""
)

@Entity(tableName = "annahmen")
data class AnnahmeEntity(
    @PrimaryKey val id: String,
    val type: String,          // "Strom" or "Gas"
    val customerType: String,  // "Neukunde" or "Bestandskunde"
    val consumption: Long,     // Consumption/volume in kWh
    val termYears: Int,        // Contract term in years (Laufzeit)
    val customerNumber: String, // Kundennummer
    val timestamp: Long
) {
    val weightedVolume: Long
        get() = consumption * termYears
}

@Entity(tableName = "promised_annahmen")
data class PromisedAnnahmeEntity(
    @PrimaryKey val id: String,
    val customerNumber: String,
    val name: String,
    val phone: String,
    val timestamp: Long,
    val isCalled: Boolean = false
)

@Entity(tableName = "annahme_dokumente")
data class AnnahmeDokumentEntity(
    @PrimaryKey val id: String,
    val customerNumber: String,
    val fileName: String,
    val fileType: String,
    val fileContentString: String,
    val localFilePath: String,
    val timestamp: Long
)

@Entity(tableName = "neukunden")
data class NeukundeEntity(
    @PrimaryKey val id: String,
    val dateCreated: Long,
    val customerNumber: String,
    val phone: String,
    val callAttempts: Int,
    val status: String, // "Anrufen", "Datenmail schreiben", "Angebot erstellen", "Zum Stand fragen"
    val customerName: String? = null,
    val company: String? = null,
    val email: String? = null,
    val deliveryAddress: String? = null,
    val meterNumber: String? = null,
    val consumption: Long? = null,
    val energyType: String? = null // "Strom" oder "Gas"
)

@Entity(tableName = "heisse_angebote")
data class HeissAngebotEntity(
    @PrimaryKey val id: String,
    val dateCreated: Long,
    val customerNumber: String,
    val phone: String,
    val callAttempts: Int,
    val notes: String
)

@Entity(tableName = "customer_messages")
data class CustomerMessageEntity(
    @PrimaryKey val id: String,
    val contactId: String?,
    val contactName: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val rawNote: String,
    val transcript: String?,
    val subject: String,
    val body: String,
    val provider: String?, // "gmail", "outlook", null
    val status: String, // "draft", "sent", "failed"
    val createdAt: Long,
    val sentAt: Long? = null,
    val errorMessage: String? = null
)



