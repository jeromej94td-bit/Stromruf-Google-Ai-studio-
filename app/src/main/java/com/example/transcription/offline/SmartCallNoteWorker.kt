package com.example.transcription.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.database.AppDatabase
import com.example.database.FollowUpEntity
import com.example.receiver.FollowUpAlarmScheduler
import com.example.repository.StromrufRepository
import com.example.smartretry.SmartRetryManager
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
        LocalTranscripts.enrich(context, name, job)
        if (job.optString("syncState") == "done" && job.optString("followUpState") in setOf("done", "none") &&
            (job.optString("followUpState") == "none" || job.optBoolean("followUpSynced"))) {
            return@withContext Result.success()
        }

        try {
            val wavDurationSeconds = (job.optLong("durationMs") / 1000L).coerceAtLeast(0L)
            val callDurationSeconds = job.optLong("callDurationSeconds").coerceAtLeast(0L)
            val durationSeconds = maxOf(wavDurationSeconds, callDurationSeconds)
            val transcript = job.optString("text").trim()
            val nextAction = job.optString("nextAction").trim()
            val baseSummary = job.optString("summary").ifBlank { GermanCallSummary.create(transcript) }
            val identity = com.example.recording.SmartRecordingMetadata.context(job)
            val summary = if (baseSummary.startsWith("Verifizierte Zuordnung:")) baseSummary else "$identity\n$baseSummary"
            val phone = com.example.recording.SmartRecordingMetadata.phone(job, name)
            val db = AppDatabase.getDatabase(context)
            val repository = StromrufRepository(context, db.stromrufDao())
            val followUpId = UUID.nameUUIDFromBytes("smart-call-followup:$name".toByteArray()).toString()

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
                val referenceTime = job.optLong("callStartedAt").takeIf { it > 0L }
                    ?: LocalTranscripts.recording(context, name).lastModified()
                val plan = GermanFollowUpPlanner.plan(planningText, referenceTime)
                if (plan != null) {
                    val contact = repository.getContactByPhone(phone)
                    val dynamicMarkers = buildString {
                        append("[smart-retry-original-at=").append(plan.originalAppointmentAt).append("]")
                        plan.windowEndAt?.let { append(" [smart-callback-window-end=").append(it).append("]") }
                    }
                    val inserted = repository.getFollowUpById(followUpId) ?: repository.insertFollowUp(
                        FollowUpEntity(
                            id = followUpId,
                            contactId = contact?.id ?: job.optString("contactId").takeIf { it.isNotBlank() },
                            contactName = contact?.name ?: job.optString("contactName").ifBlank { phone },
                            contactPhone = phone,
                            note = "$dynamicMarkers\nSmart Call: $summary\n${plan.description}",
                            dueAt = plan.dueAt,
                            callReason = "Smart Call"
                        ), preserveExactTime = true, syncImmediately = false
                    )
                    SmartRetryManager.registerAppointment(context, repository, inserted)
                    FollowUpAlarmScheduler.scheduleAlarm(
                        context,
                        inserted.id,
                        inserted.contactName,
                        inserted.contactPhone,
                        inserted.dueAt
                    )
                    job.put("followUpState", "done")
                        .put("followUpDueAt", inserted.dueAt)
                        .put("followUpMessage", if (plan.windowEndAt != null) "R?ckruf-Zeitfenster automatisch in dynamische Hotbox eingeplant" else "Termin automatisch in dynamische Hotbox eingeplant")
                } else {
                    job.put("followUpState", "none")
                        .put("followUpMessage", "Kein eindeutiger Rückruftermin erkannt")
                }
                LocalTranscripts.write(context, name, job)
            }

            if (job.optString("followUpState") == "done" && !job.optBoolean("followUpSynced")) {
                job.put("followUpSynced", repository.syncFollowUp(followUpId))
                LocalTranscripts.write(context, name, job)
            }

            val file = LocalTranscripts.recording(context, name)
            val saved = SmartCallSupabaseSync.saveSummary(
                context = context,
                phone = phone,
                contactId = job.optString("contactId").takeIf { it.isNotBlank() },
                contactName = job.optString("contactName").takeIf { it.isNotBlank() },
                callStartedAt = job.optLong("callStartedAt").takeIf { it > 0L } ?: file.lastModified(),
                durationSeconds = durationSeconds,
                summary = summary,
                sourceFileName = name
            )

            if (saved) {
                job.put("syncState", "done")
                    .put("syncMessage", "Als Smart-Call-Notiz in Supabase gespeichert")
                LocalTranscripts.write(context, name, job)
                if (job.optString("followUpState") == "done" && !job.optBoolean("followUpSynced")) Result.retry()
                else Result.success()
            } else {
                job.put("syncState", "pending")
                    .put("syncMessage", "Wartet auf Anmeldung oder Internet")
                LocalTranscripts.write(context, name, job)
                Result.retry()
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            job.put("syncState", "pending")
                .put("syncMessage", "Automatische Verarbeitung wird erneut versucht")
            LocalTranscripts.write(context, name, job)
            Result.retry()
        }
    }
}
