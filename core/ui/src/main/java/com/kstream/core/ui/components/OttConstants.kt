package com.kstream.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object OttConstants {
    // Brand colors
    val BrandRed = Color(0xFFE50914)
    val BrandRedFocused = Color(0xFFFF1A1A)
    val BrandRedPressed = Color(0xFFCC0000)  // Darker — mobile press / TV active
    val FocusBorderColor = Color.White       // White ring on focused TV components
    val DarkBackground = Color(0xFF141414)
    val SurfaceVariant = Color(0xFF2A2A2A)
    val TextSecondary = Color(0xFFB3B3B3)

    // Gradient colors for poster overlays
    val GradientScrimColors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.3f),
        Color.Black.copy(alpha = 0.7f)
    )

    // Tile sizes
    val TileSizeTv = 160.dp
    val TileSizeMobile = 140.dp
    val TileCornerRadius = 8.dp

    // Focus
    val FocusScaleFactor = 1.05f
    val FocusBorderWidth = 3.dp

    // Spacing
    val RailSpacingTv = 16.dp
    val ContentPaddingTv = 48.dp
}
