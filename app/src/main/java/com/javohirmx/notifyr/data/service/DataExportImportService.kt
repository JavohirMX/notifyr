package com.javohirmx.notifyr.data.service

import android.content.Context
import android.net.Uri
import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.KeywordRulesRepository
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.domain.model.*
import kotlinx.coroutines.Dispatchers
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
    private val notificationRepository: NotificationRepository
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
            val exportData = when (exportType) {
                ExportType.SETTINGS_ONLY -> ExportData(
                    appRules = appRulesRepository.exportAppRules(),
                    keywordRules = keywordRulesRepository.exportKeywords(),
                    settings = if (includeSettings) getExportSettings() else ExportSettings()
                )
                ExportType.NOTIFICATIONS_ONLY -> ExportData(
                    notifications = notificationRepository.exportNotifications()
                )
                ExportType.COMPLETE -> ExportData(
                    appRules = appRulesRepository.exportAppRules(),
                    keywordRules = keywordRulesRepository.exportKeywords(),
                    notifications = notificationRepository.exportNotifications(),
                    settings = if (includeSettings) getExportSettings() else ExportSettings()
                )
            }
            
            val jsonString = json.encodeToString(exportData)
            val fileName = generateFileName(exportType)
            val file = File(context.getExternalFilesDir(null), fileName)
            
            FileOutputStream(file).use { output ->
                output.write(jsonString.toByteArray())
            }
            
            val totalItems = exportData.appRules.size + 
                           exportData.keywordRules.size + 
                           exportData.notifications.size
            
            ExportResult(
                success = true,
                filePath = file.absolutePath,
                itemsExported = totalItems
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
            val exportData = when (exportType) {
                ExportType.SETTINGS_ONLY -> ExportData(
                    appRules = appRulesRepository.exportAppRules(),
                    keywordRules = keywordRulesRepository.exportKeywords(),
                    settings = if (includeSettings) getExportSettings() else ExportSettings()
                )
                ExportType.NOTIFICATIONS_ONLY -> ExportData(
                    notifications = notificationRepository.exportNotifications()
                )
                ExportType.COMPLETE -> ExportData(
                    appRules = appRulesRepository.exportAppRules(),
                    keywordRules = keywordRulesRepository.exportKeywords(),
                    notifications = notificationRepository.exportNotifications(),
                    settings = if (includeSettings) getExportSettings() else ExportSettings()
                )
            }
            
            val jsonString = json.encodeToString(exportData)
            
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(jsonString.toByteArray())
            } ?: throw IOException("Could not open output stream")
            
            val totalItems = exportData.appRules.size + 
                           exportData.keywordRules.size + 
                           exportData.notifications.size
            
            ExportResult(
                success = true,
                filePath = uri.toString(),
                itemsExported = totalItems
            )
        } catch (e: Exception) {
            ExportResult(
                success = false,
                error = "Export failed: ${e.message}"
            )
        }
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
            
            var appRulesCount = 0
            var keywordRulesCount = 0
            var notificationsCount = 0
            
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
            
            // Import settings (if needed, would require preferences repository)
            // TODO: Implement settings import when preferences repository is available
            
            ImportResult(
                success = true,
                itemsImported = ImportCounts(
                    appRules = appRulesCount,
                    keywordRules = keywordRulesCount,
                    notifications = notificationsCount
                )
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
            val jsonString = file.readText()
            val exportData = json.decodeFromString<ExportData>(jsonString)
            
            var appRulesCount = 0
            var keywordRulesCount = 0
            var notificationsCount = 0
            
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
            
            ImportResult(
                success = true,
                itemsImported = ImportCounts(
                    appRules = appRulesCount,
                    keywordRules = keywordRulesCount,
                    notifications = notificationsCount
                )
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                error = "Import failed: ${e.message}"
            )
        }
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
    
    private fun getExportSettings(): ExportSettings {
        // TODO: Get actual settings from preferences repository when available
        return ExportSettings(
            digestNotificationsEnabled = false,
            digestIntervalHours = 4,
            dataRetentionDays = 30,
            isDeveloperModeEnabled = false
        )
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
