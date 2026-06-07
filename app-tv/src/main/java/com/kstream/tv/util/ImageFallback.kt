package com.kstream.tv.util

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.model.Movie
import com.kstream.tv.R

/**
 * Centralized fallback chain for loading movie artwork.
 *
 * Hero / Landscape (focused):  enrichment.backdrops.first() -> movie.posterUrl -> gradient
 * Portrait (unfocused):        enrichment.posterUrl -> movie.posterUrl -> gradient
 *
 * The gradient placeholder is deterministic per movie (seeded by title hash),
 * so the same movie always shows the same gradient.
 */
object ImageFallback {

    /**
     * Corner radius (px) applied via Glide to every loaded bitmap. Baking the
     * rounded corners into the bitmap itself is the only reliable way to get
     * crisp clipping on Fire TV API 25 — ImageView.clipToOutline is flaky
     * when the source bitmap is larger than the view bounds.
     *
     * Kept in sync with @dimen/card_poster_corner (16dp ≈ 32px @ 2x density).
     */
    private const val CORNER_PX = 32

    private val GRADIENTS = intArrayOf(
        R.drawable.gradient_placeholder_1,
        R.drawable.gradient_placeholder_2,
        R.drawable.gradient_placeholder_3,
        R.drawable.gradient_placeholder_4,
        R.drawable.gradient_placeholder_5,
        R.drawable.gradient_placeholder_6
    )

    fun gradientFor(movie: Movie): Int {
        val key = movie.movieName.ifBlank { movie.id }
        val idx = (key.hashCode().toLong() and 0x7fffffffL).rem(GRADIENTS.size).toInt()
        return GRADIENTS[idx]
    }

    /** Hero (1280x720). Prefers TMDb backdrop, then local poster (cropped), then gradient. */
    fun loadHeroBackdrop(target: ImageView, enrichment: MovieEnrichment?, movie: Movie) {
        val primary = enrichment?.backdrops?.firstOrNull()
        val fallback = movie.posterUrl.takeIf { it.isNotBlank() }
        loadChain(target, primary, fallback, gradientFor(movie), 1280, 720, fit = true)
    }

    /**
     * Spotlight tile fill chain (used when a home rail card is focused and
     * morphs into the 600×340 landscape state):
     *
     *  1. TMDb 16:9 backdrop                → loaded into [target] as
     *                                          fitCenter (no crop), [blurFill]
     *                                          stays empty (alpha 0).
     *  2. Portrait poster centered          → poster goes into [target] as
     *                                          fitCenter (letterboxed); same
     *                                          poster is loaded into [blurFill]
     *                                          as a heavy pseudo-blur so the
     *                                          side bars are filled.
     *  3. Initials placeholder              → black→grey gradient with the
     *                                          movie's initials, drawn entirely
     *                                          from primitives (no bitmap).
     *
     * Returns `true` when the blurred side-fill should be visible (case 2),
     * so the caller can fade [blurFill] in. Returns `false` otherwise.
     */
    fun loadSpotlight(
        target: ImageView,
        blurFill: ImageView,
        enrichment: MovieEnrichment?,
        movie: Movie
    ): Boolean {
        val rounded = MultiTransformation(FitCenter(), RoundedCorners(CORNER_PX))
        val roundedCrop = MultiTransformation(CenterCrop(), RoundedCorners(CORNER_PX))
        val backdrop = enrichment?.backdrops?.firstOrNull()?.takeIf { it.isNotBlank() }
        if (backdrop != null) {
            Glide.with(blurFill).clear(blurFill)
            blurFill.setImageDrawable(null)
            Glide.with(target)
                .load(backdrop)
                .placeholder(InitialsDrawable.forMovie(movie))
                .error(InitialsDrawable.forMovie(movie))
                .override(1280, 720)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .transform(rounded)
                .into(target)
            return false
        }
        val poster = movie.posterUrl.takeIf { it.isNotBlank() }
            ?: enrichment?.posterUrl?.takeIf { it.isNotBlank() }
        if (poster != null) {
            // Sharp poster, centered, no crop, corners rounded.
            Glide.with(target)
                .load(poster)
                .placeholder(InitialsDrawable.forMovie(movie))
                .error(InitialsDrawable.forMovie(movie))
                .override(420, 630)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .transform(rounded)
                .into(target)
            // Tiny copy = pseudo-blur when upscaled by the ImageView; also
            // rounded so the side-fill never spills past the tile's corners.
            Glide.with(blurFill)
                .load(poster)
                .override(40, 60)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .transform(roundedCrop)
                .into(blurFill)
            return true
        }
        // Final fallback: initials.
        Glide.with(target).clear(target)
        Glide.with(blurFill).clear(blurFill)
        target.setImageDrawable(InitialsDrawable.forMovie(movie))
        blurFill.setImageDrawable(null)
        return false
    }

    /**
     * Preview pane backdrop chain (used by the sticky home preview).
     * Same fallback logic as [loadSpotlight] but WITHOUT rounded corners
     * — the preview pane spans the full edge of the screen and uses fades
     * (not corner radii) to soften the visual seams.
     *
     *  1. TMDb 16:9 backdrop → [target] only, [blurFill] hidden.
     *     Returns `false`.
     *  2. Portrait poster    → [target] gets the sharp poster (consumer
     *                          should set scaleType=fitCenter), [blurFill]
     *                          gets the same image scaled down so the
     *                          ImageView upscale produces a soft blur.
     *                          Returns `true`.
     *  3. Initials fallback  → [target] gets the initials drawable,
     *                          [blurFill] cleared. Returns `false`.
     */
    fun loadPreviewBackdrop(
        target: ImageView,
        blurFill: ImageView,
        enrichment: MovieEnrichment?,
        movie: Movie
    ): Boolean {
        val backdrop = enrichment?.backdrops?.firstOrNull()?.takeIf { it.isNotBlank() }
        if (backdrop != null) {
            Glide.with(blurFill).clear(blurFill)
            blurFill.setImageDrawable(null)
            Glide.with(target)
                .load(backdrop)
                .placeholder(InitialsDrawable.forMovie(movie))
                .error(InitialsDrawable.forMovie(movie))
                .override(1280, 720)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(target)
            return false
        }
        val poster = movie.posterUrl.takeIf { it.isNotBlank() }
            ?: enrichment?.posterUrl?.takeIf { it.isNotBlank() }
        if (poster != null) {
            Glide.with(target)
                .load(poster)
                .placeholder(InitialsDrawable.forMovie(movie))
                .error(InitialsDrawable.forMovie(movie))
                .override(420, 630)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .into(target)
            // Tiny copy upscaled = soft pseudo-blur for the side fill.
            Glide.with(blurFill)
                .load(poster)
                .override(40, 60)
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .transform(CenterCrop())
                .into(blurFill)
            return true
        }
        Glide.with(target).clear(target)
        Glide.with(blurFill).clear(blurFill)
        target.setImageDrawable(InitialsDrawable.forMovie(movie))
        blurFill.setImageDrawable(null)
        return false
    }

    /**
     * Ambient blur fill behind [loadHeroBackdrop] — same image rendered very
     * small (40x23) then upscaled by the ImageView, which gives a soft
     * pseudo-blur without any extra library or RenderScript on API 25.
     * The view itself should apply alpha (~0.45) for the dimmed effect.
     */
    fun loadHeroBackdropBlur(target: ImageView, enrichment: MovieEnrichment?, movie: Movie) {
        val primary = enrichment?.backdrops?.firstOrNull()
        val fallback = movie.posterUrl.takeIf { it.isNotBlank() }
        loadChain(target, primary, fallback, gradientFor(movie), 40, 23, fit = false)
    }

    /** Landscape card (~480x270). Prefers TMDb backdrop, then local poster, then gradient. */
    fun loadLandscapePoster(target: ImageView, enrichment: MovieEnrichment?, movie: Movie) {
        val primary = enrichment?.backdrops?.firstOrNull()
        val fallback = movie.posterUrl.takeIf { it.isNotBlank() }
        loadChain(target, primary, fallback, gradientFor(movie), 480, 270)
    }

    /** Portrait card (~240x360). Prefers TMDb poster, then local poster, then gradient. */
    fun loadPortraitPoster(target: ImageView, enrichment: MovieEnrichment?, movie: Movie) {
        val primary = enrichment?.posterUrl
        val fallback = movie.posterUrl.takeIf { it.isNotBlank() }
        loadChain(target, primary, fallback, gradientFor(movie), 240, 360)
    }

    fun clear(target: ImageView) {
        Glide.with(target).clear(target)
    }

    private fun loadChain(
        target: ImageView,
        primary: String?,
        fallback: String?,
        gradientRes: Int,
        widthPx: Int,
        heightPx: Int,
        fit: Boolean = false
    ) {
        target.setImageResource(gradientRes)
        val first = primary?.takeIf { it.isNotBlank() }
        val second = fallback?.takeIf { it.isNotBlank() }
        val url = first ?: second ?: return
        // Bake rounded corners directly into the bitmap so the visible
        // poster shape matches the focus ring exactly, on any API level.
        val scale = if (fit) FitCenter() else CenterCrop()
        val transform = MultiTransformation(scale, RoundedCorners(CORNER_PX))
        val request = Glide.with(target)
            .load(url)
            .placeholder(gradientRes)
            .error(gradientRes)
            .override(widthPx, heightPx)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .transform(transform)
        if (first != null && second != null) {
            request.error(
                Glide.with(target)
                    .load(second)
                    .placeholder(gradientRes)
                    .error(gradientRes)
                    .override(widthPx, heightPx)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .transform(transform)
            ).into(target)
        } else {
            request.into(target)
        }
    }
}
