package com.javohirmx.notifyr.domain.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class ExportDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `settings survive a round trip`() {
        val original = ExportData(
            settings = ExportSettings(
                notificationRetentionDays = 30,
                digestMode = DigestMode.TIME_BASED,
                digestCustomTimes = listOf(TimeOfDay(9, 0), TimeOfDay(18, 30)),
                focusMode = FocusMode.WORK,
                focusAutoSwitch = true,
                focusWorkHours = TimeRange(9, 0, 17, 0),
                focusWorkDays = setOf(2, 3, 4)
            )
        )

        val restored = json.decodeFromString<ExportData>(json.encodeToString(original))

        assertThat(restored.version).isEqualTo(ExportData.CURRENT_VERSION)
        assertThat(restored.settings).isEqualTo(original.settings)
    }

    @Test
    fun `version 1 files still decode`() {
        val legacy = """
            {
              "version": 1,
              "appName": "Notifyr",
              "appRules": [],
              "keywordRules": [],
              "notifications": [],
              "settings": {
                "digestNotificationsEnabled": true,
                "digestIntervalHours": 6,
                "dataRetentionDays": 30,
                "isDeveloperModeEnabled": false
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ExportData>(legacy)

        assertThat(decoded.version).isEqualTo(1)
        // Unknown legacy keys are ignored; the importer skips settings for version 1.
        assertThat(decoded.settings).isNotNull()
    }
}
