package com.freezr.data.repository

import com.freezr.data.database.ContainerDao
import com.freezr.data.model.Container
import com.freezr.data.model.SortOrder
import com.freezr.data.model.Status
import kotlinx.coroutines.flow.Flow

class ContainerRepository(private val dao: ContainerDao) {
    fun observe(showArchived: Boolean, sortOrder: SortOrder): Flow<List<Container>> =
        dao.observe(showArchived, sortOrder.name)

    suspend fun add(name: String, quantity: Int = 1, reminderDays: Int? = null) =
        dao.insert(Container(name = name, quantity = quantity, reminderDays = reminderDays))
    suspend fun addFromScan(uuid: String, name: String = "Scanned", quantity: Int = 1): Long {
        val existing = dao.getByUuid(uuid)
        if (existing != null) return existing.id
        // Need to construct then replace uuid via copy
        val base = Container(name = name, quantity = quantity)
        val withUuid = base.copy(uuid = uuid)
        return dao.insert(withUuid)
    }
    suspend fun archive(id: Long) = dao.updateStatus(id, Status.ARCHIVED)
    suspend fun activate(id: Long) = dao.updateStatus(id, Status.ACTIVE)
    suspend fun softDelete(id: Long) = dao.updateStatus(id, Status.DELETED)
    suspend fun reuse(id: Long, newName: String? = null): Long {
        val existing = dao.getById(id)
        return dao.insert(
            if (existing != null) {
                existing.copy(
                    id = 0,
                    name = newName ?: existing.name,
                    frozenDate = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    status = Status.ACTIVE
                )
            } else {
                Container(name = newName ?: "Reused")
            }
        )
    }

    suspend fun findByUuid(uuid: String) = dao.getByUuid(uuid)
}
