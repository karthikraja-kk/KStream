package com.kstream.tv.ui.home.presenter

import android.util.DisplayMetrics

/**
 * Shared adaptive sizing for rail tiles on the home screen.
 *
 * Every tile presenter that participates in a rail (movie poster cards,
 * the "See more" terminator tile, future hero/featured cards) needs the
 * exact same poster footprint so the row scrolls uniformly and focus
 * scaling reads consistently.
 *
 * The poster size is derived from the rails area height (1.0 - preview
 * fraction) so it scales with the TV's resolution. The section title
 * label is now drawn _inside_ the preview pane (overlapping its bottom
 * fade), so the rails area is the full 40% — no subtraction needed.
 */
object RailCardSizing {

    /** Mirror of activity_main.xml `preview_bottom` guideline. */
    private const val RAILS_HEIGHT_FRACTION = 0.40f

    /**
     * Chrome subtracted from rails area before deriving poster height:
     *   inter-row gap  (~7dp,  verticalGridView itemSpacing)
     * + next-rail peek (~70dp, ~1/3 of rail-2 poster visible so the user
     *                          clearly sees a second section exists).
     * Per-row headers are suppressed (sticky label replaces them).
     */
    private const val CHROME_DP = 7f + 70f

    /** Portrait poster aspect (120dp wide / 180dp tall = 2:3). */
    private const val POSTER_ASPECT_W_OVER_H = 120f / 180f

    /** Sanity clamps so weird display metrics can't produce comical tiles. */
    private const val MIN_POSTER_DP = 100f
    private const val MAX_POSTER_DP = 260f

    data class PosterSize(val widthPx: Int, val heightPx: Int)

    /** Compute the poster footprint (in pixels) for the current display. */
    fun computePosterSize(dm: DisplayMetrics): PosterSize {
        val screenHeightDp = dm.heightPixels / dm.density
        val railsHeightDp = screenHeightDp * RAILS_HEIGHT_FRACTION
        val posterHeightDp = (railsHeightDp - CHROME_DP)
            .coerceIn(MIN_POSTER_DP, MAX_POSTER_DP)
        val posterWidthDp = posterHeightDp * POSTER_ASPECT_W_OVER_H
        return PosterSize(
            widthPx = (posterWidthDp * dm.density).toInt(),
            heightPx = (posterHeightDp * dm.density).toInt()
        )
    }
}
