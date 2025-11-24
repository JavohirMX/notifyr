package com.javohirmx.notifyr.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TemporaryStatus {
    DONT_IGNORE,  // Don't ignore - let through with normal processing
    IGNORE,       // Ignore notifications
    URGENT        // Mark as urgent
}

@Serializable
data class TemporaryAppStatus(
    val packageName: String,
    val appName: String,
    val status: TemporaryStatus,
    val expiresAt: Long,  // Timestamp in milliseconds
    val createdAt: Long  // Timestamp in milliseconds
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= expiresAt
    }
    
    fun getRemainingMinutes(): Long {
        val remaining = expiresAt - System.currentTimeMillis()
        return if (remaining > 0) {
            (remaining / 60_000) + 1 // Round up to next minute
        } else {
            0
        }
    }
}

