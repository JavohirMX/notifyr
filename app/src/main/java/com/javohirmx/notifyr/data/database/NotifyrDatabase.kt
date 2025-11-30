package com.javohirmx.notifyr.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [NotificationEntity::class, ScreenTimeEntity::class, CustomTagEntity::class],
    version = 5,
    exportSchema = false
)
abstract class NotifyrDatabase : RoomDatabase() {
    
    abstract fun notificationDao(): NotificationDao
    abstract fun screenTimeDao(): ScreenTimeDao
    abstract fun customTagDao(): CustomTagDao
    
    companion object {
        const val DATABASE_NAME = "notifyr_database"
        
        /**
         * Migration from version 1 to 2
         * Adds: tagsJson, sender, conversationId columns
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE notifications ADD COLUMN tagsJson TEXT"
                )
                database.execSQL(
                    "ALTER TABLE notifications ADD COLUMN sender TEXT"
                )
                database.execSQL(
                    "ALTER TABLE notifications ADD COLUMN conversationId TEXT"
                )
            }
        }
        
        /**
         * Migration from version 2 to 3
         * Adds: Database indices for performance
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add indices for frequently queried columns
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_packageName ON notifications(packageName)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_timestamp ON notifications(timestamp)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_importance ON notifications(importance)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_isRead ON notifications(isRead)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_conversationId ON notifications(conversationId)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notifications_packageName_timestamp ON notifications(packageName, timestamp)"
                )
            }
        }
        
        /**
         * Migration from version 3 to 4
         * Adds: Screen time tracking table
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create screen_time table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS screen_time (
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        hour INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        PRIMARY KEY(packageName, date, hour)
                    )
                """)
                
                // Create indices
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_screen_time_date ON screen_time(date)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_screen_time_packageName ON screen_time(packageName)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_screen_time_date_packageName ON screen_time(date, packageName)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_screen_time_date_hour ON screen_time(date, hour)"
                )
            }
        }
        
        /**
         * Migration from version 4 to 5
         * Adds: Custom tags table
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create custom_tags table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS custom_tags (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        color TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
                
                // Create unique index on name
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_tags_name ON custom_tags(name)"
                )
            }
        }
    }
}
