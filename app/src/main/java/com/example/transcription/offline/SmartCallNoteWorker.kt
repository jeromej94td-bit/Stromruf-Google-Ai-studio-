package com.example.transcription.offline

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.agent.AgentBackend
import com.example.database.AppDatabase
import com.example.database.FollowUpEntity
import com.example.receiver.FollowUpAlarmScheduler
import com.example.repository.StromrufRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sends the local summary, never the WAV file or complete transcript. */
class SmartCallNoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val name = inputData.getString("file") ?: return@withContext Result.success()
        val job = LocalTranscripts.read(context, name)
        if (job.optString("state") != "done") return@withContext Result.success()
        if (job.optString("syncState") == "done") return@withContext Result.success()
        try {
            val durationSeconds = (job.optLong("durationMs") / 1000L).coerceAtLeast(0L)
            if (durationSeconds <= 60L) return@withContext Result.success()
            val transcript = job.optString("text").trim()
            val summary = job.optString("summary").ifBlank { GermanCallSummary.create(transcript) }
            job.put("summary", summary).put("syncState", "running")
                .put("syncMessage", "Zusammenfassung wird in Stromruf gespeichert …")
            LocalTranscripts.write(context, name, job)

            val phone = name.removePrefix("Call_").removeSuffix(".wav")
                .substringBeforeLast("_").trim().ifBlank { "Unbekannt" }
            val file = LocalTranscripts.recording(context, name)
            val saved = AgentBackend.saveSmartCallNote(
                context = context,
                phone = phone,
                contactId = null,
                contactName = null,
                callStartedAt = file.lastModified(),
                durationSeconds = durationSeconds,
                summary = summary,
                sourceFileName = name
            )
            if (saved) {
                val plan = GermanFollowUpPlanner.plan(transcript)
                if (plan != null) {
                    val db = AppDatabase.getDatabase(context)
                    val repository = StromrufRepository(context, db.stromrufDao())
                    val contact = repository.getContactByPhone(phone)
                    val followUpId = UUID.nameUUIDFromBytes("smart-call-followup:$name".toByteArray()).toString()
                    val inserted = repository.insertFollowUp(FollowUpEntity(
                        id = followUpId,
                        contactId = contact?.id,
                        contactName = contact?.name ?: phone,
                        contactPhone = phone,
                        note = "Smart Call: $summary\n${plan.description}",
                        dueAt = plan.dueAt,
                        callReason = "Smart Call"
                    ))
                    FollowUpAlarmScheduler.scheduleAlarm(context, inserted.id, inserted.contactName, inserted.contactPhone, inserted.dueAt)
                    job.put("followUpState", "done").put("followUpMessage", "Termin automatisch angelegt")
                } else {
                    job.put("followUpState", "none").put("followUpMessage", "Kein eindeutiger Rückruftermin erkannt")
                }
                job.put("syncState", "done").put("syncMessage", "Als Smart-Call-Notiz gespeichert")
                LocalTranscripts.write(context, name, job)
                Result.success()
            } else {
                job.put("syncState", "pending").put("syncMessage", "Wartet auf Anmeldung oder Internet")
                LocalTranscripts.write(context, name, job)
                Result.retry()
            }
        } catch (e: Exception) {
            job.put("syncState", "pending").put("syncMessage", "Speicherung wird erneut versucht")
            LocalTranscripts.write(context, name, job)
            Result.retry()
        }
    }
}
