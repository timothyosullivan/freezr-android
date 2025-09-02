package com.freezr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val key: String = "user",
    val sortOrder: SortOrder = SortOrder.CREATED_DESC,
    val showUsed: Boolean = false,
    // Default reminder window (days) applied when Container.reminderDays == null
    val defaultReminderDays: Int = 60,
    // Threshold for when an item is considered "expiring soon" (amber). Must be > criticalDays.
    val expiringSoonDays: Int = 7,
    // Threshold for when an item is considered critical/near expiry (red).
    val criticalDays: Int = 2
)

enum class SortOrder { NAME_ASC, NAME_DESC, CREATED_ASC, CREATED_DESC }
