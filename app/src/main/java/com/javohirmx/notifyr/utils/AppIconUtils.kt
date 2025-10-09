package com.javohirmx.notifyr.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

object AppIconUtils {
    @Composable
    fun rememberAppIconPainter(context: Context, packageName: String, sizePx: Int = 64): Painter? {
        val packageManager = context.packageManager
        return remember(packageName, sizePx) {
            try {
                val drawable = packageManager.getApplicationIcon(packageName)
                val bitmap = drawable.toBitmap(width = sizePx, height = sizePx, config = android.graphics.Bitmap.Config.ARGB_8888)
                BitmapPainter(bitmap.asImageBitmap())
            } catch (e: Exception) {
                val fallback = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                val bitmap = fallback?.toBitmap(width = sizePx, height = sizePx, config = android.graphics.Bitmap.Config.ARGB_8888)
                if (bitmap != null) BitmapPainter(bitmap.asImageBitmap()) else null
            }
        }
    }
}


