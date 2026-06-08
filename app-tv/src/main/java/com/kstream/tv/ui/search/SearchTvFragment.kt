package com.kstream.tv.ui.search

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.leanback.widget.VerticalGridView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.kstream.core.model.Movie
import com.kstream.feature.search.SearchScope
import com.kstream.feature.search.SearchUiState
import com.kstream.feature.search.SearchViewModel
import com.kstream.feature.search.SortCategory
import com.kstream.feature.search.SortDirection
import com.kstream.feature.search.SortOption
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
    private lateinit var clearBtn: ImageButton
    private lateinit var voiceBtn: ImageButton
    private lateinit var scopeIcon: ImageButton
    private lateinit var sortIcon: ImageButton
    private lateinit var grid: VerticalGridView
    private lateinit var resultsContainer: View
    private lateinit var resultsCount: TextView
    private lateinit var typeaheadContainer: View
    private lateinit var typeaheadList: LinearLayout
    private lateinit var typeaheadHeader: TextView
    private lateinit var typeaheadShowAll: TextView
    private lateinit var recentsContainer: View
    private lateinit var recentsRow: LinearLayout
    private lateinit var recentsClear: TextView
    private lateinit var suggestBanner: TextView
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView
    private lateinit var scopePanel: LinearLayout
    private lateinit var sortPanel: LinearLayout

    /**
     * When the user picks a grouped suggestion (e.g. "Christopher Nolan"),
     * we want the results grid to wear a "Movies of <name>" / "Movies from
     * <year>" banner instead of the generic "N results" line. We hold the
     * label here and clear it as soon as the user types again.
     */
    private var pinnedFilterLabel: String? = null
    private var pinnedFilterResults: List<Movie>? = null

    private var gridColumns: Int = 5

    private val cardPresenter by lazy {
        // showTitle = true so the search grid shows each movie's name under
        // the poster (there is no sticky preview pane here to provide it).
        MovieCardPresenter(showTitle = true, compact = true).apply {
            onMovieClick = { movie ->
                viewModel.onMovieClick(movie) {
                    startActivity(DetailsActivity.newIntent(requireContext(), movie))
                }
            }
        }
    }
    private val adapter = ArrayObjectAdapter()

    /** Suppresses TextWatcher feedback when we update EditText programmatically (e.g. voice). */
    private var suppressTextWatcher = false

    /** In-process speech recognizer (fallback when intent-based RECOGNIZE_SPEECH has no handler). */
    private var inAppRecognizer: SpeechRecognizer? = null

    private val voiceLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val matches: List<String>? = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotEmpty()) applySpokenText(spoken)
            }
        }

    private val recordPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startInAppRecognizer()
            else Toast.makeText(requireContext(), R.string.search_voice_unavailable, Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search_tv, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        wireInput()
        wireGrid()
        wireRecentsClear()
        wireVoice()
        wireScopeSortMenus()
        wireFocusChain()

        arguments?.getString(SearchActivity.ARG_INITIAL_SCOPE)?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { SearchScope.valueOf(raw) }.getOrNull()?.let { viewModel.setScope(it) }
        }
        arguments?.getString(SearchActivity.ARG_INITIAL_QUERY)?.takeIf { it.isNotBlank() }?.let {
            viewModel.setInitialQuery(it)
            if (!it.contains(":")) {
                suppressTextWatcher = true
                queryEdit.setText(it)
                queryEdit.setSelection(it.length)
                suppressTextWatcher = false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }

        queryEdit.requestFocus()
    }

    override fun onDestroyView() {
        inAppRecognizer?.destroy()
        inAppRecognizer = null
        super.onDestroyView()
    }

    /**
     * D-pad navigation chain so the user can always traverse without
     * dead-ends:
     *
     *     [scope] ↔ [sort] ↔ [voice] ↔ [input pill] ↔ [clear]
     *                              ↕
     *     grid  /  typeahead rows  /  recent chips
     *
     * Grid's first row → UP → search input is wired via an intercept
     * listener (BaseGridView's internal focus search prefers staying
     * within the grid, so nextFocusUpId alone isn't enough).
     * LEFT from the leading edge of any focusable hands off to the
     * side nav (handled in the activity-level dispatchKeyEvent).
     */
    /**
     * D-pad navigation chain so the user can always traverse without
     * dead-ends:
     *
     *     [voice] ↔ [input pill] ↔ [clear] ↔ [scope] ↔ [sort]
     *                              ↕
     *     grid  /  typeahead rows  /  recent chips
     */
    private fun wireFocusChain() {
        // Header row is left-to-right: voice → input → clear → scope → sort.
        voiceBtn.nextFocusDownId = R.id.search_grid
        queryEdit.nextFocusDownId = R.id.search_grid
        clearBtn.nextFocusDownId = R.id.search_grid
        scopeIcon.nextFocusDownId = R.id.search_grid
        sortIcon.nextFocusDownId = R.id.search_grid
        // From the grid going up — fallback path; the intercept listener
        // below does the real work (BaseGridView eats UP at row 0 sometimes).
        grid.nextFocusUpId = R.id.search_input
    }

    private fun bindViews(v: View) {
        queryEdit = v.findViewById(R.id.search_input)
        clearBtn = v.findViewById(R.id.search_clear)
        voiceBtn = v.findViewById(R.id.search_voice)
        scopeIcon = v.findViewById(R.id.search_scope_icon)
        sortIcon = v.findViewById(R.id.search_sort_icon)
        grid = v.findViewById(R.id.search_grid)
        resultsContainer = v.findViewById(R.id.search_results_container)
        resultsCount = v.findViewById(R.id.search_results_count)
        typeaheadContainer = v.findViewById(R.id.search_typeahead_container)
        typeaheadList = v.findViewById(R.id.search_typeahead_list)
        typeaheadHeader = v.findViewById(R.id.search_typeahead_header)
        typeaheadShowAll = v.findViewById(R.id.search_typeahead_show_all)
        recentsContainer = v.findViewById(R.id.search_recents_container)
        recentsRow = v.findViewById(R.id.search_recents_row)
        recentsClear = v.findViewById(R.id.search_recents_clear)
        suggestBanner = v.findViewById(R.id.search_suggest_banner)
        loadingText = v.findViewById(R.id.search_loading)
        emptyText = v.findViewById(R.id.search_empty)
        scopePanel = v.findViewById(R.id.search_scope_panel)
        sortPanel = v.findViewById(R.id.search_sort_panel)
    }

    private fun wireScopeSortMenus() {
        scopeIcon.setOnClickListener { toggleScopePanel() }
        sortIcon.setOnClickListener { toggleSortPanel() }
    }

    /** Flat sort options — each row applies a fixed (category, direction). */
    private data class SortItem(val label: String, val category: SortCategory, val direction: SortDirection)

    private fun sortItems(): List<SortItem> = listOf(
        SortItem(getString(R.string.search_sort_newest_first), SortCategory.DATE, SortDirection.DESC),
        SortItem(getString(R.string.search_sort_oldest_first), SortCategory.DATE, SortDirection.ASC),
        SortItem(getString(R.string.search_sort_title_az), SortCategory.TITLE, SortDirection.ASC),
        SortItem(getString(R.string.search_sort_title_za), SortCategory.TITLE, SortDirection.DESC),
        SortItem(getString(R.string.search_sort_rating_hi_lo), SortCategory.RATING, SortDirection.DESC),
        SortItem(getString(R.string.search_sort_rating_lo_hi), SortCategory.RATING, SortDirection.ASC),
    )

    private fun toggleScopePanel() {
        if (scopePanel.isVisible) {
            hidePanels(restoreFocusTo = scopeIcon)
        } else {
            hidePanels()
            buildScopePanel()
            scopePanel.visibility = View.VISIBLE
            scopePanel.post { scopePanel.getChildAt(0)?.requestFocus() }
        }
    }

    private fun toggleSortPanel() {
        if (sortPanel.isVisible) {
            hidePanels(restoreFocusTo = sortIcon)
        } else {
            hidePanels()
            buildSortPanel()
            sortPanel.visibility = View.VISIBLE
            sortPanel.post { sortPanel.getChildAt(0)?.requestFocus() }
        }
    }

    private fun hidePanels(restoreFocusTo: View? = null) {
        if (scopePanel.isVisible) scopePanel.visibility = View.GONE
        if (sortPanel.isVisible) sortPanel.visibility = View.GONE
        restoreFocusTo?.requestFocus()
    }

    private fun buildScopePanel() {
        scopePanel.removeAllViews()
        val current = viewModel.uiState.value.scope
        val scopes = listOf(
            SearchScope.MOVIE to getString(R.string.search_scope_movie),
            SearchScope.ACTOR to getString(R.string.search_scope_actor),
            SearchScope.DIRECTOR to getString(R.string.search_scope_director),
            SearchScope.YEAR to getString(R.string.search_scope_year),
        )
        scopes.forEachIndexed { idx, (scope, label) ->
            scopePanel.addView(makePanelRow(label, scope == current, idx == scopes.lastIndex, scopeIcon) {
                viewModel.setScope(scope)
                hidePanels(restoreFocusTo = queryEdit)
            })
        }
    }

    private fun buildSortPanel() {
        sortPanel.removeAllViews()
        val current = viewModel.uiState.value.sortOption
        val items = sortItems()
        items.forEachIndexed { idx, item ->
            val selected = item.category == current.category && item.direction == current.direction
            sortPanel.addView(makePanelRow(item.label, selected, idx == items.lastIndex, sortIcon) {
                viewModel.applySort(SortOption(item.category, item.direction))
                hidePanels(restoreFocusTo = queryEdit)
            })
        }
    }

    /** A focusable row for the dropdown panel: check icon (gold if selected) + label. */
    private fun makePanelRow(label: String, selected: Boolean, isLast: Boolean, fallbackFocus: View, onSelect: () -> Unit): View {
        val ctx = requireContext()
        val dm = resources.displayMetrics
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isFocusable = true
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_search_dropdown_row)
            setPadding((12 * dm.density).toInt(), (10 * dm.density).toInt(), (12 * dm.density).toInt(), (10 * dm.density).toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (!isLast) lp.bottomMargin = (2 * dm.density).toInt()
            layoutParams = lp
        }
        val check = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_check)
            val s = (18 * dm.density).toInt()
            layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = (10 * dm.density).toInt() }
            imageTintList = ContextCompat.getColorStateList(ctx, R.color.accent_primary)
            visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
        val tv = TextView(ctx).apply {
            text = label
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 14f
        }
        row.addView(check)
        row.addView(tv)
        row.setOnClickListener { onSelect() }
        row.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                hidePanels(restoreFocusTo = fallbackFocus)
                true
            } else false
        }
        return row
    }

    private fun wireInput() {
        queryEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (suppressTextWatcher) return
                // Typing means the user moved on from any pinned filter.
                pinnedFilterLabel = null
                pinnedFilterResults = null
                viewModel.onQueryChange(s?.toString().orEmpty())
            }
        })
        queryEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                grid.requestFocus()
                true
            } else false
        }
        clearBtn.setOnClickListener {
            queryEdit.setText("")
            queryEdit.requestFocus()
        }
        // EditText eats DPAD_LEFT/RIGHT to move the cursor before Android's
        // focus search runs — which strands users inside the input pill
        // (especially when the clear button is gone). Intercept and route
        // horizontal d-pad presses manually to the next visible neighbor.
        queryEdit.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val next = if (clearBtn.visibility == View.VISIBLE) clearBtn else scopeIcon
                    next.requestFocus()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    voiceBtn.requestFocus()
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> routeDownFromToolbar()
                else -> false
            }
        }
        // Likewise, when clear is visible and focused, RIGHT should jump
        // to the scope icon (not back into the EditText).
        clearBtn.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    scopeIcon.requestFocus(); true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> routeDownFromToolbar()
                else -> false
            }
        }
        // DOWN from any of the always-visible toolbar pills should also
        // dive into the visible results surface (typeahead > grid >
        // recents). Without this, focus search either lands nowhere
        // useful or skips past the typeahead pane entirely.
        val toolbarDownHandler = View.OnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN
            ) routeDownFromToolbar() else false
        }
        voiceBtn.setOnKeyListener(toolbarDownHandler)
        scopeIcon.setOnKeyListener(toolbarDownHandler)
        sortIcon.setOnKeyListener(toolbarDownHandler)
    }

    /**
     * Steer DPAD_DOWN from any toolbar control into the currently visible
     * results surface. Priority: typeahead pane (when typing) > results
     * grid > recents chips/clear. Returns true if focus was claimed.
     */
    private fun routeDownFromToolbar(): Boolean {
        return when {
            typeaheadContainer.isVisible && typeaheadList.childCount > 0 -> {
                typeaheadList.getChildAt(0).requestFocus()
                true
            }
            resultsContainer.isVisible && grid.isVisible -> {
                grid.requestFocus()
                true
            }
            recentsContainer.isVisible -> {
                val target = when {
                    recentsRow.childCount > 0 -> recentsRow.getChildAt(0)
                    recentsClear.isVisible -> recentsClear
                    else -> null
                }
                target?.let { it.requestFocus(); true } ?: false
            }
            else -> false
        }
    }

    private fun wireGrid() {
        // Adaptive column count is computed AFTER the grid is measured so
        // each cell sits at (posterWidth + tinyGap). VerticalGridView splits
        // its width into numColumns equal cells and centers each card in its
        // cell, so to keep tiles visually tight the cell width must hug the
        // poster width. We seed a reasonable default and refine in post().
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        val seed = when {
            widthDp >= 1600f -> 7
            widthDp >= 1280f -> 6
            widthDp >= 960f -> 5
            else -> 4
        }
        grid.setNumColumns(seed)
        gridColumns = seed
        // setItemSpacing on VerticalGridView controls the gap BETWEEN cells;
        // visual gap between posters = itemSpacing + 2 * (cell-padding around poster).
        val gapPx = (2f * dm.density).toInt()
        grid.setItemSpacing(gapPx)
        grid.setHorizontalSpacing(gapPx)
        grid.setVerticalSpacing(gapPx)
        adapter.presenterSelector = androidx.leanback.widget.SinglePresenterSelector(cardPresenter)
        grid.adapter = ItemBridgeAdapter(adapter)

        grid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val gridW = grid.width
            if (gridW <= 0) return@addOnLayoutChangeListener
            val posterW = com.kstream.tv.ui.home.presenter.RailCardSizing
                .computePosterSize(dm).widthPx
            // Each cell needs to fit poster + 4dp horizontal breathing room.
            val cellTarget = (posterW + (4f * dm.density).toInt()).coerceAtLeast(1)
            val computed = (gridW / cellTarget).coerceAtLeast(2)
            if (computed != gridColumns) {
                gridColumns = computed
                grid.setNumColumns(computed)
            }
        }

        // UP at the top row of the grid must escape to the search input.
        // BaseGridView's internal focus search consumes UP within the grid,
        // so we intercept it here and check whether the focused child sits
        // on the first row (no row above it).
        grid.setOnKeyInterceptListener(BaseGridView.OnKeyInterceptListener { event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_DPAD_UP
            ) {
                val focused = grid.focusedChild
                if (focused != null && isOnFirstRow(focused)) {
                    queryEdit.requestFocus()
                    return@OnKeyInterceptListener true
                }
            }
            false
        })
    }

    /** True if [child] is on the first visible row (no card above it within the grid). */
    private fun isOnFirstRow(child: View): Boolean {
        val idx = grid.indexOfChild(child)
        if (idx < 0) return false
        val position = grid.getChildAdapterPosition(child)
        val cols = gridColumns.coerceAtLeast(1)
        return position in 0 until cols
    }

    private fun wireRecentsClear() {
        recentsClear.setOnClickListener { viewModel.clearRecentSearches() }
    }

    private fun wireVoice() {
        voiceBtn.setOnClickListener { launchVoiceSearch() }
        // Always show the mic — the launchVoiceSearch() chain tries
        // intent → in-app recognizer → toast if neither works, so the
        // button stays consistent across devices.
    }

    private fun isAnyVoiceAvailable(): Boolean {
        val ctx = requireContext()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val intentAvailable = intent.resolveActivity(ctx.packageManager) != null
        val inAppAvailable = SpeechRecognizer.isRecognitionAvailable(ctx)
        return intentAvailable || inAppAvailable
    }

    private fun launchVoiceSearch() {
        val ctx = requireContext()
        // Path A: try the standard system speech dialog (Google / Samsung etc).
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.search_voice_listening))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (intent.resolveActivity(ctx.packageManager) != null) {
            try {
                voiceLauncher.launch(intent)
                return
            } catch (_: Exception) {
                // Fall through to in-app recognizer.
            }
        }
        // Path B: in-app SpeechRecognizer (Fire TV / Android TV without a
        // visible recognizer activity). Requires RECORD_AUDIO at runtime.
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) startInAppRecognizer()
            else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        Toast.makeText(ctx, R.string.search_voice_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun startInAppRecognizer() {
        val ctx = requireContext()
        inAppRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
        inAppRecognizer = recognizer
        Toast.makeText(ctx, R.string.search_voice_listening, Toast.LENGTH_SHORT).show()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                if (!isAdded) return
                val msg = if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) R.string.search_voice_no_match else R.string.search_voice_unavailable
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                recognizer.destroy()
                inAppRecognizer = null
            }
            override fun onResults(results: Bundle?) {
                if (!isAdded) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotEmpty()) applySpokenText(spoken)
                recognizer.destroy()
                inAppRecognizer = null
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            Toast.makeText(ctx, R.string.search_voice_unavailable, Toast.LENGTH_SHORT).show()
            recognizer.destroy()
            inAppRecognizer = null
        }
    }

    private fun applySpokenText(spoken: String) {
        suppressTextWatcher = true
        queryEdit.setText(spoken)
        queryEdit.setSelection(spoken.length)
        suppressTextWatcher = false
        viewModel.onQueryChange(spoken)
    }

    // ============================================================
    // Render
    // ============================================================

    private fun render(state: SearchUiState) {
        renderHint(state.scope)
        renderClearButton(state.query)
        renderBanner(state)
        renderRecents(state.recentSearches)

        // If the user picked a grouped suggestion (e.g. "Movies of Tom
        // Hanks"), keep the grid pinned to that subset until they type
        // again. Without this, the next ViewModel emission would
        // immediately overwrite the narrowed list with the full result
        // set.
        val pinned = pinnedFilterResults
        if (pinned != null) {
            showOnly(resultsContainer)
            renderResultsArea(state.copy(results = pinned))
            return
        }

        // Reserved prefixes (e.g. "all:*", "history:*", "year:2024") arrive
        // via setInitialQuery and intentionally leave [query] blank so the
        // verbatim prefix isn't shown in the EditText. The screen still has
        // an active search though — key off [activeQuery]/results so we
        // route to the results pane instead of the idle recents pane.
        val hasActiveSearch = state.activeQuery.isNotBlank()
        val hasResults = state.results.isNotEmpty()
        val isTyping = state.query.isNotBlank()

        when {
            !hasActiveSearch -> showOnly(recentsContainer)
            hasActiveSearch && !hasResults && !state.isLoading -> {
                showOnly(resultsContainer)
                renderResultsArea(state)
            }
            isTyping && hasResults && (state.groupedSuggestions.isNotEmpty() || state.results.size <= 5) -> {
                // Show typeahead suggestions panel while typing — when scope is
                // ACTOR/DIRECTOR/YEAR we always show it (so the user sees the
                // grouped people/years), otherwise only when results are few.
                showOnly(typeaheadContainer)
                renderTypeahead(state)
            }
            else -> {
                showOnly(resultsContainer)
                renderResultsArea(state)
            }
        }
    }

    private fun showOnly(target: View) {
        recentsContainer.isVisible = target === recentsContainer
        typeaheadContainer.isVisible = target === typeaheadContainer
        resultsContainer.isVisible = target === resultsContainer
    }

    private fun labelFor(cat: SortCategory, current: SortOption): String {
        val isCurrent = current.category == cat
        val dir = if (isCurrent) current.direction else SortDirection.DESC
        return when (cat) {
            SortCategory.RELEVANCE -> getString(R.string.search_sort_relevance)
            SortCategory.DATE -> getString(
                if (dir == SortDirection.DESC) R.string.search_sort_date_new
                else R.string.search_sort_date_old
            )
            SortCategory.TITLE -> getString(
                if (dir == SortDirection.DESC) R.string.search_sort_title_az
                else R.string.search_sort_title_za
            )
            SortCategory.RATING -> getString(
                if (dir == SortDirection.DESC) R.string.search_sort_rating_high
                else R.string.search_sort_rating_low
            )
            SortCategory.YEAR -> getString(
                if (dir == SortDirection.DESC) R.string.search_sort_year_new
                else R.string.search_sort_year_old
            )
        }
    }

    private fun renderHint(scope: SearchScope) {
        val hintRes = when (scope) {
            SearchScope.MOVIE -> R.string.search_hint_movie
            SearchScope.ACTOR -> R.string.search_hint_actor
            SearchScope.DIRECTOR -> R.string.search_hint_director
            SearchScope.YEAR -> R.string.search_hint_year
            SearchScope.GENRE -> R.string.search_hint_genre
        }
        queryEdit.hint = getString(hintRes)
        if (scope == SearchScope.YEAR) {
            queryEdit.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        } else {
            queryEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
    }

    private fun renderClearButton(query: String) {
        clearBtn.isVisible = query.isNotEmpty()
    }

    private fun renderBanner(state: SearchUiState) {
        val suggested = state.suggestedQuery
        if (state.isFuzzyMatch && !suggested.isNullOrBlank()) {
            suggestBanner.text = getString(R.string.search_did_you_mean, suggested)
            suggestBanner.isVisible = true
        } else {
            suggestBanner.isVisible = false
        }
    }

    private fun renderRecents(recents: List<String>) {
        recentsRow.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        recents.take(12).forEach { q ->
            val chip = inflater.inflate(R.layout.item_search_recent_chip, recentsRow, false) as TextView
            chip.text = q
            // UP from any chip should land on "Clear all" (rather than
            // jumping straight back into the EditText, which is closer in
            // y but skips the link).
            chip.nextFocusUpId = R.id.search_recents_clear
            chip.setOnClickListener {
                suppressTextWatcher = true
                queryEdit.setText(q)
                queryEdit.setSelection(q.length)
                suppressTextWatcher = false
                viewModel.onQueryChange(q)
            }
            recentsRow.addView(chip)
        }
        recentsClear.isVisible = recents.isNotEmpty()
        // DOWN from "Clear all" → the first chip; UP → the search input.
        if (recentsRow.childCount > 0) {
            recentsClear.nextFocusDownId = recentsRow.getChildAt(0).id.takeIf { it != View.NO_ID }
                ?: run {
                    // Chips are inflated without ids — assign one so focus links work.
                    val first = recentsRow.getChildAt(0)
                    first.id = View.generateViewId()
                    first.id
                }
        }
        recentsClear.nextFocusUpId = R.id.search_input
    }

    private fun renderResultsArea(state: SearchUiState) {
        loadingText.isVisible = state.isLoading
        val hasResults = state.results.isNotEmpty()
        emptyText.isVisible = !state.isLoading && state.query.isNotBlank() && !hasResults
        emptyText.text = state.error ?: getString(R.string.search_no_results)
        resultsCount.isVisible = hasResults
        if (hasResults) {
            val pinned = pinnedFilterLabel?.takeIf { pinnedFilterResults == state.results }
            resultsCount.text = if (pinned != null) {
                "$pinned · ${getString(R.string.search_results_count, state.results.size)}"
            } else {
                getString(R.string.search_results_count, state.results.size)
            }
        }
        val current = (0 until adapter.size()).map { adapter.get(it) as Movie }
        if (current != state.results) {
            adapter.clear()
            adapter.addAll(0, state.results)
        }
        grid.isVisible = hasResults
    }

    /**
     * Friendly label for the pinned filter banner shown above the results
     * grid after the user picks a grouped suggestion.
     */
    private fun bannerLabelFor(scope: SearchScope, key: String): String = when (scope) {
        SearchScope.ACTOR -> getString(R.string.search_banner_movies_of, key)
        SearchScope.DIRECTOR -> getString(R.string.search_banner_movies_by, key)
        SearchScope.YEAR -> getString(R.string.search_banner_movies_from, key)
        else -> key
    }

    private fun renderTypeahead(state: SearchUiState) {
        typeaheadHeader.text = getString(R.string.search_typeahead_label) +
            " · " + getString(R.string.search_results_count, state.results.size)
        typeaheadList.removeAllViews()
        if (state.groupedSuggestions.isNotEmpty()) {
            renderGroupedTypeahead(state)
            typeaheadShowAll.isVisible = false
            return
        }
        val inflater = LayoutInflater.from(requireContext())
        state.suggestions.forEach { m ->
            val row = inflater.inflate(R.layout.item_search_suggestion, typeaheadList, false)
            val thumb = row.findViewById<ImageView>(R.id.suggestion_thumb)
            val title = row.findViewById<TextView>(R.id.suggestion_title)
            val meta = row.findViewById<TextView>(R.id.suggestion_meta)
            title.text = highlightMatch(m.movieName, state.query)
            meta.text = buildMetaLine(m)
            Glide.with(thumb).load(m.posterUrl).into(thumb)
            row.setOnClickListener {
                // Close the typeahead pane immediately so the user doesn't
                // come back to a stale floating panel.
                showOnly(resultsContainer)
                viewModel.onMovieClick(m) {
                    startActivity(DetailsActivity.newIntent(requireContext(), m))
                }
            }
            typeaheadList.addView(row)
        }
        if (state.results.size > state.suggestions.size) {
            typeaheadShowAll.isVisible = true
            typeaheadShowAll.text = getString(
                R.string.search_typeahead_show_all,
                state.results.size,
                state.query
            )
            typeaheadShowAll.setOnClickListener {
                // Bump the EditText action so the grid takes focus and shows full results.
                grid.requestFocus()
                showOnly(resultsContainer)
                renderResultsArea(state)
            }
        } else {
            typeaheadShowAll.isVisible = false
        }
    }

    /**
     * Render scope-aware groups (actor / director / year). Each group is a
     * header row with the person/year + a horizontal scroll of their movies
     * as small chips. Clicking the header keeps the current filtered grid;
     * clicking a chip jumps straight to that movie's details.
     */
    private fun renderGroupedTypeahead(state: SearchUiState) {
        val ctx = requireContext()
        val dm = resources.displayMetrics
        val inflater = LayoutInflater.from(ctx)
        state.groupedSuggestions.forEach { group ->
            val header = inflater.inflate(R.layout.item_search_suggestion, typeaheadList, false)
            val thumb = header.findViewById<ImageView>(R.id.suggestion_thumb)
            val title = header.findViewById<TextView>(R.id.suggestion_title)
            val meta = header.findViewById<TextView>(R.id.suggestion_meta)
            // Hide the poster thumb for group headers — there's no per-group
            // image (the mic placeholder looked wrong). The title + subtitle
            // shift left naturally because the ImageView is GONE.
            thumb.isVisible = false
            title.text = highlightMatch(group.key, state.query)
            meta.text = group.subtitle
            header.setOnClickListener {
                // Pin a contextual banner over the grid so the user knows
                // what the narrowed list represents.
                pinnedFilterLabel = bannerLabelFor(state.scope, group.key)
                pinnedFilterResults = group.movies
                if (group.movies.isNotEmpty()) {
                    viewModel.onMovieClick(group.movies.first()) { /* no nav */ }
                }
                grid.requestFocus()
                showOnly(resultsContainer)
                renderResultsArea(state.copy(results = group.movies))
            }
            typeaheadList.addView(header)

            // Chip row: horizontal scroll of up to 6 movie titles.
            val chipScroll = android.widget.HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                isFocusable = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = (46 * dm.density).toInt()
                    topMargin = (4 * dm.density).toInt()
                    bottomMargin = (8 * dm.density).toInt()
                }
            }
            val chipRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            group.movies.take(6).forEach { m ->
                val chip = TextView(ctx).apply {
                    text = m.movieName
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    textSize = 11f
                    setPadding(
                        (10 * dm.density).toInt(),
                        (4 * dm.density).toInt(),
                        (10 * dm.density).toInt(),
                        (4 * dm.density).toInt()
                    )
                    background = ContextCompat.getDrawable(ctx, R.drawable.bg_search_dropdown_row)
                    isFocusable = true
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginEnd = (6 * dm.density).toInt()
                    layoutParams = lp
                    setOnClickListener {
                        showOnly(resultsContainer)
                        viewModel.onMovieClick(m) {
                            startActivity(DetailsActivity.newIntent(requireContext(), m))
                        }
                    }
                }
                chipRow.addView(chip)
            }
            chipScroll.addView(chipRow)
            typeaheadList.addView(chipScroll)
        }
    }

    private fun buildMetaLine(m: Movie): String = buildString {
        if (m.type.isNotBlank()) append(m.type)
        if (m.year > 0) {
            if (isNotEmpty()) append(" · ")
            append(m.year)
        }
        val genres = m.genres.take(2).joinToString(", ")
        if (genres.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(genres)
        }
        val rating = m.rating.toDoubleOrNull()
        if (rating != null && rating > 0) {
            if (isNotEmpty()) append(" · ")
            append("★ ").append(rating)
        }
    }

    private fun highlightMatch(text: String, query: String): CharSequence {
        if (query.isBlank()) return text
        val idx = text.indexOf(query, ignoreCase = true)
        if (idx < 0) return text
        val accent = ContextCompat.getColor(requireContext(), R.color.accent_primary)
        return SpannableStringBuilder(text).apply {
            setSpan(ForegroundColorSpan(accent), idx, idx + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), idx, idx + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
