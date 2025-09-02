package com.freezr.data.repository

import com.freezr.data.database.SettingsDao
import com.freezr.data.model.Settings
import com.freezr.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: SettingsDao) {
    val settings: Flow<Settings> = dao.observe().map { it ?: Settings() }
    suspend fun updateSort(sort: SortOrder, current: Settings) =
        dao.upsert(current.copy(sortOrder = sort))
    suspend fun updateShowUsed(show: Boolean, current: Settings) =
        dao.upsert(current.copy(showUsed = show))
    suspend fun updateDefaultReminderDays(days: Int, current: Settings) =
        dao.upsert(current.copy(defaultReminderDays = days))
    suspend fun updateExpiringSoonDays(days: Int, current: Settings) =
        dao.upsert(current.copy(expiringSoonDays = days))
    suspend fun updateCriticalDays(days: Int, current: Settings) =
        dao.upsert(current.copy(criticalDays = days))
}
