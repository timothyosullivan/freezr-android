package com.freezr.di

import android.app.Application
import androidx.room.Room
import com.freezr.data.database.AppDatabase
import com.freezr.data.repository.ContainerRepository
import com.freezr.data.repository.SettingsRepository
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
        app, AppDatabase::class.java, "freezr.db"
    )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    @Provides fun containerDao(db: AppDatabase) = db.containerDao()
    @Provides fun settingsDao(db: AppDatabase) = db.settingsDao()

    @Provides @Singleton
    fun containerRepo(dao: com.freezr.data.database.ContainerDao) = ContainerRepository(dao)

    @Provides @Singleton
    fun settingsRepo(dao: com.freezr.data.database.SettingsDao) = SettingsRepository(dao)
}
