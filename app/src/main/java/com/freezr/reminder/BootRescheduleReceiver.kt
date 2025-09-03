package com.freezr.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.freezr.data.model.Status
import androidx.work.WorkManager

/** Reschedules outstanding reminders after device reboot. */
class BootRescheduleReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val db = AppDbProvider.db(context)
        // naive query via rawQuery for ACTIVE items with future reminderAt
        db.openHelper.readableDatabase.query("SELECT id, reminderAt FROM containers WHERE status='ACTIVE' AND reminderAt IS NOT NULL AND reminderAt > strftime('%s','now')*1000").use { c ->
            val scheduler = WorkManagerReminderScheduler(context.applicationContext, WorkManager.getInstance(context))
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val at = c.getLong(1)
                scheduler.schedule(id, at)
            }
        }
    }
}
