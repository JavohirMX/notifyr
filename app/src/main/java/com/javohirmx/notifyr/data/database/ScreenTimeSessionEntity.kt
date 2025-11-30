package com.javohirmx.notifyr.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing minute-level screen time sessions
 * Each entry represents a single usage session with exact start and end times
 */
@Entity(
    tableName = "screen_time_sessions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["packageName"]),
        Index(value = ["date", "packageName"]),
        Index(value = ["startTime"]),
        Index(value = ["endTime"])
    ],
    primaryKeys = ["packageName", "startTime"]
)
data class ScreenTimeSessionEntity(
    val packageName: String,
    val appName: String,
    val date: Long, // Day start timestamp (midnight in milliseconds)
    val startTime: Long, // Session start timestamp in milliseconds
    val endTime: Long, // Session end timestamp in milliseconds
    val durationMs: Long // Duration in milliseconds (endTime - startTime)
)

