package com.kstream.tv.ui.home.presenter

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Rounded-rectangle outline drawable painted with a linear-gradient stroke.
 *
 * Used as the focus indicator on home rail tiles so the focused poster gets
 * a thin brand-coloured ring (orange → magenta → purple, top-left → bottom-
 * right) flush around its visible edge. Android's `<stroke>` XML tag only
 * accepts a solid color, hence this small custom drawable.
 *
 * The [insetPx] argument moves the rounded-rect path inward (positive) or
 * outward (negative) from this drawable's bounds. Negative values are used
 * to draw the ring *outside* the tile so it never covers the poster art —
 * the host container must have `clipChildren=false` for that to be visible.
 */
class GradientStrokeDrawable(
    private val strokeWidthPx: Float,
    private val cornerRadiusPx: Float,
    private val insetPx: Float,
    private val colors: IntArray,
    private val positions: FloatArray? = null
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }
    private val pathRect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        // Stroke is centered on the path; offset the path inward by half the
        // stroke so the outer edge of the stroke aligns with insetPx — i.e.
        // a negative inset of N draws the outer edge N px outside the bounds.
        val half = strokeWidthPx / 2f
        pathRect.set(
            bounds.left + insetPx + half,
            bounds.top + insetPx + half,
            bounds.right - insetPx - half,
            bounds.bottom - insetPx - half
        )
        paint.shader = LinearGradient(
            pathRect.left, pathRect.top,
            pathRect.right, pathRect.bottom,
            colors, positions, Shader.TileMode.CLAMP
        )
    }

    override fun draw(canvas: Canvas) {
        if (pathRect.isEmpty) return
        canvas.drawRoundRect(pathRect, cornerRadiusPx, cornerRadiusPx, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
