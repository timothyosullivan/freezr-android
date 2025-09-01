package com.freezr.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.freezr.data.model.Container
import com.freezr.data.model.Settings

@Database(entities = [Container::class, Settings::class], version = 2, exportSchema = false)
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
    }
}
