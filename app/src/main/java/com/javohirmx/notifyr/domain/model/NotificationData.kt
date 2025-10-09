package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationData(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val category: String?,
    val importance: NotificationImportance,
    val timestamp: Long,
    val isRead: Boolean = false
)
