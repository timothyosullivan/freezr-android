package com.freezr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.util.UUID
import java.time.Instant

@Entity(
    tableName = "containers",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class Container(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Stable globally unique identifier for labels / QR codes
    val uuid: String = UUID.randomUUID().toString(),
    // Name may be blank for UNUSED placeholder labels (claimed later)
    val name: String,
    val status: Status = Status.ACTIVE,
    // When originally frozen (default to creation time). For UNUSED placeholders we still stamp now;
    // age calculations should ignore UNUSED status in UI logic.
    val frozenDate: Long = Instant.now().toEpochMilli(),
    // Per-item override: notification reminder offset in days (when to notify). Null uses shelf life or default.
    val reminderDays: Int? = null,
    // Per-item override of shelf life (duration food considered good) in days; null uses Settings.defaultReminderDays
    val shelfLifeDays: Int? = null,
    // Quantity / portion count
    val quantity: Int = 1,
    // Optional notes (could be encrypted in future)
    val notes: String? = null,
    // When this container should trigger a reminder (epoch millis); nullable until scheduled
    val reminderAt: Long? = null,
    // When marked used/consumed
    val dateUsed: Long? = null,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)

// Lifecycle simplified: UNUSED -> ACTIVE -> USED (history) or DELETED (soft delete hidden).
enum class Status { UNUSED, ACTIVE, DELETED, USED }
