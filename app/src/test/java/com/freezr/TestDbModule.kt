package com.freezr

import android.content.Context
import androidx.room.Room
import com.freezr.data.database.AppDatabase
import com.freezr.data.database.ContainerDao
import com.freezr.data.database.SettingsDao
import com.freezr.data.repository.ContainerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Test override DB module providing in-memory Room to avoid persistent leaked DB handles in Robolectric. */
@Module
@InstallIn(SingletonComponent::class)
object TestDbModule {
    @Provides @Singleton
    fun provideInMemoryDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    fun provideContainerDao(db: AppDatabase): ContainerDao = db.containerDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides @Singleton
    fun provideContainerRepository(dao: ContainerDao): ContainerRepository = ContainerRepository(dao)

}
