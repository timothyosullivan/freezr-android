package com.freezr.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.freezr.data.model.Container
import com.freezr.data.model.Settings

@Database(entities = [Container::class, Settings::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun containerDao(): ContainerDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        // Migration 1->2: add frozenDate, reminderDays, quantity, notes to containers; add defaultReminderDays to settings; extend Status enum (no DB action needed for enum).
        val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // containers new columns with defaults
                db.execSQL("ALTER TABLE containers ADD COLUMN frozenDate INTEGER NOT NULL DEFAULT (strftime('%s','now')*1000)")
                db.execSQL("ALTER TABLE containers ADD COLUMN reminderDays INTEGER")
                db.execSQL("ALTER TABLE containers ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE containers ADD COLUMN notes TEXT")
                // settings new column
                db.execSQL("ALTER TABLE settings ADD COLUMN defaultReminderDays INTEGER NOT NULL DEFAULT 60")
            }
        }
    // Migration 2->3: add uuid column with generated values and unique index
        val MIGRATION_2_3 = object : Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE containers ADD COLUMN uuid TEXT")
                // Populate existing rows with random UUIDs
                db.query("SELECT id FROM containers WHERE uuid IS NULL").use { cursor ->
                    val ids = mutableListOf<Long>()
                    while (cursor.moveToNext()) ids += cursor.getLong(0)
                    ids.forEach { id ->
                        val uuid = java.util.UUID.randomUUID().toString()
                        db.execSQL("UPDATE containers SET uuid = '" + uuid + "' WHERE id = " + id)
                    }
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_containers_uuid ON containers(uuid)")
                // Make sure future inserts require uuid (Room model sets default, but enforce NOT NULL by recreating table)
                // SQLite cannot alter column to NOT NULL with existing rows simply; skip for now (Room will always supply value).
            }
        }
        // Migration 3->4: add reminderAt and dateUsed columns
        val MIGRATION_3_4 = object : Migration(3,4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE containers ADD COLUMN reminderAt INTEGER")
                db.execSQL("ALTER TABLE containers ADD COLUMN dateUsed INTEGER")
            }
        }
        // Migration 4->5: collapse ARCHIVED status into USED; set dateUsed if null
        val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // For any rows with legacy ARCHIVED status, mark as USED and stamp dateUsed if missing
                db.execSQL("UPDATE containers SET status = 'USED', dateUsed = COALESCE(dateUsed, strftime('%s','now')*1000) WHERE status = 'ARCHIVED'")
            }
        }
    }
}
