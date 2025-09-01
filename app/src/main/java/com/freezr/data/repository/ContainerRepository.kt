package com.freezr.data.repository

import com.freezr.data.database.ContainerDao
import com.freezr.data.model.Container
import com.freezr.data.model.SortOrder
import com.freezr.data.model.Status
import java.util.UUID
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
                    uuid = UUID.randomUUID().toString(), // ensure uniqueness when cloning explicitly
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

    /**
     * Reuse flow triggered from scanning an existing QR label:
     * 1. Archive the old record (so history is preserved) by marking it ARCHIVED and giving it a new random uuid so we can keep the original uuid for the new active record.
     * 2. Insert a new ACTIVE record with the original uuid and (optionally) updated name.
     * This preserves the physical label's QR code while keeping an archived snapshot.
     */
    suspend fun reusePreserveUuid(id: Long, newName: String?): Long {
        val existing = dao.getById(id) ?: return -1
        val originalUuid = existing.uuid
        val now = System.currentTimeMillis()
        // Archive old (assign a new uuid so unique constraint allows new row with original uuid)
        dao.update(existing.copy(status = Status.ARCHIVED, uuid = UUID.randomUUID().toString(), updatedAt = now))
        // Insert new active record with original uuid
        return dao.insert(
            Container(
                name = newName?.takeIf { it.isNotBlank() } ?: existing.name,
                uuid = originalUuid,
                quantity = existing.quantity,
                reminderDays = existing.reminderDays,
                notes = existing.notes
            )
        )
    }
}
