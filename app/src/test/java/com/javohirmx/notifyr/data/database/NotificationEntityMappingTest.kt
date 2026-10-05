package com.javohirmx.notifyr.data.database

import com.google.common.truth.Truth.assertThat
import com.javohirmx.notifyr.domain.model.ActionType
import com.javohirmx.notifyr.domain.model.NotificationContext
import com.javohirmx.notifyr.domain.model.NotificationData
import com.javohirmx.notifyr.domain.model.NotificationImportance
import com.javohirmx.notifyr.domain.model.NotificationTags
import com.javohirmx.notifyr.domain.model.Priority
import com.javohirmx.notifyr.domain.model.TimeSensitivity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Guards the tag (de)serialization path.
 *
 * Decoded tags are memoized by their JSON text because the same string recurs across a large
 * history. Memoization is only safe if it is transparent, so these tests assert that repeated
 * decodes of the same input stay correct and that malformed input still degrades gracefully.
 */
class NotificationEntityMappingTest {

    private fun entity(tagsJson: String?, id: Long = 1L) = NotificationEntity(
        id = id,
        packageName = "com.test",
        appName = "Test App",
        title = "Title",
        text = "Body",
        category = null,
        importance = NotificationImportance.URGENT.value,
        timestamp = 1_700_000_000_000L,
        isRead = false,
        tagsJson = tagsJson
    )

    @Test
    fun `decodes tags from json`() {
        val tags = NotificationTags(
            priority = Priority.CRITICAL,
            timeSensitivity = TimeSensitivity.IMMEDIATE,
            actionType = ActionType.NEEDS_RESPONSE,
            contexts = setOf(NotificationContext.EMERGENCY, NotificationContext.FINANCIAL)
        )

        val domain = entity(Json.encodeToString(tags)).toDomain()

        assertThat(domain.tags).isEqualTo(tags)
    }

    @Test
    fun `absent tags json yields default tags`() {
        assertThat(entity(tagsJson = null).toDomain().tags).isEqualTo(NotificationTags())
    }

    @Test
    fun `malformed tags json yields default tags rather than throwing`() {
        val domain = entity(tagsJson = "{ not valid json ").toDomain()

        assertThat(domain.tags).isEqualTo(NotificationTags())
    }

    @Test
    fun `truncated tags json yields default tags rather than throwing`() {
        val domain = entity(tagsJson = "").toDomain()

        assertThat(domain.tags).isEqualTo(NotificationTags())
    }

    @Test
    fun `repeated decode of identical json returns equal tags`() {
        // The memoization is keyed on the JSON text; two decodes of the same text must agree
        // with each other and with a freshly decoded instance.
        val tags = NotificationTags(
            priority = Priority.IMPORTANT,
            timeSensitivity = TimeSensitivity.SOON,
            actionType = ActionType.TRANSACTIONAL
        )
        val json = Json.encodeToString(tags)

        val first = entity(json, id = 1L).toDomain().tags
        val second = entity(json, id = 2L).toDomain().tags
        val uncached = Json.decodeFromString<NotificationTags>(json)

        assertThat(first).isEqualTo(tags)
        assertThat(second).isEqualTo(tags)
        assertThat(first).isEqualTo(second)
        assertThat(first).isEqualTo(uncached)
    }

    @Test
    fun `distinct json strings do not collide in the cache`() {
        // Distinct tag sets share the cache; a collision would return the wrong tags for a row.
        val first = NotificationTags(priority = Priority.CRITICAL)
        val second = NotificationTags(priority = Priority.LOW)
        val third = NotificationTags(priority = Priority.IMPORTANT)

        assertThat(entity(Json.encodeToString(first), id = 1L).toDomain().tags).isEqualTo(first)
        assertThat(entity(Json.encodeToString(second), id = 2L).toDomain().tags).isEqualTo(second)
        assertThat(entity(Json.encodeToString(third), id = 3L).toDomain().tags).isEqualTo(third)

        // Re-read the first to confirm the cache did not hand back a later entry.
        assertThat(entity(Json.encodeToString(first), id = 4L).toDomain().tags).isEqualTo(first)
    }

    @Test
    fun `round trip through entity preserves tags`() {
        val original = NotificationData(
            id = 7L,
            packageName = "com.test",
            appName = "Test App",
            title = "Title",
            text = "Body",
            category = "msg",
            importance = NotificationImportance.NORMAL,
            timestamp = 1_700_000_000_000L,
            isRead = true,
            tags = NotificationTags(
                priority = Priority.IMPORTANT,
                customTags = setOf("work"),
                globalTagIds = setOf("g1"),
                timeSensitivity = TimeSensitivity.LATER,
                actionType = ActionType.CONVERSATIONAL,
                contexts = setOf(NotificationContext.WORK)
            ),
            sender = "Alice",
            conversationId = "conv-1"
        )

        val roundTripped = original.toEntity().toDomain()

        assertThat(roundTripped).isEqualTo(original)
    }

    @Test
    fun `unknown tag fields are ignored rather than failing the decode`() {
        // A row written by a newer app version must not break the list for an older one.
        val json = """{"priority":"NORMAL","somethingFromTheFuture":123}"""

        val domain = entity(json).toDomain()

        assertThat(domain.tags.priority).isEqualTo(Priority.NORMAL)
    }
}