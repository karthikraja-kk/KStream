package com.kstream.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a smooth scale animation when the element receives TV focus.
 * Use on movie tiles, cards, buttons, and any interactive TV element.
 */
fun Modifier.tvFocusScale(
    focusedScale: Float = OttConstants.FocusScaleFactor,
    animDurationMs: Int = 200
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = tween(durationMillis = animDurationMs),
        label = "focusScale"
    )

    this
        .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * Applies an elevation/shadow glow effect on focus.
 */
fun Modifier.tvFocusGlow(
    focusedElevation: Dp = 12.dp,
    unfocusedElevation: Dp = 0.dp,
    shape: RoundedCornerShape = RoundedCornerShape(OttConstants.TileCornerRadius)
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val elevation by animateFloatAsState(
        targetValue = if (isFocused) focusedElevation.value else unfocusedElevation.value,
        animationSpec = tween(durationMillis = 200),
        label = "focusGlow"
    )

    this
        .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
        .shadow(elevation = Dp(elevation), shape = shape)
}

/**
 * Applies a white border on focus — the Netflix/Disney+ standard focus ring.
 * White is universally visible on dark backgrounds and red fills.
 */
fun Modifier.tvFocusBorder(
    color: Color = OttConstants.FocusBorderColor,
    width: Dp = OttConstants.FocusBorderWidth,
    shape: RoundedCornerShape = RoundedCornerShape(OttConstants.TileCornerRadius)
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    this
        .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
        .then(
            if (isFocused) Modifier.border(width, color, shape)
            else Modifier
        )
}
