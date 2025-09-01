package com.freezr.data.repository

import com.freezr.data.database.ContainerDao
import com.freezr.data.model.Container
import com.freezr.data.model.SortOrder
import com.freezr.data.model.Status
import kotlinx.coroutines.flow.Flow

class ContainerRepository(private val dao: ContainerDao) {
    fun observe(showArchived: Boolean, sortOrder: SortOrder): Flow<List<Container>> =
        dao.observe(showArchived, sortOrder.name)

    suspend fun add(name: String) = dao.insert(Container(name = name))
    suspend fun archive(id: Long) = dao.updateStatus(id, Status.ARCHIVED)
    suspend fun activate(id: Long) = dao.updateStatus(id, Status.ACTIVE)
    suspend fun softDelete(id: Long) = dao.updateStatus(id, Status.DELETED)
}
