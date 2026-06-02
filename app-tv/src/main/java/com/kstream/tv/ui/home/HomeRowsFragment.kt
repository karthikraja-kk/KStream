package com.kstream.tv.ui.home

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kstream.core.model.Movie
import com.kstream.feature.home.HomeUiState
import com.kstream.feature.home.HomeViewModel
import com.kstream.feature.home.MovieRail
import com.kstream.tv.ui.home.presenter.MovieCardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Leanback [RowsSupportFragment] that renders the Home rails (no chrome).
 *
 * Sits below [HeroFragment] inside [com.kstream.tv.ui.main.MainActivity]. The
 * activity is responsible for routing D-pad UP from row 0 → hero buttons.
 *
 * P5 scope: rails only, focus handoff hook ([currentSelectedRow] is read by the
 * Activity's [android.view.KeyEvent] handler). P6 polishes the card; P7 wires
 * the click → Details navigation.
 *
 * Diffing strategy is the same as P4 — only the rail header KEYS trigger a
 * full rebuild; row contents are replaced in-place to preserve focus.
 */
@AndroidEntryPoint
class HomeRowsFragment : RowsSupportFragment() {

    private val viewModel: HomeViewModel by viewModels({ requireActivity() })

    private val rowsAdapter = ArrayObjectAdapter(ListRowPresenter().apply {
        shadowEnabled = false
        selectEffectEnabled = false
    })
    private val cardPresenter = MovieCardPresenter()

    private val boundRailKeys = mutableListOf<String>()

    /** Current top-level row index (read by MainActivity to decide UP-to-hero handoff). */
    @Volatile
    var currentSelectedRow: Int = 0
        private set

    private val movieClickHandlers = mutableSetOf<(Movie) -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = rowsAdapter
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            (item as? Movie)?.let { m -> movieClickHandlers.forEach { it(m) } }
        }
        onItemViewSelectedListener = OnItemViewSelectedListener { _, _, _, row: Row? ->
            val pos = row?.headerItem?.id?.toInt() ?: 0
            currentSelectedRow = pos
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Transparent background — gradient/scrim painted by MainActivity behind.
        view.setBackgroundColor(Color.TRANSPARENT)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }
    }

    fun setOnMovieClicked(handler: (Movie) -> Unit) {
        movieClickHandlers += handler
    }

    private fun render(state: HomeUiState) {
        diffRailsInto(rowsAdapter, state.rails)
    }

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

    private fun replaceMovies(inner: ArrayObjectAdapter, movies: List<Movie>) {
        val currentSize = inner.size()
        val sameContent = currentSize == movies.size && (0 until currentSize).all { i ->
            (inner.get(i) as? Movie)?.id == movies[i].id
        }
        if (sameContent) return
        inner.clear()
        inner.addAll(0, movies)
    }
}
