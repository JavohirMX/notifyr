package com.javohirmx.notifyr.data.service

import android.content.Context
import android.net.Uri
import com.javohirmx.notifyr.data.repository.AppRulesRepository
import com.javohirmx.notifyr.data.repository.DigestSettingsRepository
import com.javohirmx.notifyr.data.repository.FocusSettingsRepository
import com.javohirmx.notifyr.data.repository.KeywordRulesRepository
import com.javohirmx.notifyr.data.repository.NotificationRepository
import com.javohirmx.notifyr.data.repository.NotificationRetentionRepository
import com.javohirmx.notifyr.data.security.ExportCrypto
import com.javohirmx.notifyr.data.security.WrongPasswordException
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
        includeSettings: Boolean = true,
        password: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = buildExportData(exportType, includeSettings)
            val payload = encodePayload(json.encodeToString(exportData), password)
            val fileName = generateFileName(exportType)
            val file = File(context.getExternalFilesDir(null), fileName)
            
            FileOutputStream(file).use { output ->
                output.write(payload)
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
        includeSettings: Boolean = true,
        password: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = buildExportData(exportType, includeSettings)
            val payload = encodePayload(json.encodeToString(exportData), password)
            
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(payload)
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

    /** Encrypts the JSON when a password is supplied; otherwise writes plain JSON. */
    private fun encodePayload(jsonString: String, password: String?): ByteArray =
        if (password.isNullOrEmpty()) {
            jsonString.toByteArray(Charsets.UTF_8)
        } else {
            ExportCrypto.encrypt(jsonString.toByteArray(Charsets.UTF_8), password)
        }

    private fun decodePayload(payload: ByteArray, password: String?): String {
        if (!ExportCrypto.isEncrypted(payload)) {
            return payload.toString(Charsets.UTF_8)
        }
        if (password.isNullOrEmpty()) {
            throw IOException("This backup is encrypted. Enter its password to import it.")
        }

        return try {
            ExportCrypto.decrypt(payload, password).toString(Charsets.UTF_8)
        } catch (e: WrongPasswordException) {
            throw IOException(e.message ?: "Incorrect backup password")
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
        replaceExisting: Boolean = false,
        password: String? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val payload = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: throw IOException("Could not read input stream")
            
            val exportData = json.decodeFromString<ExportData>(decodePayload(payload, password))
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
        replaceExisting: Boolean = false,
        password: String? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = json.decodeFromString<ExportData>(
                decodePayload(file.readBytes(), password)
            )
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
            val payload = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: return false

            // An encrypted backup cannot be checked without its password; accept it here and
            // let the import report a missing or wrong password with a specific message.
            if (ExportCrypto.isEncrypted(payload)) return true

            val exportData = json.decodeFromString<ExportData>(
                payload.toString(Charsets.UTF_8)
            )
            
            // Basic validation
            exportData.version > 0 && exportData.appName == "Notifyr"
        } catch (e: Exception) {
            false
        }
    }
}
