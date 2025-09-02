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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContainerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeContainerDao : ContainerDao {
        private var nextId = 1L
        private val backing = MutableStateFlow<List<Container>>(emptyList())
        override fun observe(includeArchived: Boolean, order: String): Flow<List<Container>> = backing
    override suspend fun getById(id: Long): Container? = backing.value.firstOrNull { it.id == id }
    override suspend fun getByUuid(uuid: String): Container? = backing.value.firstOrNull { it.uuid == uuid }
        override suspend fun insert(container: Container): Long {
            val assigned = container.copy(id = nextId++)
            backing.value = backing.value + assigned
            return assigned.id
        }
        override suspend fun insertAll(containers: List<Container>): List<Long> {
            return containers.map { insert(it) }
        }
        override suspend fun update(container: Container) { backing.value = backing.value.map { if (it.id == container.id) container else it } }
        override suspend fun updateAll(containers: List<Container>) { containers.forEach { update(it) } }
        override suspend fun updateStatus(id: Long, status: Status) { backing.value = backing.value.map { if (it.id == id) it.copy(status = status) else it } }
        fun seed(vararg containers: Container) { backing.value = containers.toList() }
        fun current(): List<Container> = backing.value
    }

    private class FakeSettingsDao : SettingsDao {
        private val backing = MutableStateFlow<Settings?>(Settings())
        val upserts = mutableListOf<Settings>()
        override fun observe() = backing
        override suspend fun upsert(settings: Settings) { backing.value = settings; upserts += settings }
    }

    private fun buildViewModel(
        containerDao: FakeContainerDao = FakeContainerDao(),
        settingsDao: FakeSettingsDao = FakeSettingsDao()
    ): Triple<ContainerViewModel, FakeContainerDao, FakeSettingsDao> {
        val scheduler = object : com.freezr.reminder.ReminderScheduler {
            override fun schedule(containerId: Long, triggerAtMillis: Long) { /* no-op for unit test */ }
            override fun cancel(containerId: Long) { /* no-op */ }
        }
        val vm = ContainerViewModel(
            ContainerRepository(containerDao),
            SettingsRepository(settingsDao),
            scheduler
        )
        return Triple(vm, containerDao, settingsDao)
    }

    @Test
    fun setSort_noChange_doesNotPersist() = runTest {
        val (vm, _, settingsDao) = buildViewModel()
        vm.setSort(SortOrder.CREATED_DESC)
        advanceUntilIdle()
        assertTrue(settingsDao.upserts.isEmpty())
    }

    @Test
    fun setSort_change_persistsUpdatedSort() = runTest {
        val (vm, _, settingsDao) = buildViewModel()
        vm.setSort(SortOrder.NAME_ASC)
        advanceUntilIdle()
        assertEquals(1, settingsDao.upserts.size)
        assertEquals(SortOrder.NAME_ASC, settingsDao.upserts.single().sortOrder)
    }

    @Test
    fun setShowArchived_noChange_doesNotPersist() = runTest {
        val (vm, _, settingsDao) = buildViewModel()
        vm.setShowArchived(false)
        advanceUntilIdle()
        assertTrue(settingsDao.upserts.isEmpty())
    }

    @Test
    fun setShowArchived_change_persists() = runTest {
        val (vm, _, settingsDao) = buildViewModel()
        vm.setShowArchived(true)
        advanceUntilIdle()
        assertEquals(1, settingsDao.upserts.size)
        assertTrue(settingsDao.upserts.single().showArchived)
    }

    @Test
    fun softDelete_thenUndo_restoresContainer() = runTest {
        val (vm, containerDao, _) = buildViewModel()
        containerDao.seed(Container(id = 1, name = "Item", status = Status.ACTIVE))
        vm.softDelete(1)
        advanceUntilIdle()
        assertTrue(containerDao.current().any { it.id == 1L && it.status == Status.DELETED })
        vm.undoLastDelete()
        advanceUntilIdle()
        val names = containerDao.current().map { it.name }
        assertTrue(names.count { it == "Item" } >= 1)
    }

    @Test
    fun undoLastDelete_whenNoneDeleted_isNoOp() = runTest {
        val (vm, containerDao, _) = buildViewModel()
        // No seed / delete performed
        vm.undoLastDelete()
        advanceUntilIdle()
        // Still empty list
        assertTrue(containerDao.current().isEmpty())
    }

    @Test
    fun createFromScan_createsNewContainer() = runTest {
        val (vm, containerDao, _) = buildViewModel()
        // Simulate scanning unknown code
        vm.handleScan("scan-uuid-1")
        advanceUntilIdle()
        vm.createFromScan("Alpha")
        advanceUntilIdle()
        assertTrue(containerDao.current().any { it.name == "Alpha" })
    }

    @Test
    fun archive_then_activate_updatesStatus() = runTest {
        val (vm, containerDao, _) = buildViewModel()
        containerDao.seed(Container(id = 10, name = "Box", status = Status.ACTIVE))
        vm.archive(10)
        advanceUntilIdle()
        assertTrue(containerDao.current().any { it.id == 10L && it.status == Status.ARCHIVED })
        vm.activate(10)
        advanceUntilIdle()
        assertTrue(containerDao.current().any { it.id == 10L && it.status == Status.ACTIVE })
    }
}
