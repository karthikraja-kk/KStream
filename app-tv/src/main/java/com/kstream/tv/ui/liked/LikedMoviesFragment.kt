package com.kstream.tv.ui.liked

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import com.kstream.tv.ui.common.AppConfirmDialog
import com.kstream.tv.R
import com.kstream.tv.ui.details.DetailsActivity
import com.kstream.tv.ui.personal.PersonalMovieGridAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LikedMoviesFragment : Fragment() {

    private val viewModel: LikedMoviesViewModel by viewModels()
    private lateinit var adapter: PersonalMovieGridAdapter
    private lateinit var topBar: View
    private lateinit var selectToolbar: View
    private lateinit var selectedCount: TextView
    private lateinit var selectButton: TextView
    private lateinit var selectAllButton: TextView
    private lateinit var clearButton: TextView
    private lateinit var deleteButton: TextView
    private lateinit var cancelButton: TextView
    private lateinit var grid: RecyclerView
    private lateinit var emptyState: View
    private lateinit var loading: View
    private var backCallback: OnBackPressedCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_liked_movies, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        topBar = view.findViewById(R.id.top_bar)
        selectToolbar = view.findViewById(R.id.select_toolbar)
        selectedCount = view.findViewById(R.id.selected_count)
        selectButton = view.findViewById(R.id.select_button)
        selectAllButton = view.findViewById(R.id.select_all_button)
        clearButton = view.findViewById(R.id.clear_button)
        deleteButton = view.findViewById(R.id.delete_button)
        cancelButton = view.findViewById(R.id.cancel_button)
        grid = view.findViewById(R.id.movie_grid)
        emptyState = view.findViewById(R.id.empty_state)
        loading = view.findViewById(R.id.loading_indicator)

        adapter = PersonalMovieGridAdapter(
            onClick = { item ->
                if (viewModel.uiState.value.selectMode) {
                    viewModel.toggleSelection(item.id)
                } else {
                    startActivity(DetailsActivity.newIntent(requireContext(), item.id))
                }
            },
            onLongClick = { item -> viewModel.enterSelectMode(item.id) }
        )
        grid.layoutManager = GridLayoutManager(requireContext(), 5)
        grid.adapter = adapter
        (grid.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        grid.addItemDecoration(GridSpacingDecoration(18, 22))

        selectButton.setOnClickListener { viewModel.enterSelectMode() }
        selectAllButton.setOnClickListener { viewModel.selectAll() }
        clearButton.setOnClickListener { viewModel.clearSelection() }
        cancelButton.setOnClickListener { viewModel.exitSelectMode() }
        deleteButton.setOnClickListener { confirmUnlike() }

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                viewModel.exitSelectMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { msg ->
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun render(state: LikedMoviesUiState) {
        val wasSelectMode = topBar.visibility != View.VISIBLE
        topBar.isVisible = !state.selectMode
        selectToolbar.isVisible = state.selectMode
        selectedCount.text = "${state.selectedIds.size} selected ·"
        val hasSelection = state.selectedIds.isNotEmpty()
        deleteButton.alpha = if (hasSelection) 1f else 0.32f
        deleteButton.isFocusable = hasSelection
        loading.isVisible = state.isLoading
        emptyState.isVisible = state.isEmpty && !state.isLoading
        grid.isVisible = !state.isEmpty && !state.isLoading
        backCallback?.isEnabled = state.selectMode
        adapter.submitList(state.items)
        adapter.applySelection(state.selectMode, state.selectedIds)
        if (state.selectMode && !wasSelectMode) {
            selectToolbar.post { selectAllButton.requestFocus() }
        } else if (!state.selectMode && wasSelectMode) {
            topBar.post { selectButton.requestFocus() }
        }
    }

    private fun confirmUnlike() {
        val count = viewModel.uiState.value.selectedIds.size
        if (count == 0) {
            Toast.makeText(requireContext(), "Select movies first", Toast.LENGTH_SHORT).show()
            return
        }
        AppConfirmDialog.show(
            context = requireContext(),
            title = "Unlike movies",
            message = "Unlike $count movies?",
            positiveLabel = "Unlike",
            onConfirm = { viewModel.unlikeSelected() }
        )
    }

    private class GridSpacingDecoration(
        private val horizontal: Int,
        private val vertical: Int
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.set(horizontal / 2, vertical / 2, horizontal / 2, vertical / 2)
        }
    }
}
