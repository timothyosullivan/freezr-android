package com.freezr.di

import android.app.Application
import androidx.room.Room
import com.freezr.data.database.AppDatabase
import com.freezr.data.repository.ContainerRepository
import com.freezr.data.repository.SettingsRepository
import com.freezr.reminder.ReminderScheduler
import com.freezr.reminder.WorkManagerReminderScheduler
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDb(app: Application): AppDatabase = Room.databaseBuilder(
    app, AppDatabase::class.java, "app.db"
    )
    .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    @Provides fun containerDao(db: AppDatabase) = db.containerDao()
    @Provides fun settingsDao(db: AppDatabase) = db.settingsDao()

    @Provides @Singleton
    fun containerRepo(dao: com.freezr.data.database.ContainerDao) = ContainerRepository(dao)

    @Provides @Singleton
    fun settingsRepo(dao: com.freezr.data.database.SettingsDao) = SettingsRepository(dao)

    @Provides @Singleton
    fun reminderScheduler(app: Application): ReminderScheduler =
        WorkManagerReminderScheduler(WorkManager.getInstance(app))
}
