package com.javohirmx.notifyr.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "screen_time",
    indices = [
        Index(value = ["date"]),
        Index(value = ["packageName"]),
        Index(value = ["date", "packageName"]),
        Index(value = ["date", "hour"])
    ],
    primaryKeys = ["packageName", "date", "hour"]
)
data class ScreenTimeEntity(
    val packageName: String,
    val appName: String,
    val date: Long, // Day start timestamp (midnight in milliseconds)
    val hour: Int, // 0-23
    val durationMs: Long // Duration in milliseconds for this hour
)

