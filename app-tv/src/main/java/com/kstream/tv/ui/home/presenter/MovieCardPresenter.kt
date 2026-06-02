package com.kstream.tv.ui.home.presenter

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.kstream.core.model.Movie
import com.kstream.tv.R

/**
 * Minimal Leanback [Presenter] that renders a [Movie] as an [ImageCardView].
 *
 * P4 implementation. P6 will replace the inner view with a custom card
 * supporting tier-gated focus glow, watch-progress overlay, and TMDb logos.
 *
 * Card dimensions read from `dimens.xml` so a single tweak resizes the grid.
 */
class MovieCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val ctx = ContextThemeWrapper(parent.context, R.style.Theme_KStreamTv)
        val card = ImageCardView(ctx).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            val w = resources.getDimensionPixelSize(R.dimen.card_poster_width)
            val h = resources.getDimensionPixelSize(R.dimen.card_poster_height)
            setMainImageDimensions(w, h)
            setMainImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as? Movie ?: return
        val card = viewHolder.view as ImageCardView
        card.titleText = movie.movieName
        val metaParts = listOfNotNull(
            movie.year.takeIf { it > 0 }?.toString(),
            movie.duration.takeIf { it.isNotBlank() },
            movie.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
        )
        card.contentText = metaParts.joinToString(" · ")

        Glide.with(card.context)
            .load(movie.posterUrl.takeIf { it.isNotBlank() })
            .placeholder(R.color.bg_surface)
            .error(R.color.bg_surface)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(card.mainImageView!!)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val card = viewHolder.view as ImageCardView
        card.mainImageView?.let { Glide.with(card.context).clear(it) }
        card.badgeImage = null
        card.mainImage = null
    }
}
