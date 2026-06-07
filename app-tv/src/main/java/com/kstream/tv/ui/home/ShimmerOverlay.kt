package com.kstream.tv.ui.home

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.facebook.shimmer.ShimmerFrameLayout
import com.kstream.tv.R
import com.kstream.tv.ui.home.presenter.RailCardSizing

/**
 * Tiny helper that overlays a full-screen shimmer placeholder on top of
 * MainActivity while HomeRowsFragment is waiting for its first non-empty
 * rails emission (and, when possible, for the splash-time prewarm to
 * finish).
 *
 * The overlay is attached to the activity's decor view so it covers EVERY
 * sibling (top bar, side nav, rows) and intercepts no input — focus stays
 * on whatever was about to become focusable.
 */
object ShimmerOverlay {

    private const val FADE_MS = 220L
    private const val SHIMMER_CARD_TAG = "shimmer_card"

    fun show(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewById<View>(R.id.home_shimmer_root) != null) return
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.view_home_shimmer, decor, false)
        // Resize every card-shaped placeholder so the shimmer silhouette
        // matches the real adaptive poster footprint MovieCardPresenter
        // computes. Without this, posters appear at 120×180 here but the
        // real UI lands at a different (smaller / larger) size, causing
        // a visible reflow on cross-fade.
        val size = RailCardSizing.computePosterSize(activity.resources.displayMetrics)
        forEachTaggedCard(view) { card ->
            card.layoutParams = card.layoutParams.apply {
                width = size.widthPx
                height = size.heightPx
            }
        }
        (view as? ShimmerFrameLayout)?.startShimmer()
        decor.addView(view)
    }

    fun hide(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val view = decor.findViewById<View>(R.id.home_shimmer_root) ?: return
        (view as? ShimmerFrameLayout)?.stopShimmer()
        view.animate()
            .alpha(0f)
            .setDuration(FADE_MS)
            .withEndAction { (view.parent as? ViewGroup)?.removeView(view) }
            .start()
    }

    private fun forEachTaggedCard(root: View, action: (View) -> Unit) {
        if (root.tag == SHIMMER_CARD_TAG) action(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                forEachTaggedCard(root.getChildAt(i), action)
            }
        }
    }
}
