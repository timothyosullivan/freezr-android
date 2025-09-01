package com.freezr.data.database

import androidx.room.*
import com.freezr.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContainerDao {
    @Query("SELECT * FROM containers WHERE status != 'DELETED' AND (:includeArchived OR status != 'ARCHIVED') ORDER BY CASE WHEN :order = 'NAME_ASC' THEN name END ASC, CASE WHEN :order = 'NAME_DESC' THEN name END DESC, CASE WHEN :order = 'CREATED_ASC' THEN createdAt END ASC, CASE WHEN :order = 'CREATED_DESC' THEN createdAt END DESC, id DESC")
    fun observe(includeArchived: Boolean, order: String): Flow<List<Container>>

    @Insert
    suspend fun insert(container: Container): Long

    @Update
    suspend fun update(container: Container)

    @Query("UPDATE containers SET status = :status, updatedAt = strftime('%s','now')*1000 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Status)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE key = 'user' LIMIT 1")
    fun observe(): Flow<Settings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: Settings)
}
