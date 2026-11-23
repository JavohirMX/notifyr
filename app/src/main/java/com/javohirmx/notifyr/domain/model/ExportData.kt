package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val version: Int = CURRENT_VERSION,
    val exportDate: Long = System.currentTimeMillis(),
    val appName: String = "Notifyr",
    val appRules: List<AppRule> = emptyList(),
    val keywordRules: List<KeywordRule> = emptyList(),
    val notifications: List<NotificationData> = emptyList(),
    /** Null when the export excluded settings. */
    val settings: ExportSettings? = null
) {
    companion object {
        /**
         * Version 2 added real settings (digest, focus, retention). Version 1 files only
         * contained placeholder values, which importers must ignore.
         */
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class ExportSettings(
    val notificationRetentionDays: Int = 90,
    val digestMode: DigestMode = DigestMode.CONTEXT_AWARE,
    val digestCustomTimes: List<TimeOfDay> = emptyList(),
    val digestMinNotificationThreshold: Int = 3,
    val digestUnlockDelayMinutes: Int = 30,
    val digestGroupConversations: Boolean = true,
    val digestGroupByApp: Boolean = true,
    val digestShowSummary: Boolean = true,
    val focusMode: FocusMode = FocusMode.NORMAL,
    val focusAutoSwitch: Boolean = false,
    val focusWorkHours: TimeRange? = null,
    val focusSleepHours: TimeRange = TimeRange(22, 0, 7, 0),
    val focusWorkDays: Set<Int> = setOf(2, 3, 4, 5, 6)
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
    val notifications: Int = 0,
    /** 1 when the file contained settings that were applied, otherwise 0. */
    val settings: Int = 0
)
