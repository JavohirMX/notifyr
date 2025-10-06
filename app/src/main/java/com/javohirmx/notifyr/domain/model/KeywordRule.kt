package com.javohirmx.notifyr.domain.model

data class KeywordRule(
    val keyword: String,
    val importance: NotificationImportance,
    val isEnabled: Boolean = true,
    val isRegex: Boolean = false
)
