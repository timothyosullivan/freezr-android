package com.freezr.data.repository

import com.freezr.data.database.ContainerDao
import com.freezr.data.model.Container
import com.freezr.data.model.SortOrder
import com.freezr.data.model.Status
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ContainerRepository(private val dao: ContainerDao) {
    fun observe(showUsed: Boolean, sortOrder: SortOrder): Flow<List<Container>> =
        dao.observe(showUsed, sortOrder.name)

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
    // archive/activate removed; markUsed + reuse flows cover lifecycle
    suspend fun softDelete(id: Long) = dao.updateStatus(id, Status.DELETED)
    suspend fun markUsed(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(status = Status.USED, dateUsed = System.currentTimeMillis()))
    }
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

    suspend fun snooze(id: Long, days: Int) {
        val existing = dao.getById(id) ?: return
        if (existing.status != Status.ACTIVE) return
        val base = existing.reminderAt?.takeIf { it > System.currentTimeMillis() } ?: System.currentTimeMillis()
        val newAt = base + days * 24L * 60L * 60L * 1000L
        dao.update(existing.copy(reminderAt = newAt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateReminderDays(id: Long, days: Int) {
        val existing = dao.getById(id) ?: return
        val newAt = System.currentTimeMillis() + days * 24L * 60L * 60L * 1000L
        dao.update(existing.copy(reminderDays = days, reminderAt = newAt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateShelfLifeDays(id: Long, days: Int) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(shelfLifeDays = days, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateReminderAt(id: Long, at: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(reminderAt = at, updatedAt = System.currentTimeMillis()))
    }

    suspend fun getById(id: Long) = dao.getById(id)

    suspend fun updateReminderExplicit(id: Long, days: Int, at: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(reminderDays = days, reminderAt = at, updatedAt = System.currentTimeMillis()))
    }

    suspend fun findByUuid(uuid: String) = dao.getByUuid(uuid)

    /**
     * Generate a batch of UNUSED placeholder label rows. They start with blank name and UNUSED status.
     * Returns the inserted containers (freshly generated uuids) for immediate PDF rendering.
     */
    suspend fun generatePlaceholders(count: Int): List<Container> {
        if (count <= 0) return emptyList()
        val list = (1..count).map {
            Container(name = "", status = Status.UNUSED)
        }
        val ids = dao.insertAll(list)
        return list.mapIndexed { idx, c -> c.copy(id = ids[idx]) }
    }

    /** Claim an UNUSED placeholder by uuid, updating its name and setting status ACTIVE. */
    suspend fun claimPlaceholder(uuid: String, name: String, quantity: Int = 1, shelfLifeDays: Int? = null, reminderDays: Int? = null): Long {
        val existing = dao.getByUuid(uuid) ?: return -1
        if (existing.status != Status.UNUSED) return existing.id // already claimed
        val now = System.currentTimeMillis()
        val computedReminderAt = reminderDays?.let { now + it * 24L * 60L * 60L * 1000L }
        val updated = existing.copy(
            name = name,
            quantity = quantity,
            shelfLifeDays = shelfLifeDays,
            reminderDays = reminderDays,
            status = Status.ACTIVE,
            frozenDate = now,
            reminderAt = computedReminderAt,
            updatedAt = now
        )
        dao.update(updated)
        return updated.id
    }

    /**
     * Reuse flow triggered from scanning an existing QR label:
     * 1. Mark old record USED and assign it a new random uuid so we can keep the original uuid for the new ACTIVE record.
     * 2. Insert a new ACTIVE record with the original uuid and optional updated name.
     * This preserves label QR while keeping historical USED snapshot.
     */
    suspend fun reusePreserveUuid(id: Long, newName: String?): Long {
        val existing = dao.getById(id) ?: return -1
        val originalUuid = existing.uuid
        val now = System.currentTimeMillis()
    // Mark old used (assign a new uuid so unique constraint allows new row with original uuid)
    dao.update(existing.copy(status = Status.USED, dateUsed = now, uuid = UUID.randomUUID().toString(), updatedAt = now))
        // Insert new active record with original uuid
        return dao.insert(
            Container(
                name = newName?.takeIf { it.isNotBlank() } ?: existing.name,
                uuid = originalUuid,
                quantity = existing.quantity,
                reminderDays = existing.reminderDays,
                shelfLifeDays = existing.shelfLifeDays,
                notes = existing.notes
            )
        )
    }
}
