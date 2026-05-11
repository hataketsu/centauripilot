package com.hataketsu.centauripilot.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2C037),
    secondary = Color(0xFF4FC3F7),
    background = Color(0xFF0A0F1A),
    surface = Color(0xFF14202F),
    onPrimary = Color(0xFF000000),
    onBackground = Color(0xFFE6EAF2),
    onSurface = Color(0xFFE6EAF2),
    error = Color(0xFFFF6B6B)
)

@Composable
fun CentauriPilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}
