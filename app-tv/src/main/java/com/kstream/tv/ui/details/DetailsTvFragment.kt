package com.kstream.tv.ui.details

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.enrichment.EnrichmentRepository
import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.enrichment.model.SimilarHint
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia
import com.kstream.feature.details.DetailsUiState
import com.kstream.feature.details.DetailsViewModel
import com.kstream.tv.R
import com.kstream.tv.ui.details.presenter.CastCardPresenter
import com.kstream.tv.ui.home.presenter.MovieCardPresenter
import com.kstream.tv.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetailsTvFragment : Fragment() {

    @Inject lateinit var enrichmentRepository: EnrichmentRepository
    @Inject lateinit var movieRepository: MovieRepository

    private val viewModel: DetailsViewModel by viewModels()

    private lateinit var backdrop: ImageView
    private lateinit var logoImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var taglineText: TextView
    private lateinit var metaText: TextView
    private lateinit var genreRow: ViewGroup
    private lateinit var synopsisText: TextView
    private lateinit var synopsisToggle: TextView
    private lateinit var directorText: TextView
    private lateinit var playButton: Button
    private lateinit var resumePill: View
    private lateinit var resumeLabel: TextView
    private lateinit var resumeProgressFill: View
    private lateinit var likeButton: Button
    private lateinit var qualityRow: ViewGroup
    private lateinit var castHeader: TextView
    private lateinit var castGrid: HorizontalGridView
    private lateinit var similarHeader: TextView
    private lateinit var similarGrid: HorizontalGridView
    private lateinit var errorOverlay: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var loadingOverlay: View

    private val castAdapter = ArrayObjectAdapter(CastCardPresenter())
    private val similarPresenter = MovieCardPresenter()
    private val similarAdapter = ArrayObjectAdapter(similarPresenter)

    private var initialFocusRequested = false
    private var lastBackdropUrl: String? = null
    private var lastSimilarTmdbId: Int = -1
    private var synopsisExpanded = false
    private var fullSynopsis: String = ""
    private var kenBurnsAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_details_tv, container, false)

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backdrop = view.findViewById(R.id.details_backdrop)
        logoImage = view.findViewById(R.id.details_logo)
        titleText = view.findViewById(R.id.details_title)
        taglineText = view.findViewById(R.id.details_tagline)
        metaText = view.findViewById(R.id.details_meta)
        genreRow = view.findViewById(R.id.genre_row)
        synopsisText = view.findViewById(R.id.details_synopsis)
        synopsisToggle = view.findViewById(R.id.synopsis_toggle)
        directorText = view.findViewById(R.id.details_director)
        playButton = view.findViewById(R.id.btn_play)
        resumePill = view.findViewById(R.id.resume_pill)
        resumeLabel = view.findViewById(R.id.resume_label)
        resumeProgressFill = view.findViewById(R.id.resume_progress_fill)
        likeButton = view.findViewById(R.id.btn_like)
        qualityRow = view.findViewById(R.id.quality_row)
        castHeader = view.findViewById(R.id.cast_header)
        castGrid = view.findViewById(R.id.cast_grid)
        similarHeader = view.findViewById(R.id.similar_header)
        similarGrid = view.findViewById(R.id.similar_grid)
        errorOverlay = view.findViewById(R.id.error_overlay)
        errorText = view.findViewById(R.id.error_text)
        retryButton = view.findViewById(R.id.btn_retry)
        loadingOverlay = view.findViewById(R.id.loading_overlay)

        castGrid.adapter = ItemBridgeAdapter(castAdapter)
        similarGrid.adapter = ItemBridgeAdapter(similarAdapter)
        similarPresenter.onMovieClick = { movie ->
            startActivity(DetailsActivity.newIntent(requireContext(), movie))
        }

        playButton.setOnClickListener { startPlayback(resume = false) }
        resumePill.setOnClickListener { startPlayback(resume = true) }
        resumePill.setOnKeyListener { v, keyCode, event ->
            // DPAD_DOWN on the resume pill opens the "Start over" menu (replaces
            // the separate ▾ button). Returning true consumes the event so
            // focus doesn't escape downward to the quality row.
            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
            ) {
                showResumeMoreMenu(v)
                true
            } else false
        }
        resumePill.setOnLongClickListener {
            showResumeMoreMenu(it)
            true
        }
        likeButton.setOnClickListener { viewModel.toggleLike() }
        retryButton.setOnClickListener { viewModel.refreshMovieDetails() }
        synopsisToggle.setOnClickListener { toggleSynopsis() }
        startKenBurns()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.movieWithMedia?.movie }
                    .distinctUntilChanged()
                    .flatMapLatest { movie ->
                        if (movie == null) {
                            flowOf<MovieEnrichment?>(null)
                        } else {
                            runCatching { enrichmentRepository.ensureCached(movie) }
                            enrichmentRepository.observe(movie)
                        }
                    }
                    .collectLatest { enrichment -> renderEnrichment(enrichment) }
            }
        }
    }

    override fun onDestroyView() {
        kenBurnsAnimator?.cancel()
        kenBurnsAnimator = null
        super.onDestroyView()
    }

    private fun render(state: DetailsUiState) {
        loadingOverlay.isVisible = state.isLoading
        val mwm = state.movieWithMedia
        if (state.error != null && mwm == null) {
            errorOverlay.isVisible = true
            errorText.text = state.error
            return
        }
        errorOverlay.isVisible = false
        if (mwm == null) return
        bindMovie(mwm)
        bindQualities(mwm, state.selectedQuality)
        val hasProgress = state.hasWatchProgress
        resumePill.isVisible = hasProgress
        playButton.isVisible = !hasProgress
        if (hasProgress) bindResumeProgress(state.watchProgressPercent)
        // Keep focus sensible when toggling between Play and Resume.
        if (hasProgress && playButton.isFocused) resumePill.requestFocus()
        else if (!hasProgress && resumePill.isFocused) playButton.requestFocus()
        likeButton.text = if (state.isLiked) getString(R.string.details_liked) else getString(R.string.details_like)

        // Defer initial focus until content is actually loaded so the first
        // D-pad press always lands on a meaningful action.
        if (!initialFocusRequested) {
            initialFocusRequested = true
            (if (hasProgress) resumePill else playButton).requestFocus()
        }
    }

    private fun bindResumeProgress(percent: Float) {
        val pct = percent.coerceIn(0f, 100f)
        val parent = resumeProgressFill.parent as? View ?: return
        val run = Runnable {
            val w = parent.width
            if (w <= 0) return@Runnable
            val lp = resumeProgressFill.layoutParams
            lp.width = ((w * pct) / 100f).toInt().coerceAtLeast(1)
            resumeProgressFill.layoutParams = lp
        }
        if (parent.width > 0) run.run() else parent.post(run)
    }

    private fun showResumeMoreMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_START_OVER, 0, R.string.details_start_over)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_START_OVER) {
                startPlayback(resume = false)
                true
            } else false
        }
        popup.show()
    }

    private fun bindMovie(mwm: MovieWithMedia) {
        val movie: Movie = mwm.movie
        titleText.text = movie.movieName
        bindGenres(movie.type, movie.genres)
        bindDirector(movie.director)
        bindSynopsis(movie.synopsis)
        // Local poster fallback only when we don't yet have a TMDb backdrop.
        if (lastBackdropUrl == null && movie.posterUrl.isNotBlank()) {
            loadBackdrop(movie.posterUrl)
        }
    }

    private fun bindGenres(type: String, genres: List<String>) {
        genreRow.removeAllViews()
        val ctx = requireContext()
        val padH = dp(12)
        val padV = dp(4)
        val marginEnd = dp(8)
        val trimmedType = type.trim()
        if (trimmedType.isEmpty() && genres.isEmpty()) {
            genreRow.visibility = View.GONE
            return
        }
        genreRow.visibility = View.VISIBLE
        if (trimmedType.isNotEmpty()) {
            val typeChip = TextView(ctx).apply {
                text = trimmedType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                setBackgroundResource(R.drawable.type_chip_bg)
                setTextColor(ContextCompat.getColor(ctx, R.color.accent_gold))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(padH, padV, padH, padV)
                isAllCaps = false
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = marginEnd
            genreRow.addView(typeChip, lp)
        }
        genres.take(4).forEach { g ->
            val chip = TextView(ctx).apply {
                text = g
                setBackgroundResource(R.drawable.genre_chip_bg)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(padH, padV, padH, padV)
                isAllCaps = false
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = marginEnd
            genreRow.addView(chip, lp)
        }
    }

    private fun bindDirector(directors: List<String>) {
        val nonEmpty = directors.filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) {
            directorText.isGone = true
        } else {
            directorText.isVisible = true
            directorText.text = getString(R.string.details_directed_by, nonEmpty.joinToString(", "))
        }
    }

    private fun bindSynopsis(text: String) {
        fullSynopsis = text
        synopsisText.text = text
        // Defer measurement; check after layout pass whether we need a More toggle.
        synopsisText.post {
            val needsToggle = synopsisText.layout?.let { layout ->
                layout.lineCount > synopsisText.maxLines ||
                    layout.getEllipsisCount(layout.lineCount - 1) > 0
            } ?: false
            synopsisToggle.isVisible = needsToggle && fullSynopsis.length > 0
            synopsisToggle.text = getString(if (synopsisExpanded) R.string.details_less else R.string.details_more)
        }
    }

    private fun toggleSynopsis() {
        synopsisExpanded = !synopsisExpanded
        synopsisText.maxLines = if (synopsisExpanded) Int.MAX_VALUE else 5
        synopsisToggle.text = getString(if (synopsisExpanded) R.string.details_less else R.string.details_more)
    }

    private fun loadBackdrop(url: String) {
        if (url == lastBackdropUrl) return
        lastBackdropUrl = url
        Glide.with(this).load(url)
            .placeholder(R.drawable.backdrop_placeholder)
            .error(R.drawable.backdrop_placeholder)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .into(backdrop)
    }

    private fun renderEnrichment(enrichment: MovieEnrichment?) {
        if (enrichment == null) {
            castHeader.isGone = true
            castGrid.isGone = true
            taglineText.isGone = true
            logoImage.isGone = true
            similarHeader.isGone = true
            similarGrid.isGone = true
            return
        }
        val firstBackdrop = enrichment.backdrops.firstOrNull()
        if (!firstBackdrop.isNullOrBlank()) {
            loadBackdrop(firstBackdrop)
        }
        if (!enrichment.logoUrl.isNullOrBlank()) {
            logoImage.isVisible = true
            titleText.isGone = true
            Glide.with(this).load(enrichment.logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .fitCenter()
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(logoImage)
        } else {
            logoImage.isGone = true
            titleText.isVisible = true
        }
        if (!enrichment.tagline.isNullOrBlank()) {
            taglineText.isVisible = true
            taglineText.text = enrichment.tagline
        } else {
            taglineText.isGone = true
        }
        if (synopsisText.text.isNullOrBlank() && !enrichment.overview.isNullOrBlank()) {
            bindSynopsis(enrichment.overview ?: "")
        }
        // Rebuild meta row including the TMDb rating badge.
        rebuildMetaRow(enrichment)
        if (enrichment.cast.isNotEmpty()) {
            castHeader.isVisible = true
            castGrid.isVisible = true
            castAdapter.clear()
            castAdapter.addAll(0, enrichment.cast)
        } else {
            castHeader.isGone = true
            castGrid.isGone = true
        }
        // Similar movies — fetched + intersected with local DB. Cached per tmdbId.
        if (enrichment.tmdbId > 0 && enrichment.tmdbId != lastSimilarTmdbId) {
            lastSimilarTmdbId = enrichment.tmdbId
            loadSimilarMovies(enrichment.tmdbId)
        }
    }

    private fun rebuildMetaRow(enrichment: MovieEnrichment?) {
        val movie = viewModel.uiState.value.movieWithMedia?.movie ?: return
        val parts = buildList {
            if (movie.year > 0) add(movie.year.toString())
            if (movie.duration.isNotBlank()) add(movie.duration)
            val rating = enrichment?.tmdbRating
            if (rating != null && rating > 0) {
                add("★ %.1f".format(rating))
            } else if (movie.rating.isNotBlank()) {
                add(movie.rating)
            }
            if (movie.language.isNotBlank()) add(movie.language.uppercase())
            enrichment?.certification?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        metaText.text = parts.joinToString("  •  ")
    }

    private fun loadSimilarMovies(tmdbId: Int) {
        viewLifecycleOwner.lifecycleScope.launch {
            val hints: List<SimilarHint> = runCatching {
                enrichmentRepository.fetchSimilarHints(tmdbId)
            }.getOrDefault(emptyList())
            if (hints.isEmpty()) {
                similarHeader.isGone = true
                similarGrid.isGone = true
                return@launch
            }
            val localMovies: List<Movie> = runCatching {
                movieRepository.getMovies().first()
            }.getOrDefault(emptyList())
            if (localMovies.isEmpty()) {
                similarHeader.isGone = true
                similarGrid.isGone = true
                return@launch
            }
            val byNorm = localMovies.associateBy { normalizeTitle(it.movieName) }
            val matches = mutableListOf<Movie>()
            val seen = mutableSetOf<String>()
            val currentId = viewModel.uiState.value.movieWithMedia?.movie?.id
            for (h in hints) {
                val m = byNorm[normalizeTitle(h.title)] ?: continue
                if (m.id == currentId) continue
                if (seen.add(m.id)) matches.add(m)
                if (matches.size >= 15) break
            }
            if (matches.isEmpty()) {
                similarHeader.isGone = true
                similarGrid.isGone = true
                return@launch
            }
            similarHeader.isVisible = true
            similarGrid.isVisible = true
            similarAdapter.clear()
            similarAdapter.addAll(0, matches)
        }
    }

    private fun normalizeTitle(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), "").trim()

    private fun startKenBurns() {
        kenBurnsAnimator?.cancel()
        backdrop.scaleX = 1f
        backdrop.scaleY = 1f
        kenBurnsAnimator = ValueAnimator.ofFloat(1f, 1.08f).apply {
            duration = 30_000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                val s = it.animatedValue as Float
                backdrop.scaleX = s
                backdrop.scaleY = s
            }
            start()
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun bindQualities(mwm: MovieWithMedia, selected: String?) {
        qualityRow.removeAllViews()
        val ctx = requireContext()
        val padH = dp(14)
        val padV = dp(6)
        val marginEnd = dp(12)
        mwm.media.forEach { media ->
            val btn = Button(ctx).apply {
                text = media.quality
                isAllCaps = false
                setBackgroundResource(
                    if (media.quality == selected) R.drawable.quality_chip_selected
                    else R.drawable.quality_chip_bg
                )
                setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                setPadding(padH, padV, padH, padV)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { viewModel.onQualitySelected(media.quality) }
            }
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = marginEnd
            qualityRow.addView(btn, lp)
        }
    }

    private fun startPlayback(resume: Boolean) {
        val mwm = viewModel.uiState.value.movieWithMedia ?: return
        val quality = viewModel.uiState.value.selectedQuality ?: mwm.media.firstOrNull()?.quality ?: return
        val media = mwm.media.find { it.quality == quality } ?: return
        val url = media.watchUrl1 ?: media.watchUrl2 ?: return
        if (!resume) viewModel.onStartOver()
        startActivity(
            PlayerActivity.newIntent(
                requireContext(),
                movieId = mwm.movie.id,
                title = mwm.movie.movieName,
                streamUrl = url,
                quality = quality
            )
        )
    }

    companion object {
        private const val MENU_START_OVER = 1
    }
}
