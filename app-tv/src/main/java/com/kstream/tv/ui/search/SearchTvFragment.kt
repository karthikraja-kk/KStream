package com.kstream.tv.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kstream.core.model.Movie
import com.kstream.feature.search.SearchUiState
import com.kstream.feature.search.SearchViewModel
import com.kstream.tv.R
import com.kstream.tv.ui.details.DetailsActivity
import com.kstream.tv.ui.home.presenter.MovieCardPresenter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchTvFragment : Fragment() {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var queryEdit: EditText
    private lateinit var grid: VerticalGridView
    private lateinit var emptyText: TextView
    private lateinit var loadingText: TextView

    private val cardPresenter by lazy {
        MovieCardPresenter().apply {
            onMovieClick = { movie -> startActivity(DetailsActivity.newIntent(requireContext(), movie)) }
        }
    }
    private val adapter = ArrayObjectAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search_tv, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        queryEdit = view.findViewById(R.id.search_input)
        grid = view.findViewById(R.id.search_grid)
        emptyText = view.findViewById(R.id.search_empty)
        loadingText = view.findViewById(R.id.search_loading)

        grid.setNumColumns(5)
        adapter.presenterSelector = androidx.leanback.widget.SinglePresenterSelector(cardPresenter)
        grid.adapter = ItemBridgeAdapter(adapter)

        queryEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onQueryChange(s?.toString().orEmpty())
            }
        })

        arguments?.getString(SearchActivity.ARG_INITIAL_QUERY)?.takeIf { it.isNotBlank() }?.let {
            viewModel.setInitialQuery(it)
            if (!it.contains(":")) queryEdit.setText(it)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }

        queryEdit.requestFocus()
    }

    private fun render(state: SearchUiState) {
        loadingText.isVisible = state.isLoading
        emptyText.isVisible = !state.isLoading && state.activeQuery.isNotBlank() && state.results.isEmpty()
        emptyText.text = state.error ?: getString(R.string.search_no_results)
        val current = (0 until adapter.size()).map { adapter.get(it) as Movie }
        if (current != state.results) {
            adapter.clear()
            adapter.addAll(0, state.results)
        }
    }
}
