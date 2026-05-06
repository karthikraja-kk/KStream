package com.kstream.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.GetMoviesUseCase
import com.kstream.core.domain.SyncMoviesUseCase
import com.kstream.core.domain.GetAllWatchProgressUseCase
import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val rails: List<MovieRail> = emptyList(),
    val error: String? = null
)

data class MovieRail(
    val title: String,
    val movies: List<Movie>
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMoviesUseCase: GetMoviesUseCase,
    private val syncMoviesUseCase: SyncMoviesUseCase,
    private val getAllWatchProgressUseCase: GetAllWatchProgressUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshContent()
        observeMovies()
    }

    private fun observeMovies() {
        combine(
            getMoviesUseCase().catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
            getAllWatchProgressUseCase().catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
        ) { movies, progress ->
            if (movies.isNotEmpty()) {
                _uiState.update { it.copy(isLoading = false, rails = groupMoviesIntoRails(movies, progress)) }
            } else if (_uiState.value.isLoading) {
                // If we have no movies and we're still loading, just stop loading but don't show error yet
                // as refreshContent might still be working.
                _uiState.update { it.copy(isLoading = false) }
            }
        }.launchIn(viewModelScope)
    }

    fun refreshContent() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                syncMoviesUseCase()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun groupMoviesIntoRails(
        movies: List<Movie>,
        progress: List<WatchProgress>
    ): List<MovieRail> {
        val rails = mutableListOf<MovieRail>()
        
        // Continue Watching
        val continueWatchingMovies = progress
            .filter { it.completionPercent < 95f }
            .sortedByDescending { it.lastUpdated }
            .mapNotNull { p -> movies.find { it.id == p.movieId } }
        
        if (continueWatchingMovies.isNotEmpty()) {
            rails.add(MovieRail("Continue Watching", continueWatchingMovies))
        }

        // New Releases
        rails.add(MovieRail("New Releases", movies.sortedByDescending { it.year }.take(10)))
        
        // Recommendations
        val watchedMovieIds = progress.filter { it.completionPercent > 50f }.map { it.movieId }
        val watchedGenres = movies.filter { watchedMovieIds.contains(it.id) }.flatMap { it.genres }.distinct()
        
        if (watchedGenres.isNotEmpty()) {
            val recommended = movies.filter { it.genres.any { g -> watchedGenres.contains(g) } && !watchedMovieIds.contains(it.id) }.take(10)
            if (recommended.isNotEmpty()) {
                rails.add(MovieRail("You Might Like", recommended))
            }
        }
        
        // ... (rest of rails logic)
        val allGenres = movies.flatMap { it.genres }.distinct()
        allGenres.take(5).forEach { genre ->
            val genreMovies = movies.filter { it.genres.contains(genre) }.take(10)
            if (genreMovies.isNotEmpty()) {
                rails.add(MovieRail(genre, genreMovies))
            }
        }
        
        val years = movies.map { it.year }.distinct().sortedDescending()
        years.take(3).forEach { year ->
            val yearMovies = movies.filter { it.year == year }.take(10)
            rails.add(MovieRail("Released in $year", yearMovies))
        }

        return rails
    }
}
