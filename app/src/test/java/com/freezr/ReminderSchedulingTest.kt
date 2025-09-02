package com.freezr

import com.freezr.data.database.ContainerDao
import com.freezr.data.database.SettingsDao
import com.freezr.data.model.*
import com.freezr.data.repository.ContainerRepository
import com.freezr.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderSchedulingTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class FakeContainerDao: ContainerDao {
        private var nextId = 1L
        private val backing = MutableStateFlow<List<Container>>(emptyList())
        override fun observe(includeArchived: Boolean, order: String): Flow<List<Container>> = backing
    override suspend fun getById(id: Long): Container? = backing.value.firstOrNull { it.id == id }
    override suspend fun getByUuid(uuid: String): Container? = backing.value.firstOrNull { it.uuid == uuid }
        override suspend fun insert(container: Container): Long { val c = container.copy(id = nextId++); backing.value = backing.value + c; return c.id }
    override suspend fun insertAll(containers: List<Container>): List<Long> = containers.map { insert(it) }
        override suspend fun update(container: Container) {}
    override suspend fun updateAll(containers: List<Container>) { /* no-op for tests */ }
        override suspend fun updateStatus(id: Long, status: Status) {}
    }
    private class FakeSettingsDao: SettingsDao {
        private val backing = MutableStateFlow<Settings?>(Settings())
        override fun observe() = backing
        override suspend fun upsert(settings: Settings) { backing.value = settings }
    }

    private class CapturingScheduler: com.freezr.reminder.ReminderScheduler {
        val scheduled = mutableListOf<Pair<Long,Long>>()
        val canceled = mutableListOf<Long>()
        override fun schedule(containerId: Long, triggerAtMillis: Long) { scheduled += containerId to triggerAtMillis }
        override fun cancel(containerId: Long) { canceled += containerId }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun schedulesReminderUsingDefaultDays() = runTest {
        val dao = FakeContainerDao(); val sdao = FakeSettingsDao(); val sched = CapturingScheduler()
        val vm = ContainerViewModel(ContainerRepository(dao), SettingsRepository(sdao), sched)
        vm.add("ItemX")
        advanceUntilIdle()
        assertTrue("Expected one scheduled reminder", sched.scheduled.size == 1)
        val (id, trigger) = sched.scheduled.first()
        assertTrue(id == 1L)
        assertTrue("Trigger should be in the future", trigger > System.currentTimeMillis())
    }
}
