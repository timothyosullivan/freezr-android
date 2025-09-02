package com.freezr.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freezr.data.database.AppDatabase
import com.freezr.data.model.Status
import androidx.work.WorkManager

/** Handles notification action buttons: mark used & snooze. */
class ReminderActionReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id <= 0) return
        val db = AppDbProvider.db(context)
        when (intent.action) {
            ACTION_MARK_USED -> {
                val now = System.currentTimeMillis()
                db.openHelper.writableDatabase.execSQL("UPDATE containers SET status='USED', dateUsed=$now, reminderAt=NULL WHERE id=$id")
                WorkManager.getInstance(context).cancelUniqueWork("reminder-container-$id")
            }
            ACTION_SNOOZE -> {
                // Only snooze ACTIVE items
                val cursor = db.openHelper.readableDatabase.query("SELECT status FROM containers WHERE id=$id")
                val statusIdx = 0
                val isActive = cursor.use { c -> c.moveToFirst() && c.getString(statusIdx) == "ACTIVE" }
                if (isActive) {
                    val newAt = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
                    db.openHelper.writableDatabase.execSQL("UPDATE containers SET reminderAt=$newAt WHERE id=$id")
                    WorkManagerReminderScheduler(WorkManager.getInstance(context)).schedule(id, newAt)
                }
            }
        }
    }
    companion object {
        const val ACTION_MARK_USED = "com.freezr.action.MARK_USED"
        const val ACTION_SNOOZE = "com.freezr.action.SNOOZE_7D"
        const val EXTRA_ID = "id"
    }
}

/** Simple singleton provider for DB access in receivers/workers outside Hilt graph. */
object AppDbProvider {
    @Volatile private var instance: AppDatabase? = null
    fun db(context: Context): AppDatabase = instance ?: synchronized(this) {
        instance ?: androidx.room.Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db").build().also { instance = it }
    }
}
