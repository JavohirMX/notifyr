package com.javohirmx.notifyr.utils

import android.content.Context
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

object AppIconUtils {
    @Composable
    fun rememberAppIconPainter(context: Context, packageName: String, sizeDp: Dp = 24.dp): Painter? {
        val packageManager = context.packageManager
        // Convert Dp to pixels with proper density scaling for crisp icons
        val density = androidx.compose.ui.platform.LocalDensity.current
        val sizePx = with(density) { (sizeDp * 2f).roundToPx() } // 2x for high quality
        
        return remember(packageName, sizePx) {
            try {
                // Try to get the actual app icon
                val drawable = packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(width = sizePx, height = sizePx, config = android.graphics.Bitmap.Config.ARGB_8888)
                BitmapPainter(bitmap.asImageBitmap())
            } catch (e: Exception) {
                // App might be uninstalled or inaccessible, use fallback
                android.util.Log.d("AppIconUtils", "Failed to load icon for $packageName: ${e.message}")
                try {
                    val fallback = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                        ?: ContextCompat.getDrawable(context, android.R.drawable.ic_menu_info_details)
                    val bitmap = fallback?.toBitmap(width = sizePx, height = sizePx, config = android.graphics.Bitmap.Config.ARGB_8888)
                    if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else null
                } catch (fallbackError: Exception) {
                    // Even fallback failed, return null and UI will handle it
                    null
                }
            }
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


