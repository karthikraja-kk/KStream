package com.kstream.tv.ui.home

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.enrichment.EnrichmentRepository
import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.model.Movie
import com.kstream.tv.R
import com.kstream.tv.util.DurationFormat
import com.kstream.tv.util.ImageFallback
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Renders the sticky preview pane (title + rating + year + duration +
 * landscape backdrop) for the currently-focused rail tile.
 *
 * Wires three things together:
 *  1. [FocusedMovieRelay] — pushed by [com.kstream.tv.ui.home.presenter
 *     .MovieCardPresenter] on focus.
 *  2. [EnrichmentRepository] — observed per focused movie so the backdrop
 *     swaps in once TMDb data arrives (already prefetched by
 *     [HomeRowsFragment] for above-the-fold tiles).
 *  3. The static view tree under `R.id.home_preview`.
 *
 * The relay debounces focus events by [SETTLE_MS] so a rapid D-pad sweep
 * across a rail never thrashes the backdrop. The first non-null movie
 * emission is rendered immediately (no debounce) so cold-start doesn't
 * leave the pane blank for the debounce window.
 */
class HomePreviewBinder(
    private val root: View,
    private val focusedRelay: FocusedMovieRelay,
    private val enrichmentRepository: EnrichmentRepository,
    private val userDataRepository: UserDataRepository
) {

    private val backdrop: ImageView = root.findViewById(R.id.preview_backdrop)
    private val backdropBlur: ImageView = root.findViewById(R.id.preview_backdrop_blur)
    private val backdropNext: ImageView = root.findViewById(R.id.preview_backdrop_next)
    private val title: TextView = root.findViewById(R.id.preview_title)
    private val rating: TextView = root.findViewById(R.id.preview_rating)
    private val year: TextView = root.findViewById(R.id.preview_year)
    private val duration: TextView = root.findViewById(R.id.preview_duration)
    private val badgeNew: TextView = root.findViewById(R.id.preview_badge_new)
    private val badgeQuality: TextView = root.findViewById(R.id.preview_badge_quality)
    private val badgeCert: TextView = root.findViewById(R.id.preview_badge_cert)

    private val factBubble: View = root.findViewById(R.id.preview_fact_bubble)
    private val factAvatar: View = root.findViewById(R.id.preview_fact_avatar)
    private val factBody: View = root.findViewById(R.id.preview_fact_body)
    private val factGreeting: TextView = root.findViewById(R.id.preview_fact_greeting)
    private val factText: TextView = root.findViewById(R.id.preview_fact_text)

    private var enrichmentJob: Job? = null
    private var lastRenderedMovieId: String? = null

    /**
     * Cached username — read once on attach. Picking the fact greeting
     * doesn't need to reactively re-pull this; settings changes only
     * affect future focus events, not in-flight renders.
     */
    private var cachedUsername: String? = null

    /**
     * Per-focused-movie pinned fact: once we pick a fact for a movie we
     * keep showing the SAME one until the user moves to a different tile.
     * That avoids flicker when enrichment emits twice (cache hit then
     * background refresh) and avoids re-randomising mid-view. The pick
     * itself is randomised across visits so the user sees variety.
     */
    private var pinnedFactMovieId: String? = null
    private var pinnedFact: FactBubble.Fact? = null
    private var pinnedFactSeed: Long = 0L

    /** Lazily-constructed carousel — needs a CoroutineScope from attach(). */
    private var carousel: BackdropCarousel? = null

    /** Track which movie + URL set the carousel is currently running for, to skip pointless restarts. */
    private var carouselMovieId: String? = null
    private var carouselUrls: List<String> = emptyList()

    init {
        // Brand gradient on the title (orange → magenta → purple).
        // Applied after layout so the shader has a real measured width.
        title.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    applyTitleGradient()
                }
            }
        )
    }

    fun attach(owner: LifecycleOwner) {
        carousel = BackdropCarousel(backdrop, backdropNext, owner.lifecycleScope)
        owner.lifecycleScope.launch {
            // Read once — username changes are rare and don't need to
            // retroactively rewrite the currently-shown fact.
            cachedUsername = runCatching {
                userDataRepository.username.first()
            }.getOrNull()
        }
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                focusedRelay.focused
                    .filterNotNull()
                    .distinctUntilChanged { a, b -> a.id == b.id }
                    .debounce(SETTLE_MS.milliseconds)
                    .collectLatest { movie ->
                        // New focused movie → reset the pinned fact so the
                        // next enrichment emit randomises a fresh pick.
                        if (movie.id != pinnedFactMovieId) {
                            pinnedFactMovieId = movie.id
                            pinnedFact = null
                            pinnedFactSeed = System.nanoTime()
                            // Hide bubble until enrichment arrives.
                            factBubble.visibility = View.GONE
                            // Stop any in-flight carousel so the old
                            // movie's slides don't bleed into the new one.
                            carousel?.stop()
                            carouselMovieId = null
                            carouselUrls = emptyList()
                        }
                        renderMovie(movie, enrichment = null)
                        observeEnrichment(owner, movie)
                    }
            }
        }
    }

    private fun observeEnrichment(owner: LifecycleOwner, movie: Movie) {
        enrichmentJob?.cancel()
        enrichmentJob = owner.lifecycleScope.launch {
            enrichmentRepository.observe(movie).collectLatest { enrichment ->
                renderMovie(movie, enrichment)
            }
        }
    }

    private fun renderMovie(movie: Movie, enrichment: MovieEnrichment?) {
        title.text = movie.movieName
        title.visibility = View.VISIBLE
        applyTitleGradient()

        // ★ rating: TMDb numeric > scraped string. Hide if neither present.
        val ratingText = formatRating(movie.rating, enrichment?.tmdbRating)
        if (ratingText != null) {
            rating.text = ratingText
            rating.visibility = View.VISIBLE
        } else {
            rating.visibility = View.GONE
        }

        year.text = movie.year.takeIf { it > 0 }?.toString().orEmpty()
        year.visibility = if (year.text.isNullOrBlank()) View.GONE else View.VISIBLE

        duration.text = DurationFormat.format(movie.duration)
        duration.visibility = if (duration.text.isNullOrBlank()) View.GONE else View.VISIBLE

        // NEW badge: lit when the movie was added within the last 10 days.
        // Mirrors MovieCardPresenter.isRecentlyAdded so the preview and the
        // tile under it stay in sync about "new".
        badgeNew.visibility = if (isRecentlyAdded(movie.lastUpdated)) View.VISIBLE else View.GONE

        // Quality badge: only show when we actually have a clean label
        // (e.g. "HD" for "Original HD" scraped type). Hidden otherwise so
        // we don't print noisy raw type strings.
        val quality = qualityLabel(movie.type)
        if (quality != null) {
            badgeQuality.text = quality
            badgeQuality.visibility = View.VISIBLE
        } else {
            badgeQuality.visibility = View.GONE
        }

        // Certification (US) sourced from TMDb release_dates. Only present
        // once enrichment has loaded; hides cleanly until then so the badge
        // row doesn't flicker an empty chip on first paint.
        val cert = enrichment?.certification?.trim().orEmpty()
        if (cert.isNotEmpty()) {
            badgeCert.text = cert
            badgeCert.visibility = View.VISIBLE
        } else {
            badgeCert.visibility = View.GONE
        }

        // Backdrop: TMDb landscape > portrait + blur fill > initials.
        //  - Multi-backdrop landscape → BackdropCarousel cycles up to 5
        //    images (5s dwell + 700ms crossfade). preview_backdrop_blur
        //    stays cleared while the carousel runs.
        //  - Single landscape         → carousel renders just that one
        //                               image (no cycling).
        //  - Portrait / odd / none    → fall back to the existing single-
        //                               image path with blur fill.
        val landscapeUrls = enrichment?.backdrops
            ?.asSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()

        if (landscapeUrls.isNotEmpty()) {
            // Carousel takes over — make sure scale is centerCrop and the
            // blur fill from a previous portrait render isn't lingering.
            backdrop.scaleType = ImageView.ScaleType.CENTER_CROP
            backdropNext.scaleType = ImageView.ScaleType.CENTER_CROP
            backdropBlur.alpha = 0f
            com.bumptech.glide.Glide.with(backdropBlur).clear(backdropBlur)
            backdropBlur.setImageDrawable(null)
            // Skip restart when this same movie+url-set is already cycling
            // (otherwise a cache→refresh enrichment burst resets slide 0
            // every time).
            val sameCarousel = carouselMovieId == movie.id && carouselUrls == landscapeUrls
            if (!sameCarousel) {
                val placeholder = com.kstream.tv.util.InitialsDrawable.forMovie(movie)
                carousel?.start(landscapeUrls, placeholder, placeholder)
                carouselMovieId = movie.id
                carouselUrls = landscapeUrls
            }
        } else {
            // No backdrops — fall back to single-image path (poster + blur
            // or initials). Carousel must be idle so it doesn't overwrite.
            carousel?.stop()
            carouselMovieId = null
            carouselUrls = emptyList()
            val needsBlur = ImageFallback.loadPreviewBackdrop(backdrop, backdropBlur, enrichment, movie)
            backdrop.scaleType = if (needsBlur) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            backdropBlur.alpha = if (needsBlur) 1f else 0f
        }

        renderFactBubble(movie, enrichment)
        lastRenderedMovieId = movie.id
    }

    /**
     * Picks (or reuses) a fact for the current movie and updates the
     * bubble. Plays the pop-in animation only the FIRST time the bubble
     * becomes visible for a given movie, so subsequent enrichment refreshes
     * don't re-trigger the animation.
     */
    private fun renderFactBubble(movie: Movie, enrichment: MovieEnrichment?) {
        if (enrichment == null) {
            // Pre-enrichment render — leave the bubble hidden, attach()
            // already cleared it on focus change.
            return
        }
        // Reuse the pinned pick when we're still on the same movie so the
        // user doesn't see the bubble re-randomise on a background refresh.
        val fact = pinnedFact
            ?: FactBubble.build(movie, enrichment, cachedUsername, pinnedFactSeed)?.also {
                pinnedFact = it
            }
        if (fact == null) {
            factBubble.visibility = View.GONE
            return
        }

        factGreeting.text = fact.greeting
        factText.text = fact.body

        val isAlreadyShownForMovie = factBubble.visibility == View.VISIBLE &&
            lastRenderedMovieId == movie.id
        factBubble.visibility = View.VISIBLE
        if (!isAlreadyShownForMovie) {
            playFactPopIn()
        }
    }

    /**
     * Pop-in animation:
     *  - Avatar fades + scales from 0.85→1 over 140ms.
     *  - Body slides up 6dp + scales 0.92→1 over 220ms with overshoot.
     *  - 80ms stagger between avatar and body so the avatar lands first.
     */
    private fun playFactPopIn() {
        val density = root.resources.displayMetrics.density
        val travelPx = 6f * density

        factAvatar.alpha = 0f
        factAvatar.scaleX = 0.85f
        factAvatar.scaleY = 0.85f
        factAvatar.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(140L)
            .setStartDelay(0L)
            .start()

        factBody.alpha = 0f
        factBody.scaleX = 0.92f
        factBody.scaleY = 0.92f
        factBody.translationY = travelPx
        factBody.pivotX = 0f
        factBody.pivotY = 0f
        factBody.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(220L)
            .setStartDelay(80L)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
    }

    private fun formatRating(scrapedRating: String, tmdbRating: Double?): String? {
        val tmdb = tmdbRating?.takeIf { it > 0.0 }
        if (tmdb != null) return "★ %.1f".format(tmdb)
        val scraped = scrapedRating.trim()
        if (scraped.isBlank()) return null
        return "★ $scraped"
    }

    private fun applyTitleGradient() {
        val text = title.text?.toString().orEmpty()
        if (text.isEmpty()) return
        val width = title.paint.measureText(text).coerceAtLeast(1f)
        title.paint.shader = LinearGradient(
            0f, 0f, width, 0f,
            intArrayOf(
                Color.parseColor("#FF6B1A"),
                Color.parseColor("#E91E63"),
                Color.parseColor("#6A1A9A")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        title.invalidate()
    }

    companion object {
        // Debounce on focus changes. Matches MovieCardPresenter's settle
        // window so the visual "spotlight should rest here" decision is
        // synced between the tile (none today — flat tile) and the preview.
        private const val SETTLE_MS = 130L

        // NEW badge window — keep in lock-step with MovieCardPresenter.
        private const val RECENT_DAYS = 10L
        private val recentDateFormat =
            java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.ENGLISH)
    }

    private fun qualityLabel(type: String): String? {
        return if (type.equals("Original HD", ignoreCase = true)) "HD" else null
    }

    private fun isRecentlyAdded(lastUpdated: String): Boolean {
        if (lastUpdated.isBlank()) return false
        return try {
            val parsed = recentDateFormat.parse(lastUpdated) ?: return false
            val diffDays =
                (System.currentTimeMillis() - parsed.time) / (1000L * 60 * 60 * 24)
            diffDays in 0..RECENT_DAYS
        } catch (_: Exception) {
            false
        }
    }
}
