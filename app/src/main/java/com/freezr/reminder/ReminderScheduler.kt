package com.freezr.reminder

import android.content.Context
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Simple abstraction for scheduling a one-time reminder for a container. */
interface ReminderScheduler {
    fun schedule(containerId: Long, triggerAtMillis: Long)
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
}

class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // TODO: Hook up notification in future iteration.
        return Result.success()
    }
    companion object { const val KEY_ID = "containerId" }
}
