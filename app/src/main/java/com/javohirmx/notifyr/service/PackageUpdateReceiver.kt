package com.javohirmx.notifyr.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.javohirmx.notifyr.utils.AppIconUtils

/**
 * BroadcastReceiver that listens for package updates and clears icon cache
 * This fixes the issue where app icons disappear after app updates
 */
class PackageUpdateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "PackageUpdateReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_ADDED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName != null) {
                    Log.d(TAG, "Package updated/added: $packageName, clearing icon cache")
                    // Clear icon cache for this package
                    AppIconUtils.removeFromCache(packageName)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName != null && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    // Only clear cache if package was actually removed (not replaced)
                    Log.d(TAG, "Package removed: $packageName, clearing icon cache")
                    AppIconUtils.removeFromCache(packageName)
                }
            }
        }
    }
}

