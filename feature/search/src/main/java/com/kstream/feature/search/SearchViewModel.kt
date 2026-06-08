package com.kstream.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.common.toUserMessage
import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.domain.repository.LikedMovieRepository
import com.kstream.core.domain.GetAllWatchProgressUseCase
import com.kstream.core.domain.GetRecommendationsUseCase
import com.kstream.core.model.Movie
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/** What field the user wants to search. */
enum class SearchScope { MOVIE, ACTOR, DIRECTOR, YEAR, GENRE }

/** Sort dimensions (toggle order via [SortCategory]/[SortDirection]). */
enum class SortCategory { RELEVANCE, DATE, TITLE, RATING, YEAR }
enum class SortDirection { DESC, ASC }

/**
 * Sort option — pairs a [SortCategory] with its current direction.
 * Pressing the same category chip flips the direction; pressing a
 * different category selects that category with its remembered direction.
 */
data class SortOption(
    val category: SortCategory = SortCategory.RELEVANCE,
    val direction: SortDirection = SortDirection.DESC
)

data class SearchUiState(
    val query: String = "",
    val activeQuery: String = "",
    val scope: SearchScope = SearchScope.MOVIE,
    val results: List<Movie> = emptyList(),
    val suggestions: List<Movie> = emptyList(),
    val groupedSuggestions: List<GroupedSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val recentSearches: List<String> = emptyList(),
    val sortOption: SortOption = SortOption(),
    val suggestedQuery: String? = null,
    val isFuzzyMatch: Boolean = false,
    val sortGeneration: Int = 0
)

/** A typeahead group used for scope-aware suggestions (actor / director / year). */
data class GroupedSuggestion(
    val key: String,           // e.g. "Christopher Nolan" or "2024"
    val subtitle: String,      // e.g. "12 movies"
    val movies: List<Movie>    // the movies to show as chips and to filter on tap
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userDataRepository: UserDataRepository,
    private val likedMovieRepository: LikedMovieRepository,
    private val getAllWatchProgressUseCase: GetAllWatchProgressUseCase,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var searchJob: Job? = null

    private val reservedPrefixes = listOf("history:", "liked:", "all:", "recommended:", "genre:", "year:", "actor:", "director:")

    /** Per-category remembered direction so re-selecting a chip restores last flip. */
    private val rememberedDirections: MutableMap<SortCategory, SortDirection> = mutableMapOf(
        SortCategory.DATE to SortDirection.DESC,
        SortCategory.TITLE to SortDirection.ASC,
        SortCategory.RATING to SortDirection.DESC,
        SortCategory.YEAR to SortDirection.DESC
    )

    private fun isReservedQuery(query: String): Boolean {
        val trimmed = query.trim()
        return reservedPrefixes.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val SUGGESTION_LIMIT = 5
        private const val GROUP_LIMIT = 6
    }

    private var hdOnly: Boolean = false

    init {
        userDataRepository.recentSearches
            .catch { emit(emptyList()) }
            .onEach { recents ->
                _uiState.update { it.copy(recentSearches = recents.filter { q -> !isReservedQuery(q) }) }
            }
            .launchIn(viewModelScope)

        networkMonitor.isOnline
            .onEach { online -> _isOnline.value = online }
            .launchIn(viewModelScope)

        userDataRepository.isHdOnlyFilter
            .catch { emit(false) }
            .onEach { enabled ->
                hdOnly = enabled
                val current = _uiState.value
                if (current.results.isNotEmpty()) {
                    executeSearch(current.activeQuery)
                }
            }
            .launchIn(viewModelScope)
    }

    fun setInitialQuery(query: String) {
        if (_uiState.value.activeQuery == query) return
        val displayQuery = if (isReservedQuery(query)) "" else query
        val defaultSort = if (shouldDefaultToLatest(query))
            SortOption(SortCategory.DATE, SortDirection.DESC)
        else SortOption()
        _uiState.update { it.copy(query = displayQuery, activeQuery = query, sortOption = defaultSort) }
        if (query.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                executeSearch(query)
            }
        }
    }

    private fun shouldDefaultToLatest(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed == "all:*" ||
                trimmed.startsWith("genre:", ignoreCase = true) ||
                trimmed.startsWith("year:", ignoreCase = true)
    }

    fun setScope(scope: SearchScope) {
        if (_uiState.value.scope == scope) return
        _uiState.update { it.copy(scope = scope, suggestions = emptyList()) }
        // Re-run the active query under the new scope.
        val q = _uiState.value.query
        if (q.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                delay(DEBOUNCE_MS)
                executeSearch(q)
            }
        } else {
            _uiState.update { it.copy(results = emptyList()) }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, activeQuery = newQuery) }

        searchJob?.cancel()

        if (newQuery.isNotEmpty()) {
            searchJob = viewModelScope.launch {
                delay(DEBOUNCE_MS)
                executeSearch(newQuery)
            }
        } else {
            _uiState.update { it.copy(results = emptyList(), suggestions = emptyList(), isLoading = false) }
        }
    }

    fun onMovieClick(movie: Movie, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val query = _uiState.value.query.trim()
                if (query.isNotEmpty() && !isReservedQuery(query)) {
                    userDataRepository.addRecentSearch(query)
                }
            } catch (_: Exception) { }
            onNavigate(movie.id)
        }
    }

    /** Set sort to an explicit category + direction (used by the flat sort panel). */
    fun applySort(option: SortOption) {
        _uiState.update { state ->
            if (option.category != SortCategory.RELEVANCE) {
                rememberedDirections[option.category] = option.direction
            }
            state.copy(
                sortOption = option,
                results = applySorting(state.results, option),
                sortGeneration = state.sortGeneration + 1
            )
        }
    }

    /**
     * Toggle behaviour:
     * - Press the currently-selected category → flip its direction.
     * - Press a different category → select it with its remembered direction.
     * - Relevance has no direction.
     */
    fun toggleSort(category: SortCategory) {
        _uiState.update { state ->
            val current = state.sortOption
            val newOption = if (current.category == category) {
                val flipped = if (current.direction == SortDirection.DESC) SortDirection.ASC else SortDirection.DESC
                if (category != SortCategory.RELEVANCE) rememberedDirections[category] = flipped
                SortOption(category, flipped)
            } else {
                val dir = rememberedDirections[category] ?: SortDirection.DESC
                SortOption(category, dir)
            }
            state.copy(
                sortOption = newOption,
                results = applySorting(state.results, newOption),
                sortGeneration = state.sortGeneration + 1
            )
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            try { userDataRepository.clearRecentSearches() } catch (_: Exception) { }
        }
    }

    private val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH)

    private fun parseLastUpdated(dateStr: String): Long {
        return try {
            if (dateStr.isNotBlank()) dateFormatter.parse(dateStr)?.time ?: 0L else 0L
        } catch (_: Exception) { 0L }
    }

    private fun applySorting(movies: List<Movie>, option: SortOption): List<Movie> {
        val desc = option.direction == SortDirection.DESC
        return when (option.category) {
            SortCategory.RELEVANCE -> movies
            SortCategory.TITLE ->
                if (desc) movies.sortedByDescending { it.movieName.lowercase() }
                else movies.sortedBy { it.movieName.lowercase() }
            SortCategory.RATING ->
                if (desc) movies.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
                else movies.sortedBy { it.rating.toDoubleOrNull() ?: 0.0 }
            SortCategory.YEAR ->
                if (desc) movies.sortedByDescending { it.year }
                else movies.sortedBy { it.year }
            SortCategory.DATE -> {
                if (desc) movies.sortedWith(
                    compareByDescending<Movie> { it.lastUpdated.isNotBlank() }
                        .thenByDescending { parseLastUpdated(it.lastUpdated) }
                ) else movies.sortedWith(
                    compareBy<Movie> { it.lastUpdated.isBlank() }
                        .thenBy { parseLastUpdated(it.lastUpdated) }
                )
            }
        }
    }

    private fun applyHdFilter(movies: List<Movie>): List<Movie> {
        return if (hdOnly) movies.filter { com.kstream.core.common.HdQualityFilter.isHd(it.type) } else movies
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(isLoading = true, error = null, suggestedQuery = null, isFuzzyMatch = false) }
        try {
            val state = _uiState.value
            val results: List<Movie>? = when {
                query.trim() == "history:*" -> {
                    val progress = getAllWatchProgressUseCase().first()
                    val allMovies = movieRepository.searchMovies("*")
                    val movieMap = allMovies.associateBy { it.id }
                    progress
                        .sortedByDescending { it.lastUpdated }
                        .mapNotNull { p -> movieMap[p.movieId] }
                }
                query.trim() == "liked:*" -> {
                    val likedIds = likedMovieRepository.getAllLikedMovieIds().first()
                    val allMovies = movieRepository.searchMovies("*")
                    val movieMap = allMovies.associateBy { it.id }
                    likedIds.mapNotNull { id -> movieMap[id] }
                }
                query.trim() == "all:*" -> movieRepository.searchMovies("*")
                query.trim() == "recommended:*" -> getRecommendationsUseCase().first()
                query.trim().startsWith("genre:", ignoreCase = true) -> {
                    val genre = query.trim().substringAfter(":").trim()
                    movieRepository.searchMovies("*").filter { m ->
                        m.genres.any { it.equals(genre, ignoreCase = true) }
                    }.sortedWith(compareByDescending { parseLastUpdated(it.lastUpdated) })
                }
                query.trim().startsWith("year:", ignoreCase = true) -> {
                    val year = query.trim().substringAfter(":").trim().toIntOrNull()
                    if (year == null) emptyList()
                    else movieRepository.searchMovies("*")
                        .filter { it.year == year }
                        .sortedWith(compareByDescending { parseLastUpdated(it.lastUpdated) })
                }
                query.trim().startsWith("actor:", ignoreCase = true) -> {
                    val needle = query.trim().substringAfter(":").trim()
                    if (needle.isEmpty()) emptyList()
                    else movieRepository.searchMovies("*").filter { m ->
                        m.castMembers.any { it.contains(needle, ignoreCase = true) }
                    }
                }
                query.trim().startsWith("director:", ignoreCase = true) -> {
                    val needle = query.trim().substringAfter(":").trim()
                    if (needle.isEmpty()) emptyList()
                    else movieRepository.searchMovies("*").filter { m ->
                        m.director.any { it.contains(needle, ignoreCase = true) }
                    }
                }
                state.scope == SearchScope.ACTOR -> {
                    movieRepository.searchMovies("*").filter { m ->
                        m.castMembers.any { it.contains(query.trim(), ignoreCase = true) }
                    }
                }
                state.scope == SearchScope.DIRECTOR -> {
                    movieRepository.searchMovies("*").filter { m ->
                        m.director.any { it.contains(query.trim(), ignoreCase = true) }
                    }
                }
                state.scope == SearchScope.YEAR -> {
                    val year = query.trim().toIntOrNull()
                    if (year == null) emptyList()
                    else movieRepository.searchMovies("*").filter { it.year == year }
                }
                state.scope == SearchScope.GENRE -> {
                    val needle = query.trim()
                    movieRepository.searchMovies("*").filter { m ->
                        m.genres.any { it.contains(needle, ignoreCase = true) }
                    }
                }
                else -> null // movie name → use fuzzy search below
            }

            if (results != null) {
                val filtered = applySorting(applyHdFilter(results), state.sortOption)
                val groups = buildScopeGroups(state.scope, query.trim(), filtered)
                _uiState.update {
                    it.copy(
                        results = filtered,
                        suggestions = filtered.take(SUGGESTION_LIMIT),
                        groupedSuggestions = groups,
                        isLoading = false
                    )
                }
            } else {
                val searchResult = movieRepository.searchMoviesWithSuggestion(query)
                val filtered = applySorting(applyHdFilter(searchResult.movies), state.sortOption)
                _uiState.update {
                    it.copy(
                        results = filtered,
                        suggestions = filtered.take(SUGGESTION_LIMIT),
                        groupedSuggestions = emptyList(),
                        suggestedQuery = searchResult.suggestedQuery,
                        isFuzzyMatch = searchResult.isFuzzyMatch,
                        isLoading = false
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.toUserMessage(), results = emptyList(), suggestions = emptyList(), groupedSuggestions = emptyList()) }
        }
    }

    /**
     * Build scope-aware typeahead groups. For actor / director scopes we
     * group the result set by the matching person name; for year we group
     * by year. Returns empty for MOVIE / GENRE so the flat suggestion list
     * is used instead.
     */
    private fun buildScopeGroups(
        scope: SearchScope,
        needle: String,
        results: List<Movie>
    ): List<GroupedSuggestion> {
        if (needle.isBlank() || results.isEmpty()) return emptyList()
        return when (scope) {
            SearchScope.ACTOR -> groupByPerson(results, needle) { it.castMembers }
            SearchScope.DIRECTOR -> groupByPerson(results, needle) { it.director }
            SearchScope.YEAR -> {
                results.groupBy { it.year }
                    .toSortedMap(compareByDescending { it })
                    .map { (year, ms) ->
                        GroupedSuggestion(
                            key = year.toString(),
                            subtitle = "${ms.size} movies",
                            movies = ms
                        )
                    }
                    .take(GROUP_LIMIT)
            }
            else -> emptyList()
        }
    }

    private fun groupByPerson(
        results: List<Movie>,
        needle: String,
        extract: (Movie) -> List<String>
    ): List<GroupedSuggestion> {
        val buckets = linkedMapOf<String, MutableList<Movie>>()
        results.forEach { m ->
            extract(m).filter { it.contains(needle, ignoreCase = true) }.forEach { name ->
                val canonical = name.trim()
                if (canonical.isNotEmpty()) {
                    buckets.getOrPut(canonical) { mutableListOf() }.add(m)
                }
            }
        }
        return buckets.entries
            .sortedByDescending { it.value.size }
            .take(GROUP_LIMIT)
            .map { (name, ms) ->
                GroupedSuggestion(
                    key = name,
                    subtitle = "${ms.size} ${if (ms.size == 1) "movie" else "movies"}",
                    movies = ms.distinctBy { it.id }
                )
            }
    }
}
