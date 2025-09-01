package com.freezr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "containers")
data class Container(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val status: Status = Status.ACTIVE,
    // New fields (v2):
    // When originally frozen (default to creation time for new rows)
    val frozenDate: Long = Instant.now().toEpochMilli(),
    // Per-item override of reminder days; null means use Settings.defaultReminderDays
    val reminderDays: Int? = null,
    // Quantity / portion count
    val quantity: Int = 1,
    // Optional notes (could be encrypted in future)
    val notes: String? = null,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)

// Added USED for future inventory logic; legacy ARCHIVED/DELETED retained until refactor aligns with PRS.
enum class Status { ACTIVE, ARCHIVED, DELETED, USED }
