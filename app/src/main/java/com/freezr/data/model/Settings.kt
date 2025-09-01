package com.freezr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val key: String = "user",
    val sortOrder: SortOrder = SortOrder.CREATED_DESC,
    val showArchived: Boolean = false
)

enum class SortOrder { NAME_ASC, NAME_DESC, CREATED_ASC, CREATED_DESC }
