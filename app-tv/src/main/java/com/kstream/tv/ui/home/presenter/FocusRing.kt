package com.kstream.tv.ui.home.presenter

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.kstream.tv.R

/**
 * Builds the brand-gradient focus ring used on home rail tiles.
 *
 * Stroke = 2dp, inset = -2dp (drawn 2dp outside the tile so the artwork is
 * never covered), corner radius = 18dp (matches the 16dp tile + 2dp outset).
 */
object FocusRing {
    // Stroke = 3dp, corner = 22dp (= 16dp poster corner + 6dp ring offset from
    // poster edge). Inset = 0 — the ring paints at the view edge of the
    // padded card_root, which is card_focus_padding (8dp) outside the poster.
    private const val STROKE_DP = 3f
    private const val INSET_DP = 0f
    private const val CORNER_DP = 22f

    fun build(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val warm = ContextCompat.getColor(context, R.color.accent_warm)
        val mid = ContextCompat.getColor(context, R.color.accent_primary)
        val cool = ContextCompat.getColor(context, R.color.accent_cool)
        return GradientStrokeDrawable(
            strokeWidthPx = STROKE_DP * density,
            cornerRadiusPx = CORNER_DP * density,
            insetPx = INSET_DP * density,
            colors = intArrayOf(warm, mid, cool)
        )
    }
}
