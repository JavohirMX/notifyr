package com.javohirmx.notifyr.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [NotificationEntity::class],
    version = 3,
    exportSchema = false
)
abstract class NotifyrDatabase : RoomDatabase() {
    
    abstract fun notificationDao(): NotificationDao
    
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
    }
}
