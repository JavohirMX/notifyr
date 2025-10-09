package com.javohirmx.notifyr.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [NotificationEntity::class],
    version = 2,
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
    }
}
