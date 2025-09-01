package com.freezr

import com.freezr.data.database.SettingsDao
import com.freezr.data.model.Settings
import com.freezr.data.database.AppDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: SettingsDao
    private lateinit var repo: com.freezr.data.repository.SettingsRepository

    @Before
    fun setup() {
    val context = RuntimeEnvironment.getApplication()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.settingsDao()
        repo = com.freezr.data.repository.SettingsRepository(dao)
    }

    @Test
    fun default_and_update() = runBlocking {
        val initial = repo.settings.first()
        assertEquals(Settings(), initial)
        repo.updateShowArchived(true, initial)
        val updated = repo.settings.first()
        assertEquals(true, updated.showArchived)
    }
}
