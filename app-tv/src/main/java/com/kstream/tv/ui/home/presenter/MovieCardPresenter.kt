package com.kstream.tv.ui.home.presenter

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress
import com.kstream.tv.R
import com.kstream.tv.ui.home.FocusedMovieRelay
import com.kstream.tv.util.ImageFallback
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Flat movie card.
 *
 * One state: 120×180 portrait poster + title below. Focus is shown by
 * the focus_ring_glow drawable (gold halo with brand-gradient stroke)
 * painted as foreground on the inner poster container, so the halo
 * hugs the visible tile rather than the wider 140dp wrapper.
 *
 * On focus, the bound movie is pushed into [FocusedMovieRelay] so the
 * sticky preview pane mirrors it.
 */
class MovieCardPresenter(
    private val focusedRelay: FocusedMovieRelay? = null
) : Presenter() {

    @Volatile private var progressMap: Map<String, WatchProgress> = emptyMap()
    @Volatile private var enrichmentMap: Map<String, MovieEnrichment> = emptyMap()

    var onMovieClick: ((Movie) -> Unit)? = null

    fun updateProgress(progress: Map<String, WatchProgress>) { progressMap = progress }
    fun updateEnrichment(enrichment: Map<String, MovieEnrichment>) { enrichmentMap = enrichment }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val ctx = ContextThemeWrapper(parent.context, R.style.Theme_KStreamTv)
        val inflater = LayoutInflater.from(ctx)
        val view = inflater.inflate(R.layout.card_movie, parent, false)
        applyAdaptiveSize(view)
        return ViewHolder(view)
    }

    /**
     * Resize the card so a full rail + a peek of the next rail fits in
     * the 40% rails area (the home preview pane takes the top 60%).
     *
     * Chrome accounted for per rail:
     *   row header      ~32dp
     *   bottom spacing  ~16dp
     *   next-rail peek  ~50dp
     *
     * Remaining height = poster height (2:3 aspect, width = h * 120/180).
     * Clamped to [MIN_POSTER_DP, MAX_POSTER_DP] for sanity on weird sizes.
     */
    private fun applyAdaptiveSize(view: View) {
        val size = RailCardSizing.computePosterSize(view.resources.displayMetrics)
        val padPx = view.resources.getDimensionPixelSize(R.dimen.card_focus_padding)

        view.findViewById<View>(R.id.card_poster_container)?.let { container ->
            container.layoutParams = container.layoutParams.apply {
                width = size.widthPx
                height = size.heightPx
            }
        }
        view.findViewById<View>(R.id.card_root)?.let { root ->
            // card_root reserves room for the focus-ring padding on every side
            // so the painted ring (drawn at the padded edge) is fully visible.
            root.layoutParams = root.layoutParams.apply { width = size.widthPx + 2 * padPx }
        }
        // Hide title under the tile: focused movie's title is already shown
        // large in the sticky preview pane, so duplicating it under every
        // poster wastes vertical space without adding info.
        view.findViewById<TextView>(R.id.card_title)?.visibility = View.GONE
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val movie = item as? Movie ?: return
        val v = viewHolder.view
        v.setTag(R.id.card_root, movie)

        val poster = v.findViewById<ImageView>(R.id.card_poster_portrait)
        val title = v.findViewById<TextView>(R.id.card_title)
        val badge = v.findViewById<TextView>(R.id.card_quality_badge)
        val newBadge = v.findViewById<TextView>(R.id.card_new_badge)
        val progressBar = v.findViewById<View>(R.id.card_progress)

        title.text = movie.movieName

        val qualityText = qualityLabel(movie.type)
        if (qualityText != null) {
            badge.text = qualityText
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
        newBadge.visibility = if (isRecentlyAdded(movie.lastUpdated)) View.VISIBLE else View.GONE

        bindProgress(v, progressBar, movie)
        v.setOnClickListener { onMovieClick?.invoke(movie) }

        v.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                focusedRelay?.push(movie)
            }
            // Brand-gradient focus ring on the OUTER card_root (which has
            // card_focus_padding around the inner poster). Painting the ring
            // at the padded edge guarantees it sits fully outside the artwork
            // without any chance of view-bounds clipping.
            view.foreground = if (hasFocus) FocusRing.build(view.context) else null
            view.animate().cancel()
            view.animate()
                .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                .setDuration(FOCUS_SCALE_MS)
                .start()
        }

        val enrichment = enrichmentMap[movie.id]
        ImageFallback.loadPortraitPoster(poster, enrichment, movie)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val v = viewHolder.view
        val poster = v.findViewById<ImageView>(R.id.card_poster_portrait)
        if (!isContextDestroyed(v.context)) {
            runCatching { Glide.with(v.context).clear(poster) }
        }
        poster.setImageDrawable(null)
        v.setOnFocusChangeListener(null)
        v.setOnClickListener(null)
        v.setTag(R.id.card_root, null)
        v.findViewById<View>(R.id.card_poster_container)?.foreground = null
        v.foreground = null
        // Reset focus scale so the recycled view doesn't reappear "lifted".
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f
    }

    private fun bindProgress(card: View, progressBar: View, movie: Movie) {
        val wp = progressMap[movie.id]
        if (wp == null || wp.completionPercent <= 0f) {
            progressBar.visibility = View.GONE
            return
        }
        progressBar.visibility = View.VISIBLE
        val ratio = (wp.completionPercent / 100f).coerceIn(0f, 1f)
        progressBar.post {
            val posterWidth = card.findViewById<View>(R.id.card_poster_container).width
                .coerceAtLeast(card.resources.getDimensionPixelSize(R.dimen.card_poster_width))
            val lp = progressBar.layoutParams
            lp.width = (posterWidth * ratio).toInt().coerceAtLeast(1)
            progressBar.layoutParams = lp
        }
    }

    private fun qualityLabel(type: String): String? {
        return if (type.equals("Original HD", ignoreCase = true)) "HD" else null
    }

    private fun isRecentlyAdded(lastUpdated: String): Boolean {
        if (lastUpdated.isBlank()) return false
        return try {
            val parsed = recentDateFormat.parse(lastUpdated) ?: return false
            val diffMs = System.currentTimeMillis() - parsed.time
            val diffDays = diffMs / (1000L * 60 * 60 * 24)
            diffDays in 0..RECENT_DAYS
        } catch (_: Exception) {
            false
        }
    }

    private fun isContextDestroyed(ctx: android.content.Context?): Boolean {
        val activity = generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }
            .firstOrNull { it is android.app.Activity } as? android.app.Activity
            ?: return false
        return activity.isDestroyed || activity.isFinishing
    }

    companion object {
        private const val RECENT_DAYS = 10L
        private val recentDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)

        // Focus visual: scale the whole card up so it visibly "lifts"
        // out of the row. 1.08× chosen empirically — large enough to
        // read on 10-foot TV, small enough not to collide with the next
        // tile (rail spacing absorbs the extra width comfortably).
        private const val FOCUS_SCALE = 1.08f
        private const val FOCUS_SCALE_MS = 140L
    }
}
