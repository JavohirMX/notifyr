package com.javohirmx.notifyr.domain.util

/**
 * Centralized email app detection utility.
 * Single source of truth for determining if a package is an email app.
 */
object EmailAppDetector {
    /**
     * Set of known email app package names
     */
    private val emailApps = setOf(
        "com.google.android.apps.gmail",
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "com.fsck.k9",
        "com.oneplus.email"
    )
    
    /**
     * Check if a package name is an email app
     */
    fun isEmailApp(packageName: String): Boolean {
        return emailApps.contains(packageName)
    }
    
    /**
     * Get the deduplication window for an email app (in milliseconds)
     */
    fun getDedupWindow(packageName: String): Long {
        return if (isEmailApp(packageName)) {
            120_000L // 2 minutes for email apps
        } else {
            30_000L // 30 seconds for other apps
        }
    }
}

