package com.javohirmx.notifyr.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.javohirmx.notifyr.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.util.Collections
import java.util.LinkedHashMap

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["packageName"]),
        Index(value = ["timestamp"]),
        Index(value = ["importance"]),
        Index(value = ["isRead"]),
        Index(value = ["conversationId"]),
        Index(value = ["packageName", "timestamp"]),
        // Composite indexes for the hot list/count paths. The single-column indexes above only
        // narrow the scan; these also carry the ORDER BY timestamp DESC, so the three screens
        // that read history newest-first stop paying for a sort on every query.
        Index(value = ["importance", "timestamp"]),
        Index(value = ["isRead", "importance", "timestamp"]),
        Index(value = ["packageName", "importance", "timestamp"])
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
    val conversationId: String? = null,
    val isPinned: Boolean = false
)

// Extension functions for conversion

/**
 * Lightweight projection for `SELECT DISTINCT packageName, appName`.
 *
 * Lets the app-rules screen build its app list without reading notification rows.
 */
data class AppName(
    val packageName: String,
    val appName: String
)

/**
 * Single shared [Json] instance used for tag (de)serialization.
 *
 * Creating a `Json` per call and relying on `Json.Default` means every decode pays for
 * reflective serializer lookup and re-parses identical input. One configured instance is
 * both cheaper and more forgiving of tags written by older app versions.
 */
private val tagsJsonCodec = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Bounded memoization of decoded [NotificationTags], keyed on the raw JSON string.
 *
 * [NotificationTags] is assembled from a small space of enums (Priority x TimeSensitivity
 * x ActionType x NotificationContext), so the same JSON string recurs across thousands of
 * rows in a large history. Memoizing turns tens of thousands of reflective JSON parses per
 * database emission down to a handful.
 *
 * Deliberately a pure-JVM LRU rather than `android.util.LruCache`: this mapping is exercised
 * by JVM unit tests, where the Android framework stubs throw on real method calls.
 *
 * Parse failures are intentionally not cached, so a row written by a future/older schema is
 * re-read rather than being pinned to a bogus default.
 */
private const val TAGS_CACHE_MAX_ENTRIES = 256

private val tagsCache: MutableMap<String, NotificationTags> =
    Collections.synchronizedMap(
        object : LinkedHashMap<String, NotificationTags>(
            /* initialCapacity = */ TAGS_CACHE_MAX_ENTRIES,
            /* loadFactor = */ 0.75f,
            /* accessOrder = */ true
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, NotificationTags>
            ): Boolean = size > TAGS_CACHE_MAX_ENTRIES
        }
    )

private fun decodeTags(tagsJson: String): NotificationTags {
    tagsCache[tagsJson]?.let { return it }

    val decoded = try {
        tagsJsonCodec.decodeFromString<NotificationTags>(tagsJson)
    } catch (e: Exception) {
        null
    } ?: return NotificationTags()

    tagsCache[tagsJson] = decoded
    return decoded
}

fun NotificationEntity.toDomain(): NotificationData {
    val tags = tagsJson?.let { decodeTags(it) } ?: NotificationTags()
    
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
        conversationId = conversationId,
        isPinned = isPinned
    )
}

fun NotificationData.toEntity(): NotificationEntity {
    val tagsJson = try {
        tagsJsonCodec.encodeToString(tags)
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
        conversationId = conversationId,
        isPinned = isPinned
    )
}
