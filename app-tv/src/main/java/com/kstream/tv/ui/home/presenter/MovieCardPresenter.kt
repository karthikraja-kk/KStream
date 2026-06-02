package com.kstream.tv.ui.home.presenter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress
import com.kstream.tv.R
import com.kstream.tv.tier.DeviceTier

/**
 * Polished Leanback [Presenter] for a [Movie].
 *
 * Renders `R.layout.card_movie` (a 2:3 rounded poster + title + meta) with:
 *  - Quality badge ("HD" / "4K" / type) overlaid on the top-right.
 *  - Gold watch-progress bar overlaid on the bottom 3dp (only when
 *    [progressMap] contains the movie).
 *  - Tier-gated focus animation:
 *      * LOW : sharp border via `focus_ring_low` (no animation)
 *      * MID : scale 1.06 + `focus_ring_glow`, 150 ms
 *      * HIGH: scale 1.08 + `focus_ring_glow` + slight elevation, 200 ms
 *
 * The presenter is stateless across [Movie] items; per-item progress is read
 * from [progressMap] (call [updateProgress] before swapping rail contents).
 */
class MovieCardPresenter : Presenter() {

    @Volatile
    private var progressMap: Map<String, WatchProgress> = emptyMap()

    fun updateProgress(progress: Map<String, WatchProgress>) {
        progressMap = progress
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val ctx = ContextThemeWrapper(parent.context, R.style.Theme_KStreamTv)
        val inflater = LayoutInflater.from(ctx)
        val view = inflater.inflate(R.layout.card_movie, parent, false)
        val tier = DeviceTier.get(ctx)
        applyFocusBehavior(view, tier)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as? Movie ?: return
        val v = viewHolder.view
        val poster = v.findViewById<ImageView>(R.id.card_poster)
        val title = v.findViewById<TextView>(R.id.card_title)
        val meta = v.findViewById<TextView>(R.id.card_meta)
        val badge = v.findViewById<TextView>(R.id.card_quality_badge)
        val progressBar = v.findViewById<View>(R.id.card_progress)

        title.text = movie.movieName
        val metaParts = listOfNotNull(
            movie.year.takeIf { it > 0 }?.toString(),
            movie.duration.takeIf { it.isNotBlank() },
            movie.rating.takeIf { it.isNotBlank() }?.let { "★ $it" }
        )
        meta.text = metaParts.joinToString("  ·  ")

        val qualityText = qualityLabel(movie.type)
        if (qualityText != null) {
            badge.text = qualityText
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }

        bindProgress(v, progressBar, movie)

        Glide.with(v.context)
            .load(movie.posterUrl.takeIf { it.isNotBlank() })
            .placeholder(R.drawable.card_poster_bg)
            .error(R.drawable.card_poster_bg)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(poster)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val v = viewHolder.view
        val poster = v.findViewById<ImageView>(R.id.card_poster)
        Glide.with(v.context).clear(poster)
        poster.setImageDrawable(null)
    }

    private fun bindProgress(card: View, progressBar: View, movie: Movie) {
        val wp = progressMap[movie.id]
        if (wp == null || wp.completionPercent <= 0f) {
            progressBar.visibility = View.GONE
            return
        }
        progressBar.visibility = View.VISIBLE
        val ratio = (wp.completionPercent / 100f).coerceIn(0f, 1f)
        // Defer width math until we have a measured card width.
        progressBar.post {
            val cardWidth = card.width.coerceAtLeast(
                card.resources.getDimensionPixelSize(R.dimen.card_poster_width)
            )
            val lp = progressBar.layoutParams
            lp.width = (cardWidth * ratio).toInt().coerceAtLeast(1)
            progressBar.layoutParams = lp
        }
    }

    private fun qualityLabel(type: String): String? {
        val t = type.uppercase()
        return when {
            t.contains("4K") || t.contains("UHD") -> "4K"
            t.contains("HD") -> "HD"
            t.isBlank() -> null
            else -> type.take(8).uppercase()
        }
    }

    private fun applyFocusBehavior(view: View, tier: DeviceTier) {
        when (tier) {
            DeviceTier.LOW -> {
                view.background = view.context.getDrawable(R.drawable.focus_ring_low)
                // No animation: just the focus ring state list does the work.
            }
            DeviceTier.MID -> {
                view.background = view.context.getDrawable(R.drawable.focus_ring_glow)
                attachScaleAnimator(view, target = 1.06f, durationMs = 150)
            }
            DeviceTier.HIGH -> {
                view.background = view.context.getDrawable(R.drawable.focus_ring_glow)
                attachScaleAnimator(view, target = 1.08f, durationMs = 200)
            }
        }
    }

    private fun attachScaleAnimator(view: View, target: Float, durationMs: Long) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val to = if (hasFocus) target else 1.0f
            val sx = ObjectAnimator.ofFloat(v, View.SCALE_X, to)
            val sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, to)
            AnimatorSet().apply {
                playTogether(sx, sy)
                duration = durationMs
                start()
            }
        }
    }
}
