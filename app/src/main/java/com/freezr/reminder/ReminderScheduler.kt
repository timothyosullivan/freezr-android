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
import android.app.AlarmManager
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

/** Simple abstraction for scheduling a one-time reminder for a container. */
interface ReminderScheduler { fun schedule(containerId: Long, triggerAtMillis: Long); fun cancel(containerId: Long) }

class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val appCtx: Context,
    private val workManager: WorkManager
): ReminderScheduler {
    override fun schedule(containerId: Long, triggerAtMillis: Long) {
        // Keep legacy WorkManager scheduling as a safety net
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to containerId))
            .addTag("reminder-container-$containerId")
            .build()
        workManager.enqueueUniqueWork("reminder-container-$containerId", ExistingWorkPolicy.REPLACE, req)

        // Also schedule an exact alarm to wake app if killed
    val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(appCtx, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderWorker.KEY_ID, containerId)
        }
        val flags = FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE else 0)
    val pi = PendingIntent.getBroadcast(appCtx, containerId.toInt(), intent, flags)
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }
    override fun cancel(containerId: Long) {
    workManager.cancelUniqueWork("reminder-container-$containerId")
    val am = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(appCtx, ReminderAlarmReceiver::class.java)
        val flags = FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE else 0)
    val pi = PendingIntent.getBroadcast(appCtx, containerId.toInt(), intent, flags)
        am.cancel(pi)
    }
}

class ReminderAlarmReceiver: android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderWorker.KEY_ID, -1)
        if (id <= 0) return
        // Directly deliver notification to avoid WorkManager doze delays when device is idle.
        deliverReminderNotification(context.applicationContext, id)
    }
}

private fun deliverReminderNotification(ctx: Context, id: Long) {
    // Build / reuse DB (lightweight access) – using Room directly (could swap to provider if added)
    val db = Room.databaseBuilder(ctx, AppDatabase::class.java, "app.db").fallbackToDestructiveMigration().build()
    val container = try { kotlinx.coroutines.runBlocking { db.containerDao().getById(id) } } catch (_: Exception) { null }
    // Channel
    if (Build.VERSION.SDK_INT >= 26) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Freezr Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    val markUsedIntent = Intent(ctx, ReminderActionReceiver::class.java).apply {
        action = ReminderActionReceiver.ACTION_MARK_USED
        putExtra(ReminderActionReceiver.EXTRA_ID, id)
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    val markUsedPending = PendingIntent.getBroadcast(ctx, (id * 2).toInt(), markUsedIntent, flags)
    val openIntent = Intent(ctx, com.freezr.MainActivity::class.java).apply {
        action = "com.freezr.ACTION_OPEN_CONTAINER"
        putExtra("containerId", id)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val openPending = PendingIntent.getActivity(
        ctx,
        (id * 3).toInt(),
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    )
    val itemName = container?.name?.takeIf { it.isNotBlank() } ?: "Item #$id"
    val body = "$itemName – check if it needs action"
    val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("Freezr Reminder")
        .setContentText(body)
        .setContentIntent(openPending)
        .addAction(0, "Used", markUsedPending)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()
    NotificationManagerCompat.from(ctx).notify((id % Int.MAX_VALUE).toInt(), notif)
}

class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
    val id = inputData.getLong(KEY_ID, -1)
    if (id > 0) deliverReminderNotification(applicationContext, id)
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
