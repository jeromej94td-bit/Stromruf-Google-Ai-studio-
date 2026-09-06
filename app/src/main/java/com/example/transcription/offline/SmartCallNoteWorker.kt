package com.example.transcription.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.database.AppDatabase
import com.example.database.FollowUpEntity
import com.example.receiver.FollowUpAlarmScheduler
import com.example.repository.StromrufRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Completes every Smart Call automatically:
 * summary -> follow-up planning -> Supabase summary sync.
 * WAV and complete transcript stay local.
 */
class SmartCallNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val name = inputData.getString("file") ?: return@withContext Result.success()
        val job = LocalTranscripts.read(context, name)
        if (job.optString("state") != "done") return@withContext Result.success()
        if (job.optString("syncState") == "done" && job.optString("followUpState") in setOf("done", "none")) {
            return@withContext Result.success()
        }

        try {
            val wavDurationSeconds = (job.optLong("durationMs") / 1000L).coerceAtLeast(0L)
            val callDurationSeconds = job.optLong("callDurationSeconds").coerceAtLeast(0L)
            val durationSeconds = maxOf(wavDurationSeconds, callDurationSeconds)
            val transcript = job.optString("text").trim()
            val nextAction = job.optString("nextAction").trim()
            val summary = job.optString("summary").ifBlank { GermanCallSummary.create(transcript) }
            val phone = name.removePrefix("Call_").removeSuffix(".wav")
                .substringBeforeLast("_").trim().ifBlank { "Unbekannt" }

            job.put("summary", summary)
                .put("durationSeconds", durationSeconds)
                .put("syncState", "running")
                .put("syncMessage", "Notiz und Termin werden automatisch verarbeitet …")
            LocalTranscripts.write(context, name, job)

            // Plan a follow-up independently of call duration or cloud sync success.
            if (job.optString("followUpState") !in setOf("done", "none")) {
                val planningText = buildString {
                    append(transcript)
                    if (nextAction.isNotBlank()) append("\nNächster Schritt: ").append(nextAction)
                    if (summary.isNotBlank()) append("\nZusammenfassung: ").append(summary)
                }
                val plan = GermanFollowUpPlanner.plan(planningText)
                if (plan != null) {
                    val db = AppDatabase.getDatabase(context)
                    val repository = StromrufRepository(context, db.stromrufDao())
                    val contact = repository.getContactByPhone(phone)
                    val followUpId = UUID.nameUUIDFromBytes("smart-call-followup:$name".toByteArray()).toString()
                    val inserted = repository.insertFollowUp(
                        FollowUpEntity(
                            id = followUpId,
                            contactId = contact?.id,
                            contactName = contact?.name ?: phone,
                            contactPhone = phone,
                            note = "Smart Call: $summary\n${plan.description}",
                            dueAt = plan.dueAt,
                            callReason = "Smart Call"
                        )
                    )
                    FollowUpAlarmScheduler.scheduleAlarm(
                        context,
                        inserted.id,
                        inserted.contactName,
                        inserted.contactPhone,
                        inserted.dueAt
                    )
                    job.put("followUpState", "done")
                        .put("followUpDueAt", inserted.dueAt)
                        .put("followUpMessage", "Termin automatisch angelegt")
                } else {
                    job.put("followUpState", "none")
                        .put("followUpMessage", "Kein eindeutiger Rückruftermin erkannt")
                }
                LocalTranscripts.write(context, name, job)
            }

            val file = LocalTranscripts.recording(context, name)
            val saved = SmartCallSupabaseSync.saveSummary(
                context = context,
                phone = phone,
                contactId = null,
                contactName = null,
                callStartedAt = job.optLong("callStartedAt").takeIf { it > 0L } ?: file.lastModified(),
                durationSeconds = durationSeconds,
                summary = summary,
                sourceFileName = name
            )

            if (saved) {
                job.put("syncState", "done")
                    .put("syncMessage", "Als Smart-Call-Notiz in Supabase gespeichert")
                LocalTranscripts.write(context, name, job)
                Result.success()
            } else {
                job.put("syncState", "pending")
                    .put("syncMessage", "Wartet auf Anmeldung oder Internet")
                LocalTranscripts.write(context, name, job)
                Result.retry()
            }
        } catch (e: Exception) {
            job.put("syncState", "pending")
                .put("syncMessage", "Automatische Verarbeitung wird erneut versucht")
            LocalTranscripts.write(context, name, job)
            Result.retry()
        }
    }
}
