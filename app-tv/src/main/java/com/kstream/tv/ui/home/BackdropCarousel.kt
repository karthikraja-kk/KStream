package com.kstream.tv.ui.home

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Auto-scrolling backdrop carousel for the home preview pane.
 *
 * Holds two stacked ImageViews:
 *  - [primary] always shows the currently-visible image, alpha = 1.
 *  - [next]    sits on top, alpha = 0 except during a crossfade. Used as a
 *              buffer to preload the next image and to play the alpha
 *              animation; after the fade completes its drawable is copied
 *              into [primary] and [next] is reset to alpha 0.
 *
 * Lifecycle:
 *  - [start] kicks off the cycle for the supplied URL list. Idempotent —
 *    calling it again with a new list cancels the previous loop first.
 *  - [stop] cancels the loop and resets the overlay. Call from binder when
 *    the focused movie changes or when there are <2 backdrops.
 *
 * The first URL is rendered immediately (no fade) so the initial paint
 * happens at the same time as before; subsequent advancement is on a
 * [DWELL_MS] cadence.
 */
class BackdropCarousel(
    private val primary: ImageView,
    private val next: ImageView,
    private val scope: CoroutineScope
) {

    private var loopJob: Job? = null

    /**
     * Starts the carousel for [urls]. If [urls] has only one entry the
     * first image is loaded and no looping happens. Cancels any previous
     * loop first.
     */
    fun start(urls: List<String>, placeholder: Drawable?, error: Drawable?) {
        stop()
        val clean = urls.asSequence().filter { it.isNotBlank() }.take(MAX_SLIDES).toList()
        if (clean.isEmpty()) return

        // Render the first image immediately into the primary view — no
        // fade so the preview pane shows content as soon as enrichment lands.
        loadInto(primary, clean.first(), placeholder, error)
        if (clean.size == 1) return

        loopJob = scope.launch {
            var index = 1
            while (isActive) {
                delay(DWELL_MS)
                if (!isActive) break
                val url = clean[index]
                index = (index + 1) % clean.size
                crossfadeTo(url, placeholder, error)
            }
        }
    }

    /** Stops the loop and clears the overlay alpha. */
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        next.animate().cancel()
        next.alpha = 0f
        Glide.with(next).clear(next)
        next.setImageDrawable(null)
    }

    /**
     * Loads [url] into [next], then fades it in. When the fade completes
     * the drawable is mirrored into [primary] and [next] is reset so it's
     * ready for the following tick.
     */
    private fun crossfadeTo(url: String, placeholder: Drawable?, error: Drawable?) {
        Glide.with(next)
            .load(url)
            .placeholder(placeholder)
            .error(error)
            .override(1280, 720)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean = false

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    next.animate().cancel()
                    next.animate()
                        .alpha(1f)
                        .setDuration(FADE_MS)
                        .withEndAction {
                            // Promote the next-view image to primary so we
                            // can clear the overlay without flicker.
                            primary.setImageDrawable(resource.constantState?.newDrawable() ?: resource)
                            next.alpha = 0f
                        }
                        .start()
                    return false
                }
            })
            .into(next)
    }

    private fun loadInto(target: ImageView, url: String, placeholder: Drawable?, error: Drawable?) {
        Glide.with(target)
            .load(url)
            .placeholder(placeholder)
            .error(error)
            .override(1280, 720)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(target)
    }

    companion object {
        /** Max number of backdrops to cycle through (TMDb often returns 50+). */
        private const val MAX_SLIDES = 5

        /** How long each image stays fully visible before the fade kicks in. */
        private const val DWELL_MS = 5_000L

        /** Crossfade duration. */
        private const val FADE_MS = 700L
    }
}
