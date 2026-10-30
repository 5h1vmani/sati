package com.padakanti.sati.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every text-on-background pair below is checked by tools/contrast.py.
// Change a colour here, change it there, and run the script.

/** Sati brand colour. The "on" card uses it in both light and dark themes. */
val Brand = Color(0xFFA55E3B)

/** Text and icons on [Brand]. Pure white keeps contrast at 4.92:1. */
val OnBrand = Color(0xFFFFFFFF)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = OnBrand,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3B1607),
    secondaryContainer = Color(0xFFF2DED5),
    onSecondaryContainer = Color(0xFF2C1A12),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF2E0D8),
    onSurfaceVariant = Color(0xFF53443D),
    surfaceContainer = Color(0xFFF7EBE5),
    surfaceContainerHigh = Color(0xFFF2E4DD),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD8C2BA),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF0A884),
    onPrimary = Color(0xFF4A1F0B),
    primaryContainer = Color(0xFF7E4227),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondaryContainer = Color(0xFF5A4038),
    onSecondaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF53443D),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceContainer = Color(0xFF261D1A),
    surfaceContainerHigh = Color(0xFF312825),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF53443D),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SatiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
