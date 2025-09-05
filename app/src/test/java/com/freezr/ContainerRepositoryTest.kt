package com.freezr

import com.freezr.data.database.ContainerDao
import com.freezr.data.model.Status
import com.freezr.data.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Test
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContainerRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ContainerDao
    private lateinit var repo: com.freezr.data.repository.ContainerRepository

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.containerDao()
        repo = com.freezr.data.repository.ContainerRepository(dao)
    }

    @After
    fun teardown() { if (::db.isInitialized) db.close() }

    @Test
    fun add_and_mark_used_flow() = runBlocking {
        val id = repo.add("Alpha")
        val initial = repo.observe(false, com.freezr.data.model.SortOrder.CREATED_DESC).first()
        assertEquals(1, initial.size)
        assertEquals("Alpha", initial.first().name)
        repo.markUsed(id)
        val after = repo.observe(true, com.freezr.data.model.SortOrder.CREATED_DESC).first()
        assertEquals(Status.USED, after.first().status)
    }
}
