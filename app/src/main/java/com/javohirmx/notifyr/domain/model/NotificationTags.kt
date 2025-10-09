package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationTags(
    val priority: Priority = Priority.NORMAL,
    val contexts: Set<NotificationContext> = emptySet(),
    val timeSensitivity: TimeSensitivity = TimeSensitivity.LATER,
    val actionType: ActionType = ActionType.FYI
)

@Serializable
enum class Priority(val value: Int) {
    CRITICAL(3),    // Banking alerts, emergency contacts, security
    IMPORTANT(2),   // Time-sensitive messages, meetings
    NORMAL(1),      // General notifications
    LOW(0);         // Background, can wait

    companion object {
        fun fromValue(value: Int): Priority {
            return values().find { it.value == value } ?: NORMAL
        }
        
        // Convert from old NotificationImportance
        fun fromImportance(importance: NotificationImportance): Priority {
            return when (importance) {
                NotificationImportance.URGENT -> IMPORTANT
                NotificationImportance.NORMAL -> NORMAL
                NotificationImportance.IGNORE -> LOW
            }
        }
    }
}

@Serializable
enum class NotificationContext {
    WORK,           // Work-related apps and messages
    PERSONAL,       // Personal messages and apps
    FINANCIAL,      // Banking, payments, transactions
    SOCIAL,         // Social media
    SHOPPING,       // E-commerce, deliveries
    TRAVEL,         // Maps, travel bookings
    HEALTH,         // Health and fitness apps
    ENTERTAINMENT,  // Games, streaming
    SYSTEM,         // System notifications
    EMERGENCY;      // Emergency contacts and alerts
    
    fun getDisplayName(): String {
        return when (this) {
            WORK -> "Work"
            PERSONAL -> "Personal"
            FINANCIAL -> "Financial"
            SOCIAL -> "Social"
            SHOPPING -> "Shopping"
            TRAVEL -> "Travel"
            HEALTH -> "Health"
            ENTERTAINMENT -> "Entertainment"
            SYSTEM -> "System"
            EMERGENCY -> "Emergency"
        }
    }
}

@Serializable
enum class TimeSensitivity {
    IMMEDIATE,      // Show now with sound/vibration
    SOON,          // Show quietly within an hour
    LATER,         // Add to digest, review later today
    WHENEVER;      // Archive, check when convenient
    
    fun getDisplayName(): String {
        return when (this) {
            IMMEDIATE -> "Immediate"
            SOON -> "Soon"
            LATER -> "Later"
            WHENEVER -> "Whenever"
        }
    }
}

@Serializable
enum class ActionType {
    NEEDS_RESPONSE,     // Requires user action/response
    FYI,               // Informational only
    TRANSACTIONAL,     // Receipts, confirmations
    CONVERSATIONAL,    // Part of ongoing chat
    AUTOMATED;         // System/bot messages
    
    fun getDisplayName(): String {
        return when (this) {
            NEEDS_RESPONSE -> "Needs Response"
            FYI -> "FYI"
            TRANSACTIONAL -> "Transactional"
            CONVERSATIONAL -> "Conversational"
            AUTOMATED -> "Automated"
        }
    }
}

// Helper to determine if notification should show immediately
fun NotificationTags.shouldShowImmediately(): Boolean {
    return priority == Priority.CRITICAL || 
           (priority == Priority.IMPORTANT && timeSensitivity == TimeSensitivity.IMMEDIATE)
}

// Helper to determine if notification needs user attention
fun NotificationTags.needsAttention(): Boolean {
    return actionType == ActionType.NEEDS_RESPONSE || 
           priority == Priority.CRITICAL ||
           priority == Priority.IMPORTANT
}

