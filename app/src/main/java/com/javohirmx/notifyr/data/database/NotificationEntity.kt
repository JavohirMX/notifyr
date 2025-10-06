package com.javohirmx.notifyr.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val category: String?,
    val importance: Int, // Store as Int for Room compatibility
    val timestamp: Long,
    val isRead: Boolean = false
)

// Extension functions for conversion
fun NotificationEntity.toDomain(): NotificationData {
    return NotificationData(
        id = id,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        category = category,
        importance = NotificationImportance.fromValue(importance),
        timestamp = timestamp,
        isRead = isRead
    )
}

fun NotificationData.toEntity(): NotificationEntity {
    return NotificationEntity(
        id = id,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        category = category,
        importance = importance.value,
        timestamp = timestamp,
        isRead = isRead
    )
}
