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
    private const val NEXT_DAY_HOUR = 10
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

            val phone = fired.contactPhone
            val targetId = retryId(phone)
            val existingRetry = repository.getFollowUpById(targetId)?.takeUnless { it.isCompleted }
            val attempt = existingRetry?.let { attemptFrom(it.note) }
                ?: attemptFrom(fired.note)

            ensureHotBox(
                context = context,
                repository = repository,
                phone = phone,
                fallbackName = fired.contactName,
                attempt = attempt,
                lastOutcome = null,
                lastCallAt = null,
                active = attempt < MAX_MISSED_ATTEMPTS
            )

            if (attempt >= MAX_MISSED_ATTEMPTS) {
                if (!fired.isCompleted) repository.updateFollowUpStatus(fired.id, true)
                if (existingRetry != null && existingRetry.id != fired.id) {
                    repository.updateFollowUpStatus(existingRetry.id, true)
                }
                FollowUpAlarmScheduler.cancelAlarm(context, fired.id)
                FollowUpAlarmScheduler.cancelAlarm(context, targetId)
                return@withLock
            }

            // If this is the original Gemma appointment, close it and switch to the single,
            // deterministic retry id. If it already is the retry id we simply move that row.
            if (fired.id != targetId) {
                repository.updateFollowUpStatus(fired.id, true)
                FollowUpAlarmScheduler.cancelAlarm(context, fired.id)
            }

            val base = existingRetry ?: fired
            val due = nextDue(
                from = maxOf(System.currentTimeMillis(), firedDueAt),
                attempt = attempt,
                afterActualMiss = false
            )
            saveRetry(
                context = context,
                repository = repository,
                base = base,
                retryId = targetId,
                attempt = attempt,
                lastAttemptAt = lastAttemptAt(existingRetry?.note ?: fired.note),
                dueAt = due,
                message = if (attempt == 0)
                    "Vereinbarter Rückruf noch offen – automatisch weiter im Blick"
                else
                    "Nicht erreicht – automatischer Nachfassversuch ${attempt + 1}/$MAX_MISSED_ATTEMPTS"
            )
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
                    message = "8-mal hintereinander nicht erreicht – automatische Nachverfolgung beendet"
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
        val due = nextDue(callLog.timestamp, attempt, afterActualMiss = true)
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
        message: String
    ) {
        val row = FollowUpEntity(
            id = retryId,
            contactId = base.contactId,
            contactName = base.contactName,
            contactPhone = base.contactPhone,
            note = noteFor(attempt, lastAttemptAt, message),
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

    private fun noteFor(attempt: Int, lastAttemptAt: Long, message: String): String = buildString {
        append("[smart-retry-attempt=").append(attempt).append("] ")
        append("[smart-retry-last-at=").append(lastAttemptAt).append("]\n")
        append(message)
    }

    private fun clearRetryMarker(reason: String?): String? {
        val cleaned = reason.orEmpty()
            .replace(Regex("^Smart Retry \\d+/\\d+\\s*·?\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun nextDue(from: Long, attempt: Int, afterActualMiss: Boolean): Long {
        if (afterActualMiss && attempt > 0 && attempt % 3 == 0) {
            return nextDayAtTen(from)
        }
        val candidate = from + TWO_HOURS_MS
        val cal = Calendar.getInstance().apply { timeInMillis = candidate }
        if (cal.get(Calendar.HOUR_OF_DAY) >= LATEST_SAME_DAY_HOUR) {
            return nextDayAtTen(from)
        }
        return candidate
    }

    private fun nextDayAtTen(from: Long): Long = Calendar.getInstance().apply {
        timeInMillis = from
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, NEXT_DAY_HOUR)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun repository(context: Context): StromrufRepository = StromrufRepository(
        context.applicationContext,
        AppDatabase.getDatabase(context.applicationContext).stromrufDao()
    )
}
