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
import com.kstream.core.ui.LocalLiteMode

/**
 * Applies a smooth scale animation when the element receives TV focus.
 * In Lite Mode: no scale animation (returns unmodified).
 */
fun Modifier.tvFocusScale(
    focusedScale: Float = OttConstants.FocusScaleFactor,
    animDurationMs: Int = 200
): Modifier = composed {
    val liteMode = LocalLiteMode.current
    if (liteMode) return@composed this

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
 * In Lite Mode: no glow (returns unmodified).
 */
fun Modifier.tvFocusGlow(
    focusedElevation: Dp = 12.dp,
    unfocusedElevation: Dp = 0.dp,
    shape: RoundedCornerShape = RoundedCornerShape(OttConstants.TileCornerRadius)
): Modifier = composed {
    val liteMode = LocalLiteMode.current
    if (liteMode) return@composed this

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
 * Applies a border on focus — solid 2dp accent border.
 * Works the same in both Normal and Lite mode (already lightweight).
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
