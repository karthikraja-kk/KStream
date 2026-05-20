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
import javax.inject.Inject

enum class SortOption(val label: String) {
    NONE("Relevance"),
    ALPHABET_ASC("A → Z"),
    ALPHABET_DESC("Z → A"),
    RATING_HIGH("Rating ↓"),
    RATING_LOW("Rating ↑"),
    LATEST_FIRST("Latest First"),
    OLDEST_FIRST("Oldest First")
}

data class SearchUiState(
    val query: String = "",
    val results: List<Movie> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val recentSearches: List<String> = emptyList(),
    val sortOption: SortOption = SortOption.NONE,
    val suggestedQuery: String? = null,
    val isFuzzyMatch: Boolean = false
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
    val isOnline: StateFlow<Boolean> = MutableStateFlow(true)

    private var searchJob: Job? = null

    private val reservedPrefixes = listOf("history:", "liked:", "all:", "recommended:", "genre:", "year:")

    private fun isReservedQuery(query: String): Boolean {
        val trimmed = query.trim()
        return reservedPrefixes.any { trimmed.startsWith(it, ignoreCase = true) }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
    }

    init {
        userDataRepository.recentSearches
            .catch { emit(emptyList()) }
            .onEach { recents ->
                _uiState.update { it.copy(recentSearches = recents.filter { q -> !isReservedQuery(q) }) }
            }
            .launchIn(viewModelScope)
        
        networkMonitor.isOnline
            .onEach { online ->
                (isOnline as MutableStateFlow).value = online
            }
            .launchIn(viewModelScope)
    }

    fun setInitialQuery(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        if (query.isNotEmpty()) {
            // No debounce for programmatic queries (See More, recent searches)
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                executeSearch(query)
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }

        // Cancel previous search
        searchJob?.cancel()

        if (newQuery.isNotEmpty()) {
            searchJob = viewModelScope.launch {
                // Debounce user typing — wait 300ms before searching
                delay(DEBOUNCE_MS)
                executeSearch(newQuery)
            }
        } else {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
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

    fun onSortChange(option: SortOption) {
        _uiState.update { state ->
            state.copy(
                sortOption = option,
                results = applySorting(state.results, option)
            )
        }
    }

    private fun applySorting(movies: List<Movie>, option: SortOption): List<Movie> {
        return when (option) {
            SortOption.NONE -> movies
            SortOption.ALPHABET_ASC -> movies.sortedBy { it.movieName.lowercase() }
            SortOption.ALPHABET_DESC -> movies.sortedByDescending { it.movieName.lowercase() }
            SortOption.RATING_HIGH -> movies.sortedByDescending { it.rating }
            SortOption.RATING_LOW -> movies.sortedBy { it.rating }
            SortOption.LATEST_FIRST -> movies.sortedWith(
                compareByDescending<Movie> { it.lastUpdated.isNotBlank() }
                    .thenByDescending { it.lastUpdated }
            )
            SortOption.OLDEST_FIRST -> movies.sortedWith(
                compareBy<Movie> { it.lastUpdated.isBlank() }
                    .thenBy { it.lastUpdated }
            )
        }
    }

    private suspend fun executeSearch(query: String) {
        _uiState.update { it.copy(isLoading = true, error = null, suggestedQuery = null, isFuzzyMatch = false) }
        try {
            val results = when {
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
                query.trim() == "all:*" -> {
                    movieRepository.searchMovies("*")
                }
                query.trim() == "recommended:*" -> {
                    getRecommendationsUseCase().first()
                }
                else -> null // handled below with fuzzy search
            }

            if (results != null) {
                _uiState.update { it.copy(results = applySorting(results, it.sortOption), isLoading = false) }
            } else {
                val searchResult = movieRepository.searchMoviesWithSuggestion(query)
                _uiState.update {
                    it.copy(
                        results = applySorting(searchResult.movies, it.sortOption),
                        suggestedQuery = searchResult.suggestedQuery,
                        isFuzzyMatch = searchResult.isFuzzyMatch,
                        isLoading = false
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) }
        }
    }
}