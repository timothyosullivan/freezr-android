package com.freezr.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.freezr.data.model.Container
import com.freezr.data.model.Settings

@Database(entities = [Container::class, Settings::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun containerDao(): ContainerDao
    abstract fun settingsDao(): SettingsDao
}
