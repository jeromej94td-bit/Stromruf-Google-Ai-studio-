package com.example.leads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.MainActivity
import com.example.database.AppDatabase
import com.example.database.NeukundeEntity
import com.example.util.SupabaseDbClient
import java.util.concurrent.TimeUnit

object LeadAutomation {
    private const val CHANNEL = "lead_tasks"
    private const val TAG_PREFIX = "lead-reminder-"

    fun schedule(context: Context, lead: NeukundeEntity) {
        val manager = WorkManager.getInstance(context)
        val workName = "$TAG_PREFIX${lead.id}"
        manager.cancelUniqueWork(workName)
        val due = lead.nextActionAt ?: return
        if (lead.status !in LeadWorkflow.active || lead.completedAt != null || lead.archivedAt != null) return
        val request = OneTimeWorkRequestBuilder<LeadReminderWorker>()
            .setInputData(workDataOf("leadId" to lead.id))
            .setInitialDelay((due - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(TAG_PREFIX + lead.id)
            .build()
        manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_PREFIX + id)
    }

    fun enqueueCloudSync(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<LeadCloudSyncWorker>()
            .setInputData(workDataOf("leadId" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "lead-cloud-$id", ExistingWorkPolicy.REPLACE, request
        )
    }

    internal fun notifyDue(context: Context, leads: List<NeukundeEntity>) {
        if (leads.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL, "Offene Lead-Aufgaben", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Erinnerungen an offene Neukunden und Angebote" })
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 1550, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val names = leads.take(3).joinToString(" · ") {
            it.company ?: it.customerName ?: it.customerNumber.ifBlank { it.email ?: "Lead" }
        }
        val title = when {
            leads.any { it.status == LeadWorkflow.FOLLOW_UP || it.status == LeadWorkflow.OFFER_SENT } ->
                "${leads.size} Kunden zum Stand fragen"
            leads.any { it.status == LeadWorkflow.CALL } -> "${leads.size} offene Anrufe"
            else -> "${leads.size} Angebote heute bearbeiten"
        }
        manager.notify(1550, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build())
    }
}

class LeadReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).stromrufDao()
        val id = inputData.getString("leadId") ?: return Result.success()
        var trigger = dao.getNeukundeById(id) ?: return Result.success()
        val now = System.currentTimeMillis()
        if (trigger.status !in LeadWorkflow.active || trigger.nextActionAt == null || trigger.nextActionAt > now + 60_000L) return Result.success()
        if (trigger.status == LeadWorkflow.OFFER_SENT) {
            trigger = trigger.copy(status = LeadWorkflow.FOLLOW_UP, updatedAt = now)
            dao.insertNeukunde(trigger)
            LeadAutomation.enqueueCloudSync(applicationContext, trigger.id)
        }
        val due = dao.getAllNeukundenList().filter {
            it.status in LeadWorkflow.active && it.completedAt == null && it.archivedAt == null &&
                it.nextActionAt?.let { time -> time <= now + 60_000L } == true
        }
        LeadAutomation.notifyDue(applicationContext, due)
        return Result.success()
    }
}

class LeadCloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("leadId") ?: return Result.success()
        val dao = AppDatabase.getDatabase(applicationContext).stromrufDao()
        val lead = dao.getNeukundeById(id) ?: return Result.success()
        val contact = dao.getContactById(id)
        val leadOk = SupabaseDbClient.upsertNeukunde(applicationContext, lead)
        val contactOk = contact == null || SupabaseDbClient.upsertContact(applicationContext, contact)
        return if (leadOk && contactOk) Result.success() else Result.retry()
    }
}
