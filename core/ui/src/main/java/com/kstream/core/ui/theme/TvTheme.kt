package com.kstream.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColorScheme = tvDarkColorScheme(
    primary = Color(0xFFE50914),
    secondary = Color(0xFF564D4D),
    tertiary = Color(0xFFB3B3B3),
    background = Color(0xFF141414),
    surface = Color(0xFF141414),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB3B3B3),
    inverseSurface = Color(0xFF333333),
    inverseOnSurface = Color.White,
    error = Color(0xFFCF6679),
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun KStreamTvTheme(
    content: @Composable () -> Unit
) {
    TvMaterialTheme(
        colorScheme = TvDarkColorScheme,
        typography = TvTypography,
        content = content
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
val TvTypography = androidx.tv.material3.Typography()
