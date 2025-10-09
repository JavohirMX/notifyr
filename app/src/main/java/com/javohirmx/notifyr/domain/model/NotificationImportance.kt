package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationImportance(val value: Int) {
    IGNORE(0),
    NORMAL(1),
    URGENT(2);

    companion object {
        fun fromValue(value: Int): NotificationImportance {
            return values().find { it.value == value } ?: NORMAL
        }
    }
}
