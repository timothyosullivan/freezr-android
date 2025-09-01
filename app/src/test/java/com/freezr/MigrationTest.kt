package com.freezr

import android.app.Application
import org.junit.Test
import org.junit.Ignore
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.freezr.data.database.AppDatabase

/** Basic migration smoke test ensuring 1->2 adds new columns and 2->3 adds uuid without data loss. Executes as plain unit test with a stub Application. */
class MigrationTest {
    class StubApp: Application()
    @Ignore("Needs Robolectric context for getCacheDir; will re-enable with proper setup")
    @Test fun migrate1To3_addsColumnsAndUuid() {
        val context = StubApp()
        val dbName = "migration-test.db"

        // Manually create version 1 schema database
        val configV1 = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS containers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT NOT NULL PRIMARY KEY, sortOrder TEXT NOT NULL, showArchived INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO containers (id,name,status,createdAt,updatedAt) VALUES (1,'Test','ACTIVE',0,0)")
                    db.execSQL("INSERT INTO settings (key,sortOrder,showArchived) VALUES ('user','CREATED_DESC',0)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) { }
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configV1).use { it.writableDatabase.close() }

        // Open with Room v3 (should run migrations 1->2->3)
        Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build().apply {
                openHelper.readableDatabase.query("SELECT frozenDate, reminderDays, quantity, notes FROM containers WHERE id=1").use { c ->
                    assert(c.moveToFirst())
                }
                openHelper.readableDatabase.query("SELECT defaultReminderDays FROM settings WHERE key='user'").use { c ->
                    assert(c.moveToFirst())
                }
                openHelper.readableDatabase.query("SELECT uuid FROM containers WHERE id=1").use { c ->
                    assert(c.moveToFirst())
                    val uuid = c.getString(0)
                    assert(!uuid.isNullOrBlank())
                }
                close()
            }
    }
}
