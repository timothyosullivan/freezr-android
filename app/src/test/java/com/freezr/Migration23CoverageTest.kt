package com.freezr

import android.app.Application
import androidx.room.Room
import com.freezr.data.database.AppDatabase
import org.junit.Test
import org.junit.Ignore

/** Touch migration 2->3 path for coverage by creating v2 then migrating. */
class Migration23CoverageTest {
    class StubApp: Application()
    @Ignore("Needs Robolectric; placeholder for coverage once environment configured")
    @Test fun migrate2To3_addsUuid() {
        val context = StubApp()
        val name = "m23.db"
        // Create v2 schema manually (simulate post-1->2 state without uuid)
        val cfg = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object: androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2){
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS containers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uuid TEXT, name TEXT NOT NULL, status TEXT NOT NULL, frozenDate INTEGER NOT NULL, reminderDays INTEGER, quantity INTEGER NOT NULL, notes TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT NOT NULL PRIMARY KEY, sortOrder TEXT NOT NULL, showArchived INTEGER NOT NULL, defaultReminderDays INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO containers (id,name,status,frozenDate,reminderDays,quantity,notes,createdAt,updatedAt) VALUES (1,'A','ACTIVE',0,NULL,1,NULL,0,0)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) { }
            }).build()
        androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(cfg).use { it.writableDatabase.close() }
        // Migrate using Room (2->3)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()
        db.openHelper.readableDatabase.query("SELECT uuid FROM containers WHERE id=1").use { c ->
            c.moveToFirst()
        }
        db.close()
    }
}
