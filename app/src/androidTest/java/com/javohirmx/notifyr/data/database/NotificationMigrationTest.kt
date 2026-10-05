package com.javohirmx.notifyr.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the v6 -> v7 migration.
 *
 * NotifyrDatabase's builder uses `fallbackToDestructiveMigration()`. That means a missing or
 * incorrect migration does not fail loudly in development - it silently deletes the entire
 * notification history on next launch. These tests pin the migration's SQL to the schema Room
 * generates for the entity, and assert that existing rows survive the upgrade.
 */
@RunWith(AndroidJUnit4::class)
class NotificationMigrationTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /**
     * The v6 notifications table, as one statement per list entry.
     *
     * Kept as a list rather than a single multi-statement string: `execSQL` runs only the first
     * statement of a multi-statement string and silently ignores the rest, which previously made
     * the fixture look index-free and masked what these tests are meant to assert.
     */
    private val schemaV6 = listOf(
        """
        CREATE TABLE IF NOT EXISTS `notifications` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          `packageName` TEXT NOT NULL,
          `appName` TEXT NOT NULL,
          `title` TEXT NOT NULL,
          `text` TEXT NOT NULL,
          `category` TEXT,
          `importance` INTEGER NOT NULL,
          `timestamp` INTEGER NOT NULL,
          `isRead` INTEGER NOT NULL,
          `tagsJson` TEXT,
          `sender` TEXT,
          `conversationId` TEXT
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_notifications_packageName` ON `notifications` (`packageName`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_timestamp` ON `notifications` (`timestamp`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_importance` ON `notifications` (`importance`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_isRead` ON `notifications` (`isRead`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_conversationId` ON `notifications` (`conversationId`)",
        "CREATE INDEX IF NOT EXISTS `index_notifications_packageName_timestamp` ON `notifications` (`packageName`, `timestamp`)"
    )

    /**
 * The other v6 tables, which this fixture must also provide.
 *
 * Room validates the whole schema on open, not just the migrated table, so a fixture containing
 * only `notifications` fails validation on `screen_time` before ever reaching the assertions
 * about the migration.
 */
    private val otherTablesV6 = listOf(
        """
        CREATE TABLE IF NOT EXISTS `screen_time` (
          `packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `date` INTEGER NOT NULL,
          `hour` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL,
          PRIMARY KEY(`packageName`, `date`, `hour`)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `custom_tags` (
          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL,
          `color` TEXT, `createdAt` INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `screen_time_sessions` (
          `packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `date` INTEGER NOT NULL,
          `startTime` INTEGER NOT NULL, `endTime` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL,
          PRIMARY KEY(`packageName`, `startTime`)
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_tags_name` ON `custom_tags` (`name`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_date` ON `screen_time` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_packageName` ON `screen_time` (`packageName`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_date_packageName` ON `screen_time` (`date`, `packageName`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_date_hour` ON `screen_time` (`date`, `hour`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_sessions_date` ON `screen_time_sessions` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_sessions_packageName` ON `screen_time_sessions` (`packageName`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_sessions_date_packageName` ON `screen_time_sessions` (`date`, `packageName`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_sessions_startTime` ON `screen_time_sessions` (`startTime`)",
        "CREATE INDEX IF NOT EXISTS `index_screen_time_sessions_endTime` ON `screen_time_sessions` (`endTime`)"
    )

    private fun SupportSQLiteDatabase.createSchemaV6() {
        (schemaV6 + otherTablesV6).forEach { execSQL(it) }
    }

    private fun indexNames(db: SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.query("PRAGMA index_list('notifications')").use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) names.add(c.getString(nameIndex))
        }
        return names
    }

    private fun withV6Database(block: (SupportSQLiteDatabase) -> Unit) {
        val name = "migration_test_${System.nanoTime()}"
        val context = instrumentation.targetContext
        val helper: SupportSQLiteOpenHelper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) = db.createSchemaV6()

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    })
                    .build()
            )
        try {
            helper.writableDatabase.use { db -> block(db) }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun fixture_really_starts_at_v6_with_its_original_indexes() {
        // Guards the fixture itself. If this drifts, the other tests can pass while asserting
        // nothing meaningful about a realistic starting schema.
        withV6Database { db ->
            val names = indexNames(db)
            assertEquals(6, names.size)
            assertTrue(names.contains("index_notifications_packageName_timestamp"))
        }
    }

    @Test
    fun migration_creates_the_indexes_room_expects() {
        withV6Database { db ->
            NotifyrDatabase.MIGRATION_6_7.migrate(db)

            val names = indexNames(db)

            // Names must match Room's generated CREATE INDEX statements exactly, or Room's
            // post-migration schema validation fails and the destructive fallback kicks in.
            listOf(
                "index_notifications_importance_timestamp",
                "index_notifications_isRead_importance_timestamp",
                "index_notifications_packageName_importance_timestamp"
            ).forEach { expected ->
                assertTrue("missing index $expected", names.contains(expected))
            }

            // And the pre-existing v6 indexes must still be there afterwards.
            listOf(
                "index_notifications_packageName",
                "index_notifications_timestamp",
                "index_notifications_importance",
                "index_notifications_isRead",
                "index_notifications_conversationId",
                "index_notifications_packageName_timestamp"
            ).forEach { expected ->
                assertTrue("migration dropped index $expected", names.contains(expected))
            }
            assertEquals(9, names.size)
        }
    }

    @Test
    fun migration_preserves_existing_notifications() {
        withV6Database { db ->
            db.execSQL(
                """
                INSERT INTO notifications
                  (packageName, appName, title, text, category, importance, timestamp, isRead,
                   tagsJson, sender, conversationId)
                VALUES
                  ('com.test', 'Test', 'Title', 'Body', 'msg', 1, 1700000000000, 0,
                   '{"priority":"NORMAL"}', 'sender', 'conv')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO notifications
                  (packageName, appName, title, text, category, importance, timestamp, isRead,
                   tagsJson, sender, conversationId)
                VALUES
                  ('com.other', 'Other', 'T2', 'B2', NULL, 2, 1700000001000, 1, NULL, NULL, NULL)
                """.trimIndent()
            )

            NotifyrDatabase.MIGRATION_6_7.migrate(db)

            db.query("SELECT COUNT(*) FROM notifications").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
            db.query("SELECT title, sender FROM notifications WHERE packageName = 'com.test'")
                .use { c ->
                    c.moveToFirst()
                    assertEquals("Title", c.getString(0))
                    assertEquals("sender", c.getString(1))
                }
        }
    }

    @Test
    fun migration_is_idempotent() {
        // DatabaseModule registers every migration; a re-run must not throw on existing indexes.
        withV6Database { db ->
            NotifyrDatabase.MIGRATION_6_7.migrate(db)
            NotifyrDatabase.MIGRATION_6_7.migrate(db)

            db.query("SELECT COUNT(*) FROM notifications").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun room_accepts_the_migrated_schema_and_keeps_the_rows() {
        // The end-to-end safety net. `fallbackToDestructiveMigration()` means a migration that
        // produces a schema Room does not expect will not fail loudly - it will drop the history.
        // So let Room run the migration itself and validate the result: if the SQL does not
        // produce exactly the schema implied by the entity, Room throws here instead of wiping.
        val name = "migration_e2e_${System.nanoTime()}"
        val context = instrumentation.targetContext

        // 1. Create a v6 database holding real rows, then close it.
        val helper: SupportSQLiteOpenHelper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.createSchemaV6()
                            db.execSQL(
                                """
                                INSERT INTO notifications
                                  (packageName, appName, title, text, category, importance,
                                   timestamp, isRead, tagsJson, sender, conversationId)
                                VALUES ('com.keep.me', 'Keep Me', 'Original Title', 'Original Body',
                                        'msg', 1, 1700000000000, 0, '{"priority":"NORMAL"}',
                                        'sender', 'conv')
                                """.trimIndent()
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) = Unit
                    })
                    .build()
            )
        helper.writableDatabase.close()

        // 2. Let Room migrate and validate.
        val room = androidx.room.Room
            .databaseBuilder(context, NotifyrDatabase::class.java, name)
            .addMigrations(NotifyrDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = room.notificationDao()
            kotlinx.coroutines.runBlocking {
                assertEquals("migration must not drop rows", 1, dao.getNotificationCount())

                val survivors = dao.getAllNotificationsSync()
                assertEquals(1, survivors.size)
                assertEquals("Original Title", survivors[0].title)
                assertEquals("com.keep.me", survivors[0].packageName)
            }

            // The new indexes must be usable, i.e. present in the migrated schema.
            val indexed = room.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name IN " +
                    "('index_notifications_importance_timestamp'," +
                    "'index_notifications_isRead_importance_timestamp'," +
                    "'index_notifications_packageName_importance_timestamp')"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals(3, indexed)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration_span_matches_the_previous_schema_version() {
        // A version bump without a matching, registered migration is the destructive-fallback
        // scenario, so pin the migration's span to the version that is actually on device.
        assertEquals(6, NotifyrDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, NotifyrDatabase.MIGRATION_6_7.endVersion)
    }
}