package com.kstream.tv.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kstream.core.model.Movie
import com.kstream.feature.home.HomeUiState
import com.kstream.feature.home.HomeViewModel
import com.kstream.feature.home.MovieRail
import com.kstream.tv.R
import com.kstream.tv.ui.home.presenter.MovieCardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Leanback [BrowseSupportFragment] that renders the Home rails.
 *
 * P4 scope:
 *  - One [ListRow] per [MovieRail] from [HomeViewModel.uiState].
 *  - Headers visible (left side) so the user has predictable row navigation;
 *    P5 will add the hero carousel above the rails.
 *  - Click on any card is a defined no-op for now; Details is wired in P7.
 *  - Diffs adapter contents on every state emission rather than rebuilding —
 *    rebuilding would reset focus to the first row.
 */
@AndroidEntryPoint
class HomeBrowseFragment : BrowseSupportFragment() {

    private val viewModel: HomeViewModel by viewModels()

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val cardPresenter = MovieCardPresenter()

    /** Mirror of currently-bound rail keys so we can diff vs the next state. */
    private val boundRailKeys = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureBrowseHeader()
        adapter = rowsAdapter
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            (item as? Movie)?.let { onMovieClicked(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }
    }

    private fun configureBrowseHeader() {
        title = getString(R.string.app_name)
        searchAffordanceColor = Color.TRANSPARENT
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = resources.getColor(R.color.bg_base, requireContext().theme)
    }

    private fun render(state: HomeUiState) {
        diffRailsInto(rowsAdapter, state.rails)
    }

    /**
     * Diffs [rails] into [target]:
     *  - Rebuild only if the set/order of rail titles changed.
     *  - Otherwise replace each row's inner adapter contents in place so
     *    Leanback keeps focus / scroll position (ListRow identity preserved).
     */
    private fun diffRailsInto(target: ArrayObjectAdapter, rails: List<MovieRail>) {
        val newKeys = rails.map { it.title }
        if (newKeys != boundRailKeys) {
            target.clear()
            rails.forEachIndexed { index, rail ->
                val header = HeaderItem(index.toLong(), rail.title)
                val inner = ArrayObjectAdapter(cardPresenter).apply { addAll(0, rail.movies) }
                target.add(ListRow(header, inner))
            }
            boundRailKeys.clear()
            boundRailKeys.addAll(newKeys)
            return
        }
        rails.forEachIndexed { index, rail ->
            val row = target.get(index) as? ListRow ?: return@forEachIndexed
            val inner = row.adapter as? ArrayObjectAdapter ?: return@forEachIndexed
            replaceMovies(inner, rail.movies)
        }
    }

    /** Replace [inner]'s items with [movies] if the id sequence differs. */
    private fun replaceMovies(inner: ArrayObjectAdapter, movies: List<Movie>) {
        val currentSize = inner.size()
        val sameSize = currentSize == movies.size
        val sameContent = sameSize && (0 until currentSize).all { i ->
            (inner.get(i) as? Movie)?.id == movies[i].id
        }
        if (sameContent) return
        inner.clear()
        inner.addAll(0, movies)
    }

    private fun onMovieClicked(movie: Movie) {
        // P7 wires DetailsActivity. Intentional no-op stub.
    }
}
