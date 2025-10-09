package com.javohirmx.notifyr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppIconPlaceholder(
    appName: String,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val initial = appName.firstOrNull()?.uppercase() ?: "?"
    val backgroundColor = getColorForApp(appName)
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = (size.value * 0.5).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun getColorForApp(appName: String): Color {
    // Generate consistent color based on app name hash
    val hash = appName.hashCode()
    val hue = (hash % 360).toFloat()
    
    // Predefined nice-looking colors
    val colors = listOf(
        Color(0xFF6200EA), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFFF44336), // Red
        Color(0xFF2196F3), // Blue
        Color(0xFFE91E63), // Pink
        Color(0xFF009688), // Teal
    )
    
    return colors[hash.mod(colors.size)]
}

