package com.javohirmx.notifyr.data.repository

import androidx.datastore.core.DataStore
import com.javohirmx.notifyr.data.datastore.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How long notification history is kept on the device.
 *
 * A value of 0 (or any non-positive number) means history is kept until the user clears it.
 */
@Singleton
class NotificationRetentionRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) {

    val retentionDays: Flow<Int> = dataStore.data.map { it.notificationRetentionDays }

    suspend fun currentRetentionDays(): Int = dataStore.data.first().notificationRetentionDays

    suspend fun setRetentionDays(days: Int) {
        val normalized = if (days <= 0) 0 else days.coerceAtMost(MAX_RETENTION_DAYS)
        dataStore.updateData { it.copy(notificationRetentionDays = normalized) }
    }

    companion object {
        const val MAX_RETENTION_DAYS = 365
        val PRESET_DAYS = listOf(0, 30, 60, 90, 180, 365)
    }
}
