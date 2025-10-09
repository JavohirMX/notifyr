package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val appName: String = "Notifyr",
    val appRules: List<AppRule> = emptyList(),
    val keywordRules: List<KeywordRule> = emptyList(),
    val notifications: List<NotificationData> = emptyList(),
    val settings: ExportSettings = ExportSettings()
)

@Serializable
data class ExportSettings(
    val digestNotificationsEnabled: Boolean = false,
    val digestIntervalHours: Int = 4,
    val dataRetentionDays: Int = 30,
    val isDeveloperModeEnabled: Boolean = false
)

enum class ExportType {
    SETTINGS_ONLY,      // Only app rules, keyword rules, and settings
    NOTIFICATIONS_ONLY, // Only notification history
    COMPLETE           // Everything
}

data class ExportResult(
    val success: Boolean,
    val filePath: String? = null,
    val error: String? = null,
    val itemsExported: Int = 0
)

data class ImportResult(
    val success: Boolean,
    val error: String? = null,
    val itemsImported: ImportCounts = ImportCounts()
)

data class ImportCounts(
    val appRules: Int = 0,
    val keywordRules: Int = 0,
    val notifications: Int = 0
)
