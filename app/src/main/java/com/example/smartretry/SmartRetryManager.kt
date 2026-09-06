package com.example.smartretry

import android.content.Context
import com.example.database.AppDatabase
import com.example.database.CallLogEntity
import com.example.database.ContactEntity
import com.example.database.FollowUpEntity
import com.example.receiver.FollowUpAlarmScheduler
import com.example.repository.StromrufRepository
import com.example.util.ContactsUtil
import com.example.util.SupabaseDbClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Deterministic follow-up safety net for appointments created by Smart Calls.
 *
 * Gemma/Whisper are responsible only for understanding the conversation and creating the first
 * appointment. From that point on this class owns the retry policy so an LLM can never create an
 * unbounded retry loop.
 *
 * Rules:
 * - only follow appointments whose callReason/note identifies them as Smart Call / Smart Retry;
 * - an actual "nicht erreicht" counts as one attempt;
 * - after attempt 1 and 2 retry in roughly two hours, after attempt 3 move to the next day;
 *   the three-step rhythm repeats until attempt 8;
 * - after attempt 8 automatic chasing stops;
 * - a due reminder which the user simply does not call is rolled forward without increasing the
 *   attempt counter;
 * - a successful call immediately closes the retry chain;
 * - retry contacts are kept in the Hotbox and reset to "not called in this cycle" so they remain
 *   eligible ahead of already-called Hotbox contacts.
 */
object SmartRetryManager {
    const val MAX_MISSED_ATTEMPTS = 8
    private const val TWO_HOURS_MS = 2L * 60L * 60L * 1000L
    private const val RECENT_LOG_WINDOW_MS = 15L * 60L * 1000L
    private const val DUPLICATE_CALL_WINDOW_MS = 90L * 1000L
    private const val APPOINTMENT_MATCH_EARLY_MS = 3L * 60L * 60L * 1000L
    private const val APPOINTMENT_MATCH_LATE_MS = 2L * 24L * 60L * 60L * 1000L
    private const val LATEST_SAME_DAY_HOUR = 20
    private const val RETRY_REASON = "Smart Retry"
    private const val DEFAULT_HOTBOX = "Hotbox"

    private val mutex = Mutex()

    /** Called centrally after a fresh call log is persisted. Historical sync rows are ignored. */
    suspend fun onCallLog(context: Context, repository: StromrufRepository, callLog: CallLogEntity) {
        val now = System.currentTimeMillis()
        if (abs(now - callLog.timestamp) > RECENT_LOG_WINDOW_MS) return
        if (callLog.phone.isBlank()) return

        mutex.withLock {
            val active = repository.getActiveFollowUpsList()
                .filter { samePhone(it.contactPhone, callLog.phone) && isSmartTracked(it) }
            val retryId = retryId(callLog.phone)
            val retry = repository.getFollowUpById(retryId)?.takeUnless { it.isCompleted }
            val relevant = (active + listOfNotNull(retry)).distinctBy { it.id }

            if (isMissed(callLog.outcome)) {
                val appointment = relevant.firstOrNull { matchesAppointmentWindow(it, callLog.timestamp) }
                    ?: return@withLock
                handleMissedCall(context, repository, callLog, appointment, relevant)
            } else if (isReached(callLog.outcome)) {
                if (relevant.isNotEmpty()) {
                    finishChain(context, repository, callLog, relevant)
                }
            }
        }
    }

    /**
     * Called by the alarm receiver. A reminder that is ignored must not disappear: it becomes or
     * updates one Smart Retry follow-up and is scheduled again, without counting a failed attempt.
     */
    suspend fun onReminderDue(
        context: Context,
        followUpId: String,
        firedDueAt: Long = System.currentTimeMillis()
    ) {
        if (followUpId.isBlank()) return
        mutex.withLock {
            val repository = repository(context)
            val fired = repository.getFollowUpById(followUpId) ?: return@withLock
            if (fired.isCompleted || !isSmartTracked(fired)) return@withLock

            // A f?lliger Smart-Call-Termin bleibt ?berf?llig und damit ganz oben in der
            // dynamischen Hotbox, bis wirklich angerufen wurde. Nur ein tats?chlicher
            // "nicht erreicht"-Call erzeugt einen Retry.
            ensureHotBox(
                context = context,
                repository = repository,
                phone = fired.contactPhone,
                fallbackName = fired.contactName,
                attempt = attemptFrom(fired.note),
                lastOutcome = null,
                lastCallAt = null,
                active = true
            )
            syncPriority(context, fired, "hoch")
        }
    }

    /** Marks a newly detected Smart-Call appointment as dynamically Hotbox-eligible immediately. */
    suspend fun registerAppointment(
        context: Context,
        repository: StromrufRepository,
        followUp: FollowUpEntity
    ) {
        mutex.withLock {
            ensureHotBox(
                context = context,
                repository = repository,
                phone = followUp.contactPhone,
                fallbackName = followUp.contactName,
                attempt = 0,
                lastOutcome = null,
                lastCallAt = null,
                active = true
            )
            syncPriority(context, followUp, "hoch")
        }
    }

    private suspend fun handleMissedCall(
        context: Context,
        repository: StromrufRepository,
        callLog: CallLogEntity,
        appointment: FollowUpEntity,
        relevant: List<FollowUpEntity>
    ) {
        val targetId = retryId(callLog.phone)
        val currentRetry = repository.getFollowUpById(targetId)?.takeUnless { it.isCompleted }
        val previousLastAttemptAt = lastAttemptAt(currentRetry?.note)
        if (previousLastAttemptAt > 0L && abs(callLog.timestamp - previousLastAttemptAt) < DUPLICATE_CALL_WINDOW_MS) {
            return
        }

        val previousAttempt = currentRetry?.let { attemptFrom(it.note) }
            ?: relevant.maxOfOrNull { attemptFrom(it.note) }
            ?: 0
        val attempt = (previousAttempt + 1).coerceAtMost(MAX_MISSED_ATTEMPTS)

        relevant.forEach {
            if (!it.isCompleted && it.id != targetId) {
                repository.updateFollowUpStatus(it.id, true)
                FollowUpAlarmScheduler.cancelAlarm(context, it.id)
            }
        }

        ensureHotBox(
            context = context,
            repository = repository,
            phone = callLog.phone,
            fallbackName = callLog.contactName ?: appointment.contactName,
            attempt = attempt,
            lastOutcome = "nicht_erreicht",
            lastCallAt = callLog.timestamp,
            active = attempt < MAX_MISSED_ATTEMPTS
        )

        if (attempt >= MAX_MISSED_ATTEMPTS) {
            val history = FollowUpEntity(
                id = targetId,
                contactId = appointment.contactId,
                contactName = callLog.contactName ?: appointment.contactName,
                contactPhone = callLog.phone,
                note = noteFor(
                    attempt = attempt,
                    lastAttemptAt = callLog.timestamp,
                    message = "8-mal hintereinander nicht erreicht – automatische Nachverfolgung beendet",
                    originalAppointmentAt = originalAppointmentAt(currentRetry?.note).takeIf { it > 0L } ?: appointment.dueAt
                ),
                dueAt = callLog.timestamp,
                isCompleted = true,
                callReason = RETRY_REASON
            )
            repository.insertFollowUp(history)
            FollowUpAlarmScheduler.cancelAlarm(context, targetId)
            syncPriority(context, history, "hoch")
            return
        }

        val base = currentRetry ?: appointment
        val originalAppointmentAt = originalAppointmentAt(currentRetry?.note)
            .takeIf { it > 0L } ?: originalAppointmentAt(appointment.note).takeIf { it > 0L } ?: appointment.dueAt
        val due = nextDue(callLog.timestamp, attempt, originalAppointmentAt)
        saveRetry(
            context = context,
            repository = repository,
            base = base,
            retryId = targetId,
            attempt = attempt,
            lastAttemptAt = callLog.timestamp,
            dueAt = due,
            message = "Nicht erreicht – Versuch $attempt/$MAX_MISSED_ATTEMPTS; Kunde bleibt priorisiert in der Hotbox"
        )
    }

    private suspend fun finishChain(
        context: Context,
        repository: StromrufRepository,
        callLog: CallLogEntity,
        relevant: List<FollowUpEntity>
    ) {
        relevant.distinctBy { it.id }.forEach {
            if (!it.isCompleted) repository.updateFollowUpStatus(it.id, true)
            FollowUpAlarmScheduler.cancelAlarm(context, it.id)
        }
        FollowUpAlarmScheduler.cancelAlarm(context, retryId(callLog.phone))

        val existing = repository.getContactByPhone(callLog.phone) ?: return
        repository.upsertContactForAutomation(
            existing.copy(
                lastCallAt = callLog.timestamp,
                lastOutcome = callLog.outcome,
                hasBeenCalledInHotCycle = true,
                callReason = clearRetryMarker(existing.callReason)
            )
        )
    }

    private suspend fun saveRetry(
        context: Context,
        repository: StromrufRepository,
        base: FollowUpEntity,
        retryId: String,
        attempt: Int,
        lastAttemptAt: Long,
        dueAt: Long,
        message: String,
        originalAppointmentAt: Long = originalAppointmentAt(base.note).takeIf { it > 0L } ?: base.dueAt
    ) {
        val row = FollowUpEntity(
            id = retryId,
            contactId = base.contactId,
            contactName = base.contactName,
            contactPhone = base.contactPhone,
            note = noteFor(attempt, lastAttemptAt, message, originalAppointmentAt),
            dueAt = dueAt,
            isCompleted = false,
            callReason = RETRY_REASON
        )
        val saved = repository.insertFollowUp(row)
        FollowUpAlarmScheduler.cancelAlarm(context, retryId)
        FollowUpAlarmScheduler.scheduleAlarm(
            context,
            saved.id,
            saved.contactName,
            saved.contactPhone,
            saved.dueAt
        )
        syncPriority(context, saved, "hoch")
    }

    private suspend fun ensureHotBox(
        context: Context,
        repository: StromrufRepository,
        phone: String,
        fallbackName: String,
        attempt: Int,
        lastOutcome: String?,
        lastCallAt: Long?,
        active: Boolean
    ) {
        val existing = repository.getContactByPhone(phone)
        val listName = existing?.hotBoxListName?.takeIf { it.isNotBlank() } ?: DEFAULT_HOTBOX
        runCatching { SupabaseDbClient.upsertHotBoxList(context, listName) }

        val baseReason = clearRetryMarker(existing?.callReason)
        val retryReason = if (active) {
            buildString {
                append("Smart Retry ").append(attempt).append('/').append(MAX_MISSED_ATTEMPTS)
                if (!baseReason.isNullOrBlank()) append(" · ").append(baseReason)
            }
        } else baseReason

        val updated = if (existing != null) {
            existing.copy(
                lastCallAt = lastCallAt ?: existing.lastCallAt,
                lastOutcome = lastOutcome ?: existing.lastOutcome,
                isHotBox = true,
                // This is the existing Hotbox queue's priority signal: unlike already-called
                // contacts, an active retry stays eligible in the uncalled pool.
                hasBeenCalledInHotCycle = !active,
                hotBoxListName = listName,
                callReason = retryReason
            )
        } else {
            ContactEntity(
                id = UUID.randomUUID().toString(),
                name = fallbackName.ifBlank { phone },
                phone = phone,
                company = null,
                email = null,
                lastCallAt = lastCallAt,
                lastOutcome = lastOutcome,
                isHotBox = true,
                hasBeenCalledInHotCycle = !active,
                callReason = retryReason,
                hotBoxListName = listName
            )
        }
        repository.upsertContactForAutomation(updated)
    }

    private suspend fun syncPriority(context: Context, followUp: FollowUpEntity, priority: String) {
        // Local Room intentionally stays schema-compatible. Supabase already has the priority
        // column, so Smart Retry rows are additionally marked high there without a DB migration.
        runCatching {
            SupabaseDbClient.upsertTableRow(context, "followups", JSONObject().apply {
                put("id", followUp.id)
                put("contact_id", followUp.contactId ?: JSONObject.NULL)
                put("contact_name", followUp.contactName)
                put("contact_phone", followUp.contactPhone)
                put("note", followUp.note ?: JSONObject.NULL)
                put("due_at", followUp.dueAt)
                put("is_completed", followUp.isCompleted)
                put("call_reason", followUp.callReason ?: JSONObject.NULL)
                put("priority", priority)
                put("appointment_type", "wiedervorlage")
            })
        }
    }

    private fun isSmartTracked(followUp: FollowUpEntity): Boolean {
        val reason = followUp.callReason.orEmpty().lowercase(Locale.GERMAN)
        val note = followUp.note.orEmpty().lowercase(Locale.GERMAN)
        return reason.contains("smart call") || reason.contains("smart retry") ||
            note.contains("smart call") || note.contains("smart retry")
    }

    private fun matchesAppointmentWindow(followUp: FollowUpEntity, callAt: Long): Boolean {
        if (followUp.callReason.equals(RETRY_REASON, ignoreCase = true)) return true
        return callAt >= followUp.dueAt - APPOINTMENT_MATCH_EARLY_MS &&
            callAt <= followUp.dueAt + APPOINTMENT_MATCH_LATE_MS
    }

    private fun isMissed(outcome: String): Boolean {
        val clean = outcome.lowercase(Locale.GERMAN).replace('_', ' ').trim()
        return clean.contains("nicht erreicht") || clean.contains("entgangen") || clean.contains("missed")
    }

    private fun isReached(outcome: String): Boolean {
        val clean = outcome.lowercase(Locale.GERMAN).trim()
        return clean.startsWith("erreicht") || clean == "termin" || clean == "answered"
    }

    private fun retryId(phone: String): String = UUID.nameUUIDFromBytes(
        "smart-retry:${canonicalPhone(phone)}".toByteArray(Charsets.UTF_8)
    ).toString()

    private fun canonicalPhone(phone: String): String {
        var digits = phone.filter { it.isDigit() || it == '+' }
        if (digits.startsWith("0049")) digits = "+49" + digits.drop(4)
        if (digits.startsWith("0") && !digits.startsWith("00")) digits = "+49" + digits.drop(1)
        return digits
    }

    private fun samePhone(a: String, b: String): Boolean =
        ContactsUtil.arePhoneNumbersMatching(a, b) || canonicalPhone(a) == canonicalPhone(b)

    private fun attemptFrom(note: String?): Int = Regex("smart-retry-attempt=(\\d+)")
        .find(note.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, MAX_MISSED_ATTEMPTS) ?: 0

    private fun lastAttemptAt(note: String?): Long = Regex("smart-retry-last-at=(\\d+)")
        .find(note.orEmpty())?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

    private fun originalAppointmentAt(note: String?): Long = Regex("smart-retry-original-at=(\\d+)")
        .find(note.orEmpty())?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

    private fun noteFor(
        attempt: Int,
        lastAttemptAt: Long,
        message: String,
        originalAppointmentAt: Long
    ): String = buildString {
        append("[smart-retry-attempt=").append(attempt).append("] ")
        append("[smart-retry-last-at=").append(lastAttemptAt).append("] ")
        append("[smart-retry-original-at=").append(originalAppointmentAt).append("]\n")
        append(message)
    }

    private fun clearRetryMarker(reason: String?): String? {
        val cleaned = reason.orEmpty()
            .replace(Regex("^Smart Retry \\d+/\\d+\\s*·?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun nextDue(from: Long, attempt: Int, originalAppointmentAt: Long): Long {
        // 1. Nichterreichen: +2h. 2. Nichterreichen: n?chster Tag zur urspr?nglich
        // vereinbarten Uhrzeit. Danach wiederholt sich dieses 2-Stufen-Muster.
        if (attempt > 0 && attempt % 2 == 0) {
            return nextDayAtOriginalTime(from, originalAppointmentAt)
        }
        val candidate = from + TWO_HOURS_MS
        val cal = Calendar.getInstance().apply { timeInMillis = candidate }
        if (cal.get(Calendar.HOUR_OF_DAY) >= LATEST_SAME_DAY_HOUR) {
            return nextDayAtOriginalTime(from, originalAppointmentAt)
        }
        return candidate
    }

    private fun nextDayAtOriginalTime(from: Long, originalAppointmentAt: Long): Long {
        val original = Calendar.getInstance().apply { timeInMillis = originalAppointmentAt }
        return Calendar.getInstance().apply {
            timeInMillis = from
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, original.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, original.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun repository(context: Context): StromrufRepository = StromrufRepository(
        context.applicationContext,
        AppDatabase.getDatabase(context.applicationContext).stromrufDao()
    )
}
