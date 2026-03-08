package com.javohirmx.notifyr.domain.rules

import com.javohirmx.notifyr.domain.model.AppRule
import com.javohirmx.notifyr.domain.model.AppRuleType
import com.javohirmx.notifyr.domain.model.NotificationData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStatusDropPolicy @Inject constructor(
    private val detector: SyncStatusNotificationDetector
) {
    fun shouldDrop(notification: NotificationData, appRule: AppRule?): Boolean {
        if (appRule != null && appRule.isEnabled) {
            when (appRule.ruleType) {
                AppRuleType.NEVER_DROP_SYNC_STATUS -> return false
                AppRuleType.ALWAYS_DROP_SYNC_STATUS -> {
                    return detector.isSyncStatusNotification(
                        notification = notification,
                        additionalPhrases = appRule.syncStatusPhrases
                    )
                }
                else -> Unit
            }
        }

        return detector.isSyncStatusNotification(notification)
    }
}
