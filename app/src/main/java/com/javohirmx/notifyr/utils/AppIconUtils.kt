package com.javohirmx.notifyr.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.javohirmx.notifyr.ui.components.AppIconPlaceholder
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

object AppIconUtils {
    // Cache for app icons - using package name + size as key
    private val iconCache = object : android.util.LruCache<String, Bitmap>(50) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024 // Size in KB
        }
    }
    
    private val cacheLock = ReentrantReadWriteLock()
    
    /**
     * Get app icon as Bitmap for use in notifications (non-Composable)
     * Uses improved fallback mechanism with colored initial icon and caching
     * 
     * @param context Android context
     * @param packageName Package name of the app
     * @param appName App name for fallback icon generation
     * @param sizePx Size in pixels (typically 64dp for notification large icons)
     * @return Bitmap of app icon or fallback icon, never null
     */
    fun getAppIconBitmap(
        context: Context,
        packageName: String,
        appName: String,
        sizePx: Int = 128
    ): Bitmap {
        // Check cache first
        val cacheKey = "${packageName}_${sizePx}"
        cacheLock.read {
            iconCache.get(cacheKey)?.let { return it }
        }
        
        val bitmap = getAppIconBitmapInternal(context, packageName, appName, sizePx)
        
        // Cache the result
        cacheLock.write {
            iconCache.put(cacheKey, bitmap)
        }
        
        return bitmap
    }
    
    /**
     * Get app icon from StatusBarNotification if available, otherwise fall back to package icon
     * Note: Currently falls back to package icon lookup. Notification icon extraction can be added later.
     */
    fun getAppIconBitmapFromNotification(
        context: Context,
        sbn: StatusBarNotification?,
        packageName: String,
        appName: String,
        sizePx: Int = 128
    ): Bitmap {
        // For now, use the regular app icon lookup with caching
        // Future enhancement: extract icon directly from notification.largeIcon
        return getAppIconBitmap(context, packageName, appName, sizePx)
    }
    
    /**
     * Internal method to get app icon without caching (used by caching wrapper)
     */
    private fun getAppIconBitmapInternal(
        context: Context,
        packageName: String,
        appName: String,
        sizePx: Int
    ): Bitmap {
        val packageManager = context.packageManager
        
        // Validate package name
        if (packageName.isBlank()) {
            android.util.Log.w("AppIconUtils", "Blank package name provided")
            return createFallbackIconBitmap(appName, sizePx)
        }
        
        // Validate package exists before trying to load
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            // Package doesn't exist, remove from cache
            removeFromCache(packageName)
            android.util.Log.d("AppIconUtils", "Package not found: $packageName")
            return createFallbackIconBitmap(appName, sizePx)
        }
        
        // Try to get the actual app icon with retry
        var lastException: Exception? = null
        repeat(2) { attempt ->
            try {
                val drawable = packageManager.getApplicationIcon(packageName)
                return drawable.toBitmap(width = sizePx, height = sizePx, config = Bitmap.Config.ARGB_8888)
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                lastException = e
                removeFromCache(packageName)
                android.util.Log.d("AppIconUtils", "App not found: $packageName")
                // Don't retry for NameNotFoundException
                return@repeat
            } catch (e: SecurityException) {
                lastException = e
                android.util.Log.w("AppIconUtils", "Security exception loading icon for $packageName: ${e.message}")
                // Don't retry for security exceptions
                return@repeat
            } catch (e: Exception) {
                lastException = e
                android.util.Log.d("AppIconUtils", "Failed to load icon for $packageName (attempt ${attempt + 1}): ${e.message}")
                if (attempt == 0) {
                    // Wait a bit before retry
                    try {
                        Thread.sleep(50)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
        }
        
        // Fallback 1: Try system default app icon
        try {
            val fallback = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            if (fallback != null) {
                return fallback.toBitmap(width = sizePx, height = sizePx, config = Bitmap.Config.ARGB_8888)
            }
        } catch (e: Exception) {
            android.util.Log.d("AppIconUtils", "Failed to load system fallback icon: ${e.message}")
        }
        
        // Fallback 2: Generate colored circle with app initial (best fallback)
        return createFallbackIconBitmap(appName, sizePx)
    }
    
    /**
     * Clear the icon cache (useful when apps are uninstalled or updated)
     */
    fun clearCache() {
        cacheLock.write {
            iconCache.evictAll()
        }
    }
    
    /**
     * Remove a specific package from cache (useful when app is updated)
     */
    fun removeFromCache(packageName: String) {
        cacheLock.write {
            val keysToRemove = iconCache.snapshot().keys.filter { it.startsWith("${packageName}_") }
            keysToRemove.forEach { iconCache.remove(it) }
        }
    }
    
    /**
     * Create a fallback icon bitmap with colored circle and app initial
     * Similar to AppIconPlaceholder but as a Bitmap for notifications
     */
    private fun createFallbackIconBitmap(appName: String, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Get color for app based on name hash
        val backgroundColor = getColorForAppName(appName)
        
        // Draw circle background
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = backgroundColor
            style = Paint.Style.FILL
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        
        // Draw app initial
        val initial = appName.firstOrNull()?.uppercase() ?: "?"
        val textPaint = Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.WHITE
            textSize = sizePx * 0.5f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val textBounds = Rect()
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        val textY = radius + (textBounds.height() / 2f)
        
        canvas.drawText(initial, radius, textY, textPaint)
        
        return bitmap
    }
    
    /**
     * Get consistent color for app name (same logic as AppIconPlaceholder)
     */
    private fun getColorForAppName(appName: String): Int {
        val hash = appName.hashCode()
        val colors = listOf(
            0xFF6200EA.toInt(), // Purple
            0xFF00BCD4.toInt(), // Cyan
            0xFF4CAF50.toInt(), // Green
            0xFFFF9800.toInt(), // Orange
            0xFFF44336.toInt(), // Red
            0xFF2196F3.toInt(), // Blue
            0xFFE91E63.toInt(), // Pink
            0xFF009688.toInt(), // Teal
        )
        return colors[hash.mod(colors.size)]
    }
    @Composable
    fun rememberAppIconPainter(context: Context, packageName: String, sizeDp: Dp = 24.dp): Painter? {
        val packageManager = context.packageManager
        // Convert Dp to pixels with proper density scaling for crisp icons
        val density = androidx.compose.ui.platform.LocalDensity.current
        val sizePx = with(density) { (sizeDp * 2f).roundToPx() } // 2x for high quality
        
        return remember(packageName, sizePx) {
            // Validate package name first
            if (packageName.isBlank()) {
                android.util.Log.w("AppIconUtils", "Blank package name provided")
                return@remember null
            }
            
            // Check cache first (for Composable version)
            val cacheKey = "${packageName}_${sizePx}"
            cacheLock.read {
                iconCache.get(cacheKey)?.let { cachedBitmap ->
                    return@remember BitmapPainter(cachedBitmap.asImageBitmap())
                }
            }
            
            // Try to load icon with retry logic
            var lastException: Exception? = null
            repeat(2) { attempt ->
                try {
                    // Validate package exists before trying to load
                    try {
                        packageManager.getPackageInfo(packageName, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        // Package doesn't exist, remove from cache if present
                        removeFromCache(packageName)
                        android.util.Log.d("AppIconUtils", "Package not found: $packageName")
                        return@remember null
                    }
                    
                    // Try to get the actual app icon
                    val drawable = packageManager.getApplicationIcon(packageName)
                    val bitmap = drawable.toBitmap(width = sizePx, height = sizePx, config = android.graphics.Bitmap.Config.ARGB_8888)
                    
                    // Cache the successful result
                    cacheLock.write {
                        iconCache.put(cacheKey, bitmap)
                    }
                    
                    return@remember BitmapPainter(bitmap.asImageBitmap())
                } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                    lastException = e
                    // Package not found, remove from cache
                    removeFromCache(packageName)
                    android.util.Log.d("AppIconUtils", "App not found: $packageName (attempt ${attempt + 1})")
                    // Don't retry for NameNotFoundException
                    return@remember null
                } catch (e: SecurityException) {
                    lastException = e
                    android.util.Log.w("AppIconUtils", "Security exception loading icon for $packageName: ${e.message}")
                    // Don't retry for security exceptions
                    return@remember null
                } catch (e: Exception) {
                    lastException = e
                    android.util.Log.d("AppIconUtils", "Failed to load icon for $packageName (attempt ${attempt + 1}): ${e.message}")
                    if (attempt == 0) {
                        // Wait a bit before retry (only for transient errors)
                        try {
                            Thread.sleep(50)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            }
            
            // All attempts failed, try system fallback
            try {
                val fallback = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                    ?: ContextCompat.getDrawable(context, android.R.drawable.ic_menu_info_details)
                val bitmap = fallback?.toBitmap(width = sizePx, height = sizePx, config = android.graphics.Bitmap.Config.ARGB_8888)
                if (bitmap != null) {
                    return@remember BitmapPainter(bitmap.asImageBitmap())
                }
            } catch (fallbackError: Exception) {
                android.util.Log.w("AppIconUtils", "Fallback icon also failed: ${fallbackError.message}")
            }
            
            // Return null - UI will show placeholder
            null
        }
    }
    
    /**
     * Composable that displays app icon with automatic fallback to placeholder
     */
    @Composable
    fun AppIconOrPlaceholder(
        context: Context,
        packageName: String,
        appName: String,
        size: Dp = 24.dp,
        modifier: Modifier = Modifier
    ) {
        val painter = rememberAppIconPainter(context, packageName, size)
        
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = "App icon for $appName",
                modifier = modifier
            )
        } else {
            // Show placeholder with app initial
            AppIconPlaceholder(
                appName = appName,
                size = size,
                modifier = modifier
            )
        }
    }
}


