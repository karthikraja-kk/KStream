package com.kstream.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE50914), // Netflix Red
    secondary = Color(0xFF564D4D),
    tertiary = Color(0xFFB3B3B3),
    background = Color(0xFF141414),
    surface = Color(0xFF141414),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
@Suppress("UNUSED_PARAMETER")
fun KStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For now, only Dark mode as it's an OTT app
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Typography placeholder
val Typography = androidx.compose.material3.Typography()
