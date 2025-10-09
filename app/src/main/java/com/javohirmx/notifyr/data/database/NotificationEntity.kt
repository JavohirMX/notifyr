package com.javohirmx.notifyr.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.javohirmx.notifyr.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"]),
        Index(value = ["importance"]),
        Index(value = ["isRead"]),
        Index(value = ["conversationId"]),
        Index(value = ["packageName", "timestamp"])
    ]
)
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
    val isRead: Boolean = false,
    // New enhanced fields
    val tagsJson: String? = null,  // JSON serialized NotificationTags
    val sender: String? = null,
    val conversationId: String? = null
)

// Extension functions for conversion
fun NotificationEntity.toDomain(): NotificationData {
    val tags = try {
        tagsJson?.let { Json.decodeFromString<NotificationTags>(it) } ?: NotificationTags()
    } catch (e: Exception) {
        NotificationTags()
    }
    
    return NotificationData(
        id = id,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        category = category,
        importance = NotificationImportance.fromValue(importance),
        timestamp = timestamp,
        isRead = isRead,
        tags = tags,
        sender = sender,
        conversationId = conversationId
    )
}

fun NotificationData.toEntity(): NotificationEntity {
    val tagsJson = try {
        Json.encodeToString(tags)
    } catch (e: Exception) {
        null
    }
    
    return NotificationEntity(
        id = id,
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        category = category,
        importance = importance.value,
        timestamp = timestamp,
        isRead = isRead,
        tagsJson = tagsJson,
        sender = sender,
        conversationId = conversationId
    )
}
