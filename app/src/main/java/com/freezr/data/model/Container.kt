package com.freezr.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "containers")
data class Container(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val status: Status = Status.ACTIVE,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)

enum class Status { ACTIVE, ARCHIVED, DELETED }
