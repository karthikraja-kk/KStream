package com.kstream.tv.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.kstream.core.model.Movie

/**
 * Final-fallback artwork for the home spotlight.
 *
 * Renders the first 1–2 letters of the movie title in heavy white type,
 * centered on a vertical black → grey gradient. No bitmap is allocated —
 * everything is drawn from primitives, so it's safe to use as a placeholder
 * inside an ImageView even on LOW-tier Fire TV.
 *
 * Use [forMovie] to derive initials from a [Movie.movieName].
 */
class InitialsDrawable(private val initials: String) : Drawable() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        bgPaint.shader = LinearGradient(
            0f, bounds.top.toFloat(),
            0f, bounds.bottom.toFloat(),
            Color.parseColor("#000000"),
            Color.parseColor("#3A3F4A"),
            Shader.TileMode.CLAMP
        )
        // Scale text size with the bounds — initials should read large even
        // in the portrait resting state (120dp) and dominate the spotlight
        // landscape state (600×340).
        textPaint.textSize = bounds.height() * 0.42f
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, bgPaint)
        val cx = bounds.exactCenterX()
        val baseline = bounds.exactCenterY() -
            (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initials, cx, baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        fun forMovie(movie: Movie): InitialsDrawable {
            val name = movie.movieName.trim()
            val initials = when {
                name.isEmpty() -> "?"
                else -> {
                    val tokens = name.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    when {
                        tokens.size >= 2 ->
                            "${tokens[0].first().uppercaseChar()}${tokens[1].first().uppercaseChar()}"
                        tokens[0].length >= 2 ->
                            tokens[0].take(2).uppercase()
                        else ->
                            tokens[0].first().uppercaseChar().toString()
                    }
                }
            }
            return InitialsDrawable(initials)
        }
    }
}
