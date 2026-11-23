package com.javohirmx.notifyr.data.service

import android.content.Context
import android.net.Uri
import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.DigestSettingsRepository
import com.javohirmx.notifyr.data.repository.FocusSettingsRepository
import com.javohirmx.notifyr.data.repository.KeywordRulesRepository
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.NotificationRetentionRepository
import com.javohirmx.notifyr.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExportImportService @Inject constructor(
    private val appRulesRepository: AppRulesRepository,
    private val keywordRulesRepository: KeywordRulesRepository,
    private val notificationRepository: NotificationRepository,
    private val digestSettingsRepository: DigestSettingsRepository,
    private val focusSettingsRepository: FocusSettingsRepository,
    private val retentionRepository: NotificationRetentionRepository
) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    suspend fun exportData(
        context: Context,
        exportType: ExportType,
        includeSettings: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = buildExportData(exportType, includeSettings)
            val jsonString = json.encodeToString(exportData)
            val fileName = generateFileName(exportType)
            val file = File(context.getExternalFilesDir(null), fileName)
            
            FileOutputStream(file).use { output ->
                output.write(jsonString.toByteArray())
            }
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                itemsExported = countItems(exportData)
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                error = "Export failed: ${e.message}"
            )
        }
    }
    
    suspend fun exportDataToUri(
        context: Context,
        uri: Uri,
        exportType: ExportType,
        includeSettings: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = buildExportData(exportType, includeSettings)
            val jsonString = json.encodeToString(exportData)
            
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(jsonString.toByteArray())
            } ?: throw IOException("Could not open output stream")
            
            ExportResult(
                success = true,
                filePath = uri.toString(),
                itemsExported = countItems(exportData)
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                error = "Export failed: ${e.message}"
            )
        }
    }

    private suspend fun buildExportData(
        exportType: ExportType,
        includeSettings: Boolean
    ): ExportData {
        val settings = if (includeSettings && exportType != ExportType.NOTIFICATIONS_ONLY) {
            getExportSettings()
        } else {
            null
        }

        return when (exportType) {
            ExportType.SETTINGS_ONLY -> ExportData(
                appRules = appRulesRepository.exportAppRules(),
                keywordRules = keywordRulesRepository.exportKeywords(),
                settings = settings
            )
            ExportType.NOTIFICATIONS_ONLY -> ExportData(
                notifications = notificationRepository.exportNotifications()
            )
            ExportType.COMPLETE -> ExportData(
                appRules = appRulesRepository.exportAppRules(),
                keywordRules = keywordRulesRepository.exportKeywords(),
                notifications = notificationRepository.exportNotifications(),
                settings = settings
            )
        }
    }

    private fun countItems(exportData: ExportData): Int {
        return exportData.appRules.size +
            exportData.keywordRules.size +
            exportData.notifications.size +
            if (exportData.settings != null) 1 else 0
    }
    
    suspend fun importDataFromUri(
        context: Context,
        uri: Uri,
        replaceExisting: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw IOException("Could not read input stream")
            
            val exportData = json.decodeFromString<ExportData>(jsonString)
            ImportResult(
                success = true,
                itemsImported = applyImport(exportData, replaceExisting)
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                error = "Import failed: ${e.message}"
            )
        }
    }
    
    suspend fun importDataFromFile(
        file: File,
        replaceExisting: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = json.decodeFromString<ExportData>(file.readText())
            ImportResult(
                success = true,
                itemsImported = applyImport(exportData, replaceExisting)
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                error = "Import failed: ${e.message}"
            )
        }
    }

    private suspend fun applyImport(
        exportData: ExportData,
        replaceExisting: Boolean
    ): ImportCounts {
        var appRulesCount = 0
        var keywordRulesCount = 0
        var notificationsCount = 0
        var settingsCount = 0

        // Import app rules
        if (exportData.appRules.isNotEmpty()) {
            if (replaceExisting) {
                appRulesRepository.clearAllRules()
            }
            appRulesRepository.importAppRules(exportData.appRules)
            appRulesCount = exportData.appRules.size
        }

        // Import keyword rules
        if (exportData.keywordRules.isNotEmpty()) {
            if (replaceExisting) {
                keywordRulesRepository.clearAllKeywords()
            }
            keywordRulesRepository.importKeywords(exportData.keywordRules)
            keywordRulesCount = exportData.keywordRules.size
        }

        // Import notifications
        if (exportData.notifications.isNotEmpty()) {
            if (replaceExisting) {
                notificationRepository.deleteAllNotifications()
            }
            notificationRepository.importNotifications(exportData.notifications)
            notificationsCount = exportData.notifications.size
        }

        // Settings were only added to the export format in version 2. Version 1 files
        // carry placeholder defaults, so importing them would silently overwrite the
        // user's real settings.
        val settings = exportData.settings
        if (exportData.version >= 2 && settings != null) {
            applySettings(settings)
            settingsCount = 1
        }

        return ImportCounts(
            appRules = appRulesCount,
            keywordRules = keywordRulesCount,
            notifications = notificationsCount,
            settings = settingsCount
        )
    }

    private suspend fun getExportSettings(): ExportSettings {
        val digest = digestSettingsRepository.digestSettings.first()
        val focus = focusSettingsRepository.focusSettings.first()

        return ExportSettings(
            notificationRetentionDays = retentionRepository.currentRetentionDays(),
            digestMode = digest.mode,
            digestCustomTimes = digest.customTimes,
            digestMinNotificationThreshold = digest.minNotificationThreshold,
            digestUnlockDelayMinutes = digest.unlockDelayMinutes,
            digestGroupConversations = digest.groupConversations,
            digestGroupByApp = digest.groupByApp,
            digestShowSummary = digest.showSummary,
            focusMode = focus.currentMode,
            focusAutoSwitch = focus.autoSwitch,
            focusWorkHours = focus.workHours,
            focusSleepHours = focus.sleepHours,
            focusWorkDays = focus.workDays
        )
    }

    private suspend fun applySettings(settings: ExportSettings) {
        retentionRepository.setRetentionDays(settings.notificationRetentionDays)

        digestSettingsRepository.updateDigestSettings(
            DigestSettings(
                mode = settings.digestMode,
                customTimes = settings.digestCustomTimes,
                minNotificationThreshold = settings.digestMinNotificationThreshold,
                unlockDelayMinutes = settings.digestUnlockDelayMinutes,
                groupConversations = settings.digestGroupConversations,
                groupByApp = settings.digestGroupByApp,
                showSummary = settings.digestShowSummary
            )
        )

        // Preserve fields that are not part of the export (quiet hours, emergency contacts)
        // instead of resetting them to defaults.
        val currentFocus = focusSettingsRepository.focusSettings.first()
        focusSettingsRepository.updateFocusSettings(
            currentFocus.copy(
                currentMode = settings.focusMode,
                autoSwitch = settings.focusAutoSwitch,
                workHours = settings.focusWorkHours,
                sleepHours = settings.focusSleepHours,
                workDays = settings.focusWorkDays
            )
        )
    }
    
    private fun generateFileName(exportType: ExportType): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        
        return when (exportType) {
            ExportType.SETTINGS_ONLY -> "notifyr_settings_$timestamp.json"
            ExportType.NOTIFICATIONS_ONLY -> "notifyr_notifications_$timestamp.json"
            ExportType.COMPLETE -> "notifyr_complete_$timestamp.json"
        }
    }
    
    fun validateExportFile(context: Context, uri: Uri): Boolean {
        return try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return false
            
            val exportData = json.decodeFromString<ExportData>(jsonString)
            
            // Basic validation
            exportData.version > 0 && exportData.appName == "Notifyr"
        } catch (e: Exception) {
            false
        }
    }
}
