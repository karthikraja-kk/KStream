package com.kstream.tv.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.kstream.core.model.Movie
import com.kstream.feature.home.HomeViewModel
import com.kstream.tv.R
import com.kstream.tv.tier.DeviceTier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Top-of-Home hero carousel.
 *
 *  - Reads [HomeViewModel.uiState.heroMovies] (top 5).
 *  - Auto-advances every [ROTATE_INTERVAL_MS]; pauses while one of the
 *    interactive elements (Play / More Info) holds focus.
 *  - Crossfades the backdrop via Glide (LOW tier: instant swap, no crossfade).
 *  - Dots indicator at the bottom shows current position.
 *  - Exposes [onPlayClicked] / [onMoreInfoClicked] callbacks that MainActivity
 *    wires to the Player / Details flows (Details lands in P7, Player in P8).
 *
 * Focus contract with MainActivity:
 *  - When the hero is paused via [setAutoRotateEnabled] (false), the activity
 *    knows hero is interactive.
 *  - When the hero loses focus to the rows below, the activity calls
 *    [setAutoRotateEnabled] (true) again so rotation resumes.
 */
@AndroidEntryPoint
class HeroFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels({ requireActivity() })

    private lateinit var backdrop: ImageView
    private lateinit var titleView: TextView
    private lateinit var metaView: TextView
    private lateinit var synopsisView: TextView
    private lateinit var playButton: Button
    private lateinit var moreInfoButton: Button
    private lateinit var dotsContainer: LinearLayout

    private var heroes: List<Movie> = emptyList()
    private var currentIndex: Int = 0
    private var rotateJob: Job? = null
    private var autoRotateEnabled: Boolean = true

    private val onPlayHandlers = mutableSetOf<(Movie) -> Unit>()
    private val onMoreInfoHandlers = mutableSetOf<(Movie) -> Unit>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_hero, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backdrop = view.findViewById(R.id.hero_backdrop)
        titleView = view.findViewById(R.id.hero_title)
        metaView = view.findViewById(R.id.hero_meta)
        synopsisView = view.findViewById(R.id.hero_synopsis)
        playButton = view.findViewById(R.id.hero_btn_play)
        moreInfoButton = view.findViewById(R.id.hero_btn_more)
        dotsContainer = view.findViewById(R.id.hero_dots)

        playButton.setOnClickListener {
            heroes.getOrNull(currentIndex)?.let { m -> onPlayHandlers.forEach { it(m) } }
        }
        moreInfoButton.setOnClickListener {
            heroes.getOrNull(currentIndex)?.let { m -> onMoreInfoHandlers.forEach { it(m) } }
        }

        // Pause rotation while hero is interactive (any button focused).
        val focusListener = View.OnFocusChangeListener { _, hasFocus ->
            setAutoRotateEnabled(!hasFocus)
        }
        playButton.onFocusChangeListener = focusListener
        moreInfoButton.onFocusChangeListener = focusListener

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    onHeroesChanged(state.heroMovies)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startRotation()
    }

    override fun onStop() {
        super.onStop()
        stopRotation()
    }

    fun setOnPlayClicked(handler: (Movie) -> Unit) { onPlayHandlers += handler }
    fun setOnMoreInfoClicked(handler: (Movie) -> Unit) { onMoreInfoHandlers += handler }

    /** MainActivity calls this when D-pad up brings focus back to hero. */
    fun focusPlayButton(): Boolean = if (::playButton.isInitialized) playButton.requestFocus() else false

    private fun setAutoRotateEnabled(enabled: Boolean) {
        if (autoRotateEnabled == enabled) return
        autoRotateEnabled = enabled
        if (enabled) startRotation() else stopRotation()
    }

    private fun onHeroesChanged(newHeroes: List<Movie>) {
        if (newHeroes == heroes) return
        heroes = newHeroes
        currentIndex = currentIndex.coerceIn(0, (heroes.size - 1).coerceAtLeast(0))
        rebuildDots()
        bindCurrent()
        if (autoRotateEnabled) startRotation()
    }

    private fun startRotation() {
        if (heroes.size < 2 || !autoRotateEnabled) return
        if (rotateJob?.isActive == true) return
        rotateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && autoRotateEnabled && heroes.size >= 2) {
                delay(ROTATE_INTERVAL_MS)
                if (!autoRotateEnabled) break
                currentIndex = (currentIndex + 1) % heroes.size
                bindCurrent()
                updateDotsSelection()
            }
        }
    }

    private fun stopRotation() {
        rotateJob?.cancel()
        rotateJob = null
    }

    private fun bindCurrent() {
        val m = heroes.getOrNull(currentIndex) ?: run {
            titleView.text = ""
            metaView.text = ""
            synopsisView.text = ""
            backdrop.setImageDrawable(null)
            return
        }
        titleView.text = m.movieName
        val meta = listOfNotNull(
            m.year.takeIf { it > 0 }?.toString(),
            m.duration.takeIf { it.isNotBlank() },
            m.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
            m.language.takeIf { it.isNotBlank() }
        )
        metaView.text = meta.joinToString("   ·   ")
        synopsisView.text = m.synopsis

        val tier = DeviceTier.get(requireContext())
        val request = Glide.with(this)
            .load(m.posterUrl.takeIf { it.isNotBlank() })
            .placeholder(R.color.bg_surface)
            .error(R.color.bg_surface)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        if (tier != DeviceTier.LOW) {
            request.transition(DrawableTransitionOptions.withCrossFade(500))
        }
        request.into(backdrop)
    }

    private fun rebuildDots() {
        dotsContainer.removeAllViews()
        val ctx = requireContext()
        val size = resources.getDimensionPixelSize(R.dimen.space_sm)
        val margin = resources.getDimensionPixelSize(R.dimen.space_xs)
        heroes.indices.forEach { i ->
            val dot = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                background = ctx.getDrawable(R.drawable.hero_dot)
                isSelected = (i == currentIndex)
                alpha = if (i == currentIndex) 1.0f else 0.4f
            }
            dotsContainer.addView(dot)
        }
    }

    private fun updateDotsSelection() {
        for (i in 0 until dotsContainer.childCount) {
            val v = dotsContainer.getChildAt(i)
            v.isSelected = (i == currentIndex)
            v.alpha = if (i == currentIndex) 1.0f else 0.4f
        }
    }

    companion object {
        private const val ROTATE_INTERVAL_MS = 7_000L
    }
}
