package com.freezr.reminder

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.room.Room
import com.freezr.data.database.AppDatabase
import android.app.TaskStackBuilder
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Simple abstraction for scheduling a one-time reminder for a container. */
interface ReminderScheduler {
    fun schedule(containerId: Long, triggerAtMillis: Long)
    fun cancel(containerId: Long)
}

class WorkManagerReminderScheduler(private val workManager: WorkManager): ReminderScheduler {
    override fun schedule(containerId: Long, triggerAtMillis: Long) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to containerId))
            .addTag("reminder-container-$containerId")
            .build()
        workManager.enqueueUniqueWork(
            "reminder-container-$containerId",
            ExistingWorkPolicy.REPLACE,
            req
        )
    }
    override fun cancel(containerId: Long) {
        workManager.cancelUniqueWork("reminder-container-$containerId")
    }
}

class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        ensureChannel()
        val markUsedIntent = Intent(applicationContext, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_MARK_USED
            putExtra(ReminderActionReceiver.EXTRA_ID, id)
        }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    val markUsedPending = PendingIntent.getBroadcast(applicationContext, (id * 2).toInt(), markUsedIntent, flags)
        // Fetch container name (fallback to generic)
        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "app.db").fallbackToDestructiveMigration().build()
        val container = try { db.containerDao().getById(id) } catch (_: Exception) { null }
        val openIntent = Intent(applicationContext, com.freezr.MainActivity::class.java).apply {
            action = "com.freezr.ACTION_OPEN_CONTAINER"
            putExtra("containerId", id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            applicationContext,
            (id * 3).toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val itemName = container?.name?.takeIf { it.isNotBlank() } ?: "Item #$id"
    val body = "$itemName – check if it needs action"
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Freezr Reminder")
            .setContentText(body)
            .setContentIntent(openPending)
            .addAction(0, "Used", markUsedPending)
            // Snooze action removed
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify((id % Int.MAX_VALUE).toInt(), notif)
        return Result.success()
    }
    companion object { const val KEY_ID = "containerId" }
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Freezr Reminders", NotificationManager.IMPORTANCE_DEFAULT))
            }
        }
    }
}

private const val CHANNEL_ID = "freezr_reminders"
