package com.kstream.tv.ui.details

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
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
import java.text.SimpleDateFormat
import java.util.Locale
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
    private lateinit var heroLandscape: ImageView
    private lateinit var logoImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var badgeNew: TextView
    private lateinit var taglineText: TextView
    private lateinit var metaText: TextView
    private lateinit var metaChipsRow: ViewGroup
    private lateinit var genreRow: ViewGroup
    private lateinit var synopsisText: TextView
    private lateinit var synopsisToggle: TextView
    private lateinit var directorText: TextView
    private lateinit var playButton: Button
    private lateinit var resumeGroup: View
    private lateinit var resumePill: View
    private lateinit var resumeCaret: View
    private var startOverTipPopup: PopupWindow? = null
    private lateinit var resumeLabel: TextView
    private lateinit var resumeProgressTrack: View
    private lateinit var resumeProgressFill: View
    private lateinit var resumeProgressLabel: TextView
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

    private val castAdapter = ArrayObjectAdapter(
        CastCardPresenter { cast ->
            // Person search — scope to ACTOR so results are grouped by actor.
            startActivity(
                com.kstream.tv.ui.search.SearchActivity.newIntent(
                    requireContext(),
                    cast.name,
                    com.kstream.feature.search.SearchScope.ACTOR.name
                )
            )
        }
    )
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
        heroLandscape = view.findViewById(R.id.hero_landscape)
        logoImage = view.findViewById(R.id.details_logo)
        titleText = view.findViewById(R.id.details_title)
        badgeNew = view.findViewById(R.id.badge_new)
        taglineText = view.findViewById(R.id.details_tagline)
        metaText = view.findViewById(R.id.details_meta)
        metaChipsRow = view.findViewById(R.id.meta_chips_row)
        genreRow = view.findViewById(R.id.genre_row)
        synopsisText = view.findViewById(R.id.details_synopsis)
        synopsisToggle = view.findViewById(R.id.synopsis_toggle)
        directorText = view.findViewById(R.id.details_director)
        playButton = view.findViewById(R.id.btn_play)
        resumeGroup = view.findViewById(R.id.resume_group)
        resumePill = view.findViewById(R.id.resume_pill)
        resumeCaret = view.findViewById(R.id.resume_caret)
        resumeLabel = view.findViewById(R.id.resume_label)
        resumeProgressTrack = view.findViewById(R.id.resume_progress_track)
        resumeProgressFill = view.findViewById(R.id.resume_progress_fill)
        resumeProgressLabel = view.findViewById(R.id.resume_progress_label)
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
        // ↺ Start Over: dedicated action, no dropdown. Click/OK on the
        // right segment restarts from the beginning immediately.
        resumeCaret.setOnClickListener { startPlayback(resume = false) }
        resumeCaret.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) showStartOverTip(v) else dismissStartOverTip()
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
        dismissStartOverTip()
        super.onDestroyView()
    }

    /**
     * Floating "Start Over" chip anchored just above the resume caret.
     * Shown only while the caret holds focus; uses a PopupWindow so it does
     * not push or reflow the existing button row.
     */
    private fun showStartOverTip(anchor: View) {
        dismissStartOverTip()
        val ctx = context ?: return
        val dm = resources.displayMetrics
        val tip = TextView(ctx).apply {
            text = getString(R.string.details_start_over)
            setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_start_over_tip)
            setPadding(
                (10 * dm.density).toInt(),
                (4 * dm.density).toInt(),
                (10 * dm.density).toInt(),
                (4 * dm.density).toInt()
            )
        }
        val popup = PopupWindow(
            tip,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isClippingEnabled = false
            isFocusable = false
            isTouchable = false
            isOutsideTouchable = false
        }
        // Measure so we can horizontally center over the caret.
        tip.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val tipW = tip.measuredWidth
        val tipH = tip.measuredHeight
        val gap = (6 * dm.density).toInt()
        val xOffset = (anchor.width - tipW) / 2
        val yOffset = -(anchor.height + tipH + gap)
        popup.showAsDropDown(anchor, xOffset, yOffset, Gravity.START or Gravity.TOP)
        startOverTipPopup = popup
    }

    private fun dismissStartOverTip() {
        startOverTipPopup?.dismiss()
        startOverTipPopup = null
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshWatchProgress()
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
        val alreadyWatched = state.isAlreadyWatched
        val showResumeGroup = hasProgress || alreadyWatched
        resumeGroup.isVisible = showResumeGroup
        playButton.isVisible = !showResumeGroup
        if (hasProgress) {
            bindResumeProgress(state.watchProgressPercent)
            resumeLabel.text = getString(R.string.details_resume)
            resumeProgressTrack.isVisible = true
            resumeProgressLabel.isVisible = true
        } else if (alreadyWatched) {
            resumeLabel.text = getString(R.string.details_already_watched)
            resumeProgressTrack.isVisible = false
            resumeProgressLabel.isVisible = false
        }
        // Keep focus sensible when toggling between Play and Resume.
        if (showResumeGroup && playButton.isFocused) resumePill.requestFocus()
        else if (!showResumeGroup && (resumePill.isFocused || resumeCaret.isFocused)) playButton.requestFocus()
        likeButton.text = if (state.isLiked) getString(R.string.details_liked) else getString(R.string.details_like)

        // Defer initial focus until content is actually loaded so the first
        // D-pad press always lands on a meaningful action.
        if (!initialFocusRequested) {
            initialFocusRequested = true
            (if (showResumeGroup) resumePill else playButton).requestFocus()
        }
    }

    private fun bindResumeProgress(percent: Float) {
        val pct = percent.coerceIn(0f, 100f)
        val run = Runnable {
            val w = resumeProgressTrack.width
            if (w <= 0) return@Runnable
            val lp = resumeProgressFill.layoutParams
            lp.width = ((w * pct) / 100f).toInt().coerceAtLeast(1)
            resumeProgressFill.layoutParams = lp
        }
        if (resumeProgressTrack.width > 0) run.run() else resumeProgressTrack.post(run)
        resumeProgressLabel.text = "%d %% watched".format(pct.toInt())
    }

    private fun bindMovie(mwm: MovieWithMedia) {
        val movie: Movie = mwm.movie
        titleText.text = movie.movieName
        badgeNew.isVisible = isRecentlyAdded(movie.lastUpdated)
        bindVibeTags(movie.genres)
        bindDirector(movie.director)
        bindSynopsis(movie.synopsis)
        // Local poster fallback only when we don't yet have a TMDb backdrop.
        if (lastBackdropUrl == null && movie.posterUrl.isNotBlank()) {
            loadBackdrop(movie.posterUrl)
            loadHeroLandscape(localPoster = movie.posterUrl, backdropUrl = null)
        }
        // Always populate the chip-based meta row from local data — enrichment
        // refresh later replaces it via rebuildMetaRow().
        rebuildMetaRow(enrichment = null)
    }

    /**
     * Vibe-tag row under the meta chips. Derived from local `genres` —
     * styled like lowercase tags separated by middle dots. Future
     * enhancement: TMDb keywords when available.
     */
    private fun bindVibeTags(genres: List<String>) {
        genreRow.removeAllViews()
        val tags = genres.take(4).filter { it.isNotBlank() }
        if (tags.isEmpty()) {
            genreRow.visibility = View.GONE
            return
        }
        genreRow.visibility = View.VISIBLE
        val ctx = requireContext()
        val dotMargin = dp(6)
        tags.forEachIndexed { idx, g ->
            val tag = TextView(ctx).apply {
                text = g.lowercase(Locale.getDefault())
                setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                isAllCaps = true
                letterSpacing = 0.10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            genreRow.addView(tag)
            if (idx < tags.lastIndex) {
                val dot = TextView(ctx).apply {
                    text = "·"
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_tertiary))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.marginStart = dotMargin
                lp.marginEnd = dotMargin
                genreRow.addView(dot, lp)
            }
        }
    }

    private fun bindDirector(directors: List<String>) {
        val nonEmpty = directors.filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) {
            directorText.isGone = true
            directorText.setOnClickListener(null)
            directorText.isClickable = false
            directorText.isFocusable = false
            return
        }
        directorText.isVisible = true

        // Build "Directed by Alice, Bob" where each name is its own clickable
        // span — D-pad / touch on a single name launches a search for *that*
        // director. Spans are individually focusable via LinkMovementMethod.
        val prefix = getString(R.string.details_directed_by, "").trimEnd()
        val builder = android.text.SpannableStringBuilder()
        if (prefix.isNotEmpty()) {
            builder.append(prefix).append(' ')
        }
        val accent = ContextCompat.getColor(requireContext(), R.color.accent_primary)
        nonEmpty.forEachIndexed { idx, name ->
            if (idx > 0) builder.append(", ")
            val start = builder.length
            builder.append(name)
            val end = builder.length
            val span = object : android.text.style.ClickableSpan() {
                override fun onClick(widget: View) {
                    startActivity(
                        com.kstream.tv.ui.search.SearchActivity.newIntent(
                            requireContext(),
                            name,
                            com.kstream.feature.search.SearchScope.DIRECTOR.name
                        )
                    )
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.color = accent
                    ds.isUnderlineText = false
                    ds.isFakeBoldText = true
                }
            }
            builder.setSpan(span, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        directorText.text = builder
        directorText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        directorText.highlightColor = android.graphics.Color.TRANSPARENT
        directorText.isFocusable = true
        directorText.isClickable = true
        directorText.setBackgroundResource(R.drawable.focus_ring_low)
        directorText.setPadding(dp(6), dp(2), dp(6), dp(2))
        // Top-level click (fallback): when there's only one director the
        // span handles it; when multiple, leave to LinkMovementMethod.
        if (nonEmpty.size == 1) {
            directorText.setOnClickListener {
                startActivity(
                    com.kstream.tv.ui.search.SearchActivity.newIntent(
                        requireContext(),
                        nonEmpty.first(),
                        com.kstream.feature.search.SearchScope.DIRECTOR.name
                    )
                )
            }
        } else {
            directorText.setOnClickListener(null)
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

    /**
     * Hero image pinned top-right of the glass card. Prefer the TMDb
     * backdrop (16:9); fall back to the local portrait poster (2:3) by
     * resizing the ImageView so the artwork isn't squished.
     */
    private fun loadHeroLandscape(localPoster: String?, backdropUrl: String?) {
        val url = backdropUrl?.takeIf { it.isNotBlank() } ?: localPoster?.takeIf { it.isNotBlank() }
        if (url.isNullOrBlank()) {
            heroLandscape.isGone = true
            return
        }
        heroLandscape.isVisible = true
        // Landscape (16:9) when we have a backdrop; portrait fallback
        // (2:3) is taller and narrower so the poster artwork keeps its
        // intended aspect ratio.
        val isLandscape = !backdropUrl.isNullOrBlank()
        val lp = heroLandscape.layoutParams
        lp.width = dp(if (isLandscape) 280 else 130)
        lp.height = dp(if (isLandscape) 158 else 195)
        heroLandscape.layoutParams = lp
        Glide.with(this).load(url)
            .placeholder(R.drawable.bg_hero_landscape)
            .error(R.drawable.bg_hero_landscape)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade(200))
            .into(heroLandscape)
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
        // Hero (top-right card image): TMDb backdrop preferred, local
        // poster fallback. Recomputed whenever enrichment arrives so we
        // upgrade from local poster → TMDb backdrop seamlessly.
        val movie = viewModel.uiState.value.movieWithMedia?.movie
        loadHeroLandscape(localPoster = movie?.posterUrl, backdropUrl = firstBackdrop)

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
        // NEW badge lives in the hero stack (top-right of the glass card)
        // so it remains visible whether the title is text or a TMDb logo.
        badgeNew.isVisible = isRecentlyAdded(movie?.lastUpdated.orEmpty())
        if (!enrichment.tagline.isNullOrBlank()) {
            taglineText.isVisible = true
            taglineText.text = enrichment.tagline
        } else {
            taglineText.isGone = true
        }
        if (synopsisText.text.isNullOrBlank() && !enrichment.overview.isNullOrBlank()) {
            bindSynopsis(enrichment.overview ?: "")
        }
        // Rebuild meta row including the TMDb rating + certification.
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

    /**
     * Chip-based meta row inside the glass card. Builds a row of small
     * pill chips for: type (Movie/Show/Doc — gradient style), year,
     * duration, ★ rating (TMDb when available), language (uppercase),
     * certification (TMDb when available, else local rating fallback).
     */
    private fun rebuildMetaRow(enrichment: MovieEnrichment?) {
        val movie = viewModel.uiState.value.movieWithMedia?.movie ?: return
        metaChipsRow.removeAllViews()
        val ctx = requireContext()
        val padH = dp(10)
        val padV = dp(4)
        val marginEnd = dp(8)

        fun addChip(text: String, type: Boolean = false, rating: Boolean = false) {
            if (text.isBlank()) return
            val chip = TextView(ctx).apply {
                this.text = text
                setBackgroundResource(
                    when {
                        type -> R.drawable.bg_chip_type_v2
                        else -> R.drawable.bg_chip_meta
                    }
                )
                setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (rating) R.color.accent_warm else R.color.text_secondary
                    )
                )
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
            metaChipsRow.addView(chip, lp)
        }

        val type = movie.type.trim()
        if (type.isNotEmpty()) {
            addChip(
                type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                type = true
            )
        }
        if (movie.year > 0) addChip(movie.year.toString())
        if (movie.duration.isNotBlank()) addChip(movie.duration)
        val tmdbRating = enrichment?.tmdbRating
        if (tmdbRating != null && tmdbRating > 0) {
            addChip("★ %.1f".format(tmdbRating), rating = true)
        }
        if (movie.language.isNotBlank()) addChip(movie.language.uppercase(Locale.getDefault()))
        val cert = enrichment?.certification?.takeIf { it.isNotBlank() } ?: movie.rating
        if (cert.isNotBlank()) addChip(cert)
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
        val padH = dp(12)
        val padV = dp(6)
        val marginEnd = dp(8)
        // Highest quality (by ranking) is the de-facto "primary" — pre-select
        // it visually if nothing is selected yet so the row has a clear focus
        // candidate when entering it.
        val effectiveSelected = selected ?: mwm.media.maxByOrNull { qualityRank(it.quality) }?.quality
        mwm.media.forEach { media ->
            val isSelected = media.quality == effectiveSelected
            val btn = Button(ctx).apply {
                text = media.quality
                isAllCaps = false
                setBackgroundResource(
                    if (isSelected) R.drawable.bg_q_badge_selected
                    else R.drawable.bg_q_badge
                )
                setTextColor(ContextCompat.getColor(ctx, android.R.color.white))
                setPadding(padH, padV, padH, padV)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textSize = 12f
                isFocusable = true
                isFocusableInTouchMode = true
                minHeight = 0
                minimumHeight = 0
                stateListAnimator = null
                setOnClickListener { viewModel.onQualitySelected(media.quality) }
            }
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(30)
            )
            lp.marginEnd = marginEnd
            qualityRow.addView(btn, lp)
        }
    }

    private fun qualityRank(q: String): Int {
        val digits = q.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    /**
     * Same recency window as MovieCardPresenter / HomePreviewBinder so the
     * NEW badge on details matches what the user saw on home.
     */
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
        private const val RECENT_DAYS = 10L
        private val recentDateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)
    }
}
