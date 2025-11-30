package com.javohirmx.notifyr.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "custom_tags",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class CustomTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String? = null, // Optional color for UI display (hex color string)
    val createdAt: Long = System.currentTimeMillis()
)

