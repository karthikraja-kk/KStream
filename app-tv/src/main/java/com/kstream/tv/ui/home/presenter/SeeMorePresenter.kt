package com.kstream.tv.ui.home.presenter

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.Presenter
import com.kstream.tv.R

/**
 * Presenter for [SeeMoreCard]. Renders a tile the same height as a poster so
 * Leanback's HorizontalGridView keeps consistent row alignment, with a
 * centered "See more →" label and gold focus ring.
 */
class SeeMorePresenter : Presenter() {

    var onClick: ((SeeMoreCard) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val ctx = ContextThemeWrapper(parent.context, R.style.Theme_KStreamTv)
        val view = LayoutInflater.from(ctx).inflate(R.layout.card_see_more, parent, false)
        // Match the adaptive poster footprint used by MovieCardPresenter so
        // the See-more tile aligns with neighbouring posters on any screen.
        // The static @dimen/card_poster_* in the layout is just a fallback.
        val size = RailCardSizing.computePosterSize(view.resources.displayMetrics)
        view.findViewById<View>(R.id.see_more_tile)?.let { tile ->
            tile.layoutParams = tile.layoutParams.apply {
                width = size.widthPx
                height = size.heightPx
            }
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val card = item as? SeeMoreCard ?: return
        val v = viewHolder.view
        v.setOnClickListener { onClick?.invoke(card) }
        v.setOnFocusChangeListener { view, hasFocus ->
            // Brand-gradient focus ring on the outer see_more_root (padded
            // by card_focus_padding so the ring sits outside the inner tile).
            view.foreground = if (hasFocus) FocusRing.build(view.context) else null
            view.animate().cancel()
            view.animate().scaleX(if (hasFocus) 1.08f else 1f)
                .scaleY(if (hasFocus) 1.08f else 1f)
                .setDuration(140L).start()
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        viewHolder.view.foreground = null
        viewHolder.view.setOnClickListener(null)
        viewHolder.view.setOnFocusChangeListener(null)
    }
}
