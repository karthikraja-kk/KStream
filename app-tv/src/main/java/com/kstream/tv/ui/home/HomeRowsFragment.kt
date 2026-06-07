package com.kstream.tv.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import androidx.leanback.widget.RowHeaderView
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kstream.core.enrichment.EnrichmentRepository
import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.model.Movie
import com.kstream.feature.home.HomeUiState
import com.kstream.feature.home.HomeViewModel
import com.kstream.feature.home.MovieRail
import com.kstream.tv.ui.home.presenter.MovieCardPresenter
import com.kstream.tv.ui.home.presenter.SeeMoreCard
import com.kstream.tv.ui.home.presenter.SeeMorePresenter
import com.kstream.tv.ui.search.SearchActivity
import com.kstream.tv.ui.splash.HomePrewarmTask
import com.kstream.tv.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Leanback [RowsSupportFragment] that renders the Home rails (no chrome).
 *
 * HS-P5: prefetches TMDb enrichment for all visible rail movies (capped at
 * [PREFETCH_CAP]) so landscape backdrops and tagline metadata are ready by the
 * time the user focuses a card. A bounded [Semaphore] keeps concurrent TMDb
 * requests to [PREFETCH_PARALLELISM] to avoid hammering the API.
 *
 * The fetched enrichment map is pushed into [MovieCardPresenter] so already-
 * created card view holders can pick up backdrops on next focus event without
 * forcing a full rebind (which would lose focus).
 */
@AndroidEntryPoint
class HomeRowsFragment : RowsSupportFragment() {

    @Inject lateinit var focusedRelay: FocusedMovieRelay
    @Inject lateinit var enrichmentRepository: EnrichmentRepository
    @Inject lateinit var homePrewarmTask: HomePrewarmTask

    private val viewModel: HomeViewModel by viewModels({ requireActivity() })

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter().apply {
        shadowEnabled = false
        selectEffectEnabled = false
        // Suppress per-row headers entirely — the sticky section label
        // above the rails (see activity_main.xml > section_title_label)
        // shows the active rail name. The HeaderItem is still kept on
        // each ListRow so onItemViewSelectedListener can read row.headerItem
        // for position tracking, but no header view is rendered.
        headerPresenter = null
    })
    private val cardPresenter: MovieCardPresenter by lazy {
        MovieCardPresenter(focusedRelay)
    }
    private val seeMorePresenter = SeeMorePresenter()
    private val itemPresenterSelector: ClassPresenterSelector by lazy {
        ClassPresenterSelector()
            .addClassPresenter(Movie::class.java, cardPresenter)
            .addClassPresenter(SeeMoreCard::class.java, seeMorePresenter)
    }

    private val boundRailKeys = mutableListOf<String>()

    @Volatile
    var currentSelectedRow: Int = 0
        private set

    private val movieClickHandlers = mutableSetOf<(Movie) -> Unit>()

    /**
     * Section change callback. Fired with the currently focused rail's
     * title whenever D-pad focus moves to a different row, so the host
     * activity can update its sticky section name label (the "Hotstar
     * placeholder" between the preview and the rails).
     */
    var onSectionChanged: ((String) -> Unit)? = null
    private val railTitles = mutableListOf<String>()

    // HS-P5 prefetch state. Both maps/sets are touched only from the main
    // dispatcher (the lifecycleScope launches), so no lock needed.
    private val enrichmentMap: MutableMap<String, MovieEnrichment> = mutableMapOf()
    private val observedMovieIds: MutableSet<String> = mutableSetOf()
    private var prefetchJob: Job? = null
    private val tmdbSemaphore = Semaphore(PREFETCH_PARALLELISM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = rowsAdapter
        seeMorePresenter.onClick = { card ->
            startActivity(SearchActivity.newIntent(requireContext(), card.query))
        }
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is Movie -> movieClickHandlers.forEach { it(item) }
                is SeeMoreCard -> startActivity(SearchActivity.newIntent(requireContext(), item.query))
            }
        }
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, row: Row? ->
            val pos = row?.headerItem?.id?.toInt() ?: 0
            if (pos != currentSelectedRow) {
                currentSelectedRow = pos
                // Update the sticky section label whenever focus crosses
                // into a new rail.
                railTitles.getOrNull(pos)?.let { onSectionChanged?.invoke(it) }
            }
            (item as? Movie)?.let { focusedRelay.push(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(Color.TRANSPARENT)

        // Adaptive inter-row spacing — keeps rails tight on 720p panels
        // (540dp screen → ~7dp gap) and gives a bit more breathing room
        // on 4K (1080dp → ~13dp).
        val dm = resources.displayMetrics
        val screenHeightDp = dm.heightPixels / dm.density
        val interRowDp = (screenHeightDp * 0.012f).coerceIn(2f, 12f)
        val interRowPx = (interRowDp * dm.density).toInt()
        verticalGridView?.setItemSpacing(interRowPx)
        // Let the focus ring on each tile draw OUTSIDE the tile bounds —
        // Leanback's grids default to clipping, which would crop the ring
        // back to the tile edge and make it look "smaller than the tile".
        verticalGridView?.clipChildren = false
        verticalGridView?.clipToPadding = false

        // Carousel-mode safety: each inner HorizontalGridView (the per-row
        // RecyclerView with id row_content) must have its ItemAnimator
        // disabled. Otherwise DefaultItemAnimator will run its own fade/move
        // animation on a view that MovieCardPresenter is simultaneously
        // animating via AnimatorSet, which crashes with
        // "Tmp detached view should be removed before it can be recycled".
        //
        // While we're attaching to each row, also apply the inter-tile
        // horizontal spacing (the layout-time gap between cards is otherwise
        // ~0 because Leanback's HorizontalGridView defaults to zero). Tagged
        // so we only setItemSpacing ONCE per HGV — repeating it on every
        // attach would force a requestLayout each time, contributing to
        // main-thread thrash and ANRs on first-install.
        val spacingPx = resources.getDimensionPixelSize(R.dimen.card_poster_spacing)
        val spacingTagId = R.id.card_poster_spacing_applied
        verticalGridView?.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(rowView: View) {
                    val hgv = rowView.findViewById<RecyclerView>(
                        androidx.leanback.R.id.row_content
                    )
                    hgv?.itemAnimator = null
                    // Same anti-clip treatment for the per-row horizontal grid
                    // so the focus ring can extend past the tile edge.
                    hgv?.clipChildren = false
                    hgv?.clipToPadding = false
                    (rowView as? ViewGroup)?.let {
                        it.clipChildren = false
                        it.clipToPadding = false
                    }
                    val grid = hgv as? androidx.leanback.widget.HorizontalGridView
                    if (grid != null && grid.getTag(spacingTagId) == null) {
                        grid.setItemSpacing(spacingPx)
                        grid.setTag(spacingTagId, true)
                    }
                }
                override fun onChildViewDetachedFromWindow(rowView: View) = Unit
            }
        )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    render(state)
                    schedulePrefetch(state.rails)
                }
            }
        }

        // Show shimmer while we wait for the first non-empty rails set. We
        // intentionally do NOT gate on homePrewarmTask: prewarm is a silent
        // background optimization that fills Glide's disk cache, but the
        // shimmer must lift the moment we have something to draw — otherwise
        // a slow TMDb response (first install + cold network) could keep
        // shimmer up for the entire 25s timeout, and the home screen would
        // be doing so much background work in the meantime that the main
        // thread starves and ANRs.
        ShimmerOverlay.show(requireActivity())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .onEach { state ->
                        if (state.rails.isNotEmpty()) {
                            ShimmerOverlay.hide(requireActivity())
                        }
                    }
                    .launchIn(this)
            }
        }
    }

    fun setOnMovieClicked(handler: (Movie) -> Unit) {
        movieClickHandlers += handler
    }

    private var didInitialFocus: Boolean = false

    private suspend fun render(state: HomeUiState) {
        cardPresenter.updateProgress(state.watchProgressMap)
        // Seed the enrichment map BEFORE binding so the very first
        // onBindViewHolder call sees TMDb URLs and ImageFallback picks
        // the TMDb poster (already on Glide's disk from a prior session).
        // One batched DAO read for the entire screen; ~ms-class on Fire TV.
        if (state.rails.isNotEmpty()) {
            val allMovies = state.rails.asSequence()
                .flatMap { it.movies.asSequence() }
                .distinctBy { it.id }
                .toList()
            val cached = runCatching { enrichmentRepository.getMany(allMovies) }
                .getOrDefault(emptyMap())
            if (cached.isNotEmpty()) {
                enrichmentMap.putAll(cached)
                cardPresenter.updateEnrichment(enrichmentMap.toMap())
            }
        }
        diffRailsInto(rowsAdapter, state.rails)
        // P4: cold-start focus → first tile of the first rail. We only do this
        // once per fragment instance so subsequent state updates don't yank
        // focus away from wherever the user has navigated to.
        if (!didInitialFocus && state.rails.isNotEmpty()) {
            didInitialFocus = true
            view?.post {
                setSelectedPosition(0, true)
            }
        }
    }

    // ------------------------------------------------------------------
    // HS-P5: enrichment prefetch
    // ------------------------------------------------------------------
    private fun schedulePrefetch(rails: List<MovieRail>) {
        val visibleMovies = rails.asSequence()
            .flatMap { it.movies.asSequence() }
            .distinctBy { it.id }
            .take(PREFETCH_CAP)
            .toList()
        if (visibleMovies.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = viewLifecycleOwner.lifecycleScope.launch {
            // 1) For movies we haven't observed yet, attach a cache-observer
            //    so any enrichment write (existing OR new from ensureCached)
            //    immediately flows into our map and into the presenter.
            visibleMovies.forEach { movie ->
                if (observedMovieIds.add(movie.id)) {
                    enrichmentRepository.observe(movie)
                        .onEach { enrichment ->
                            if (enrichment != null) {
                                val previouslyMissing = !enrichmentMap.containsKey(movie.id)
                                enrichmentMap[movie.id] = enrichment
                                cardPresenter.updateEnrichment(enrichmentMap.toMap())
                                // If this is the first time enrichment arrived
                                // for this movie, the on-screen card was bound
                                // with the DB-poster fallback. Tell Leanback to
                                // re-bind so it picks up the TMDb URL now.
                                if (previouslyMissing) {
                                    notifyMovieChanged(movie.id)
                                }
                            }
                        }
                        .launchIn(viewLifecycleOwner.lifecycleScope)
                }
            }

            // 2) Kick off TMDb fetches for the visible movies, bounded so we
            //    don't slam the API.
            visibleMovies.forEach { movie ->
                launch {
                    tmdbSemaphore.withPermit {
                        runCatching { enrichmentRepository.ensureCached(movie) }
                    }
                }
            }
        }
    }

    private fun diffRailsInto(target: ArrayObjectAdapter, rails: List<MovieRail>) {
        val newKeys = rails.map { it.title }
        // Keep the parallel titles list in sync so the sticky section
        // label can be looked up by row position from onItemViewSelected.
        railTitles.clear()
        railTitles.addAll(newKeys)
        if (newKeys != boundRailKeys) {
            target.clear()
            rails.forEachIndexed { index, rail ->
                // HeaderItem id carries the row position back via
                // onItemViewSelectedListener, but the header view itself
                // is suppressed: ListRowPresenter renders no per-row
                // header now that we have a sticky placeholder above.
                val header = HeaderItem(index.toLong(), rail.title)
                val inner = ArrayObjectAdapter(itemPresenterSelector).apply {
                    addAll(0, rail.movies)
                    rail.seeMoreQuery?.takeIf { rail.movies.size > SEE_MORE_MIN_TILES }?.let {
                        add(SeeMoreCard(it, rail.title))
                    }
                }
                target.add(ListRow(header, inner))
            }
            boundRailKeys.clear()
            boundRailKeys.addAll(newKeys)
            // Seed the sticky label with the first rail's title so the
            // placeholder isn't blank before any focus event fires.
            railTitles.firstOrNull()?.let { onSectionChanged?.invoke(it) }
            return
        }
        rails.forEachIndexed { index, rail ->
            val row = target.get(index) as? ListRow ?: return@forEachIndexed
            val inner = row.adapter as? ArrayObjectAdapter ?: return@forEachIndexed
            replaceMovies(inner, rail.movies, rail.seeMoreQuery, rail.title)
        }
    }

    private fun replaceMovies(
        inner: ArrayObjectAdapter,
        movies: List<Movie>,
        seeMoreQuery: String?,
        railTitle: String
    ) {
        val showSeeMore = seeMoreQuery != null && movies.size > SEE_MORE_MIN_TILES
        val expectedSize = movies.size + if (showSeeMore) 1 else 0
        val sameContent = inner.size() == expectedSize && (0 until movies.size).all { i ->
            (inner.get(i) as? Movie)?.id == movies[i].id
        }
        if (sameContent) return
        inner.clear()
        inner.addAll(0, movies)
        if (showSeeMore && seeMoreQuery != null) {
            inner.add(SeeMoreCard(seeMoreQuery, railTitle))
        }
    }

    /**
     * Walks every rail and tells Leanback to re-bind any card whose movie id
     * matches. A single movie can appear in multiple rails (e.g. New Releases
     * + Genre), so we notify every occurrence rather than break on first hit.
     */
    private fun notifyMovieChanged(movieId: String) {
        for (rowIdx in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(rowIdx) as? ListRow ?: continue
            val inner = row.adapter as? ArrayObjectAdapter ?: continue
            for (i in 0 until inner.size()) {
                val item = inner.get(i)
                if (item is Movie && item.id == movieId) {
                    inner.notifyArrayItemRangeChanged(i, 1)
                }
            }
        }
    }

    companion object {
        // Reduced from 60 → 20 to keep first-install responsive. 20 tiles
        // covers above-the-fold (3 rails × ~5 visible) + a buffer; the rest
        // stream in lazily as the user scrolls. Higher caps caused 60
        // parallel ensureCached calls plus 60 Main-dispatcher observers,
        // which combined with Glide image decoding pegged the UI thread
        // long enough to ANR on cold start.
        private const val PREFETCH_CAP = 20
        private const val PREFETCH_PARALLELISM = 4

        /** "See more" tile only shows when the rail has STRICTLY more than this many movies. */
        private const val SEE_MORE_MIN_TILES = 4

        // Hard ceiling on how long the shimmer can hold the screen while we
        // wait for the splash prewarm. Generous enough to cover first-install
        // (sync + TMDb fetch for the above-the-fold tiles) but bounded so a
        // dead network never traps the user behind shimmer forever.
        private const val SHIMMER_PREWARM_TIMEOUT_MS = 25_000L
    }
}
