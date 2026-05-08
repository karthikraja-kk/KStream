package com.kstream.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.repository.MovieRepository
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.model.Movie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<Movie> = emptyList(),
    val isLoading: Boolean = false,
    val recentSearches: List<String> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userDataRepository: UserDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        userDataRepository.recentSearches
            .onEach { recents -> _uiState.update { it.copy(recentSearches = recents) } }
            .launchIn(viewModelScope)
    }

    fun setInitialQuery(query: String) {
        if (_uiState.value.query == query) return
        _uiState.update { it.copy(query = query) }
        if (query.length >= 3) {
            searchMovies(query)
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        if (newQuery.length >= 3) {
            searchMovies(newQuery)
        } else {
            _uiState.update { it.copy(results = emptyList()) }
        }
    }

    fun onMovieClick(movie: Movie, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            userDataRepository.addRecentSearch(_uiState.value.query.trim())
            onNavigate(movie.id)
        }
    }

    private fun searchMovies(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val results = movieRepository.searchMovies(query)
                _uiState.update { it.copy(results = results, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
