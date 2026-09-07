package com.example.leads

import com.example.database.NeukundeEntity
import java.util.Calendar

object LeadWorkflow {
    const val CALL = "Anrufen"
    const val MAIL = "Datenmail schreiben"
    const val OFFER = "Angebot erstellen"
    const val OFFER_SENT = "Angebot gesendet"
    const val FOLLOW_UP = "Zum Stand fragen"
    const val DONE = "Abgeschlossen"
    const val ARCHIVED = "Archiviert"

    val active = setOf(CALL, MAIL, OFFER, OFFER_SENT, FOLLOW_UP)

    fun initial(phone: String) = if (phone.isBlank()) MAIL else CALL

    fun atToday(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val target = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    fun twoDaysAt17(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = now; add(Calendar.DAY_OF_YEAR, 2)
        set(Calendar.HOUR_OF_DAY, 17); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun next(item: NeukundeEntity, now: Long = System.currentTimeMillis()): NeukundeEntity = when (item.status) {
        CALL -> item.copy(status = MAIL, nextActionAt = null, updatedAt = now)
        MAIL -> item.copy(status = OFFER, nextActionAt = atToday(15, 50, now), updatedAt = now)
        OFFER -> item.copy(status = OFFER_SENT, offerSentAt = now, nextActionAt = twoDaysAt17(now), updatedAt = now)
        OFFER_SENT -> item.copy(status = FOLLOW_UP, nextActionAt = item.nextActionAt ?: twoDaysAt17(item.offerSentAt ?: now), updatedAt = now)
        FOLLOW_UP -> item.copy(status = DONE, nextActionAt = null, completedAt = now, updatedAt = now)
        else -> item
    }

    fun missed(item: NeukundeEntity, now: Long = System.currentTimeMillis()) = item.copy(
        callAttempts = item.callAttempts + 1,
        status = CALL,
        nextActionAt = now + 2 * 60 * 60 * 1000L,
        updatedAt = now
    )
}
