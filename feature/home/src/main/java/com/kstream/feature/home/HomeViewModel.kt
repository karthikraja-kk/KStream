package com.kstream.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.common.toUserMessage
import com.kstream.core.domain.GetMoviesUseCase
import com.kstream.core.domain.SyncMoviesUseCase
import com.kstream.core.domain.GetAllWatchProgressUseCase
import com.kstream.core.domain.GetRecommendationsUseCase
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress
import com.kstream.core.domain.repository.LikedMovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val userName: String = "",
    val rails: List<MovieRail> = emptyList(),
    val watchProgressMap: Map<String, WatchProgress> = emptyMap(),
    val error: String? = null
)

data class MovieRail(
    val title: String,
    val movies: List<Movie>,
    val totalCount: Int? = null,
    val seeMoreQuery: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMoviesUseCase: GetMoviesUseCase,
    private val syncMoviesUseCase: SyncMoviesUseCase,
    private val getAllWatchProgressUseCase: GetAllWatchProgressUseCase,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val likedMovieRepository: LikedMovieRepository,
    private val downloadRepository: DownloadRepository,
    private val userDataRepository: com.kstream.core.domain.repository.UserDataRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
    
    private var lastSyncTime = 0L
    private val syncDebounceMs = 5000L
    
    init {
        refreshContent()
        observeData()
        observeNetworkChanges()
        observeActivityForRecommendations()
    }

    private fun observeNetworkChanges() {
        networkMonitor.isOnline
            .onEach { online ->
                (isOnline as MutableStateFlow).value = online
            }
            .launchIn(viewModelScope)
        
        networkMonitor.isOnline
            .filter { isOnline -> isOnline }
            .onEach {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSyncTime > syncDebounceMs) {
                    lastSyncTime = currentTime
                    // If there's an error or no content loaded, do a full refresh
                    // to clear the error and reload everything
                    val state = _uiState.value
                    if (state.error != null || state.rails.isEmpty()) {
                        refreshContent()
                    } else {
                        try {
                            syncMoviesUseCase()
                        } catch (_: Exception) { }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeData() {
        combine(
            getMoviesUseCase().catch { e -> _uiState.update { it.copy(error = e.toUserMessage(), isLoading = false) } },
            getAllWatchProgressUseCase().catch { e -> _uiState.update { it.copy(error = e.toUserMessage(), isLoading = false) } },
            userDataRepository.username.catch { emit("") },
            likedMovieRepository.getAllLikedMovieIds().catch { emit(emptyList()) },
            getRecommendationsUseCase().catch { emit(emptyList()) }
        ) { movies, progress, username, likedIds, recommendations ->
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    userName = username,
                    rails = groupMoviesIntoRails(movies, progress, likedIds, recommendations),
                    watchProgressMap = progress.associateBy { p -> p.movieId }
                ) 
            }
        }.launchIn(viewModelScope)
    }

    fun refreshContent() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                lastSyncTime = System.currentTimeMillis()
                syncMoviesUseCase()
                getRecommendationsUseCase.refreshRecommendations()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.toUserMessage(), isLoading = false) }
            }
        }
    }

    /**
     * Watches the 3 user activity sources (watch progress, liked, downloads).
     * When any changes, debounce 2s then recompute and store recommendations.
     */
    private fun observeActivityForRecommendations() {
        combine(
            getAllWatchProgressUseCase().catch { emit(emptyList()) },
            likedMovieRepository.getAllLikedMovieIds().catch { emit(emptyList()) },
            downloadRepository.getDownloads().catch { emit(emptyList()) }
        ) { progress, liked, downloads ->
            Triple(progress.size, liked.size, downloads.size)
        }
            .drop(1) // skip initial emission — refreshContent handles first compute
            .debounce(2000L)
            .onEach {
                try {
                    getRecommendationsUseCase.refreshRecommendations()
                } catch (_: Exception) {}
            }
            .launchIn(viewModelScope)

        // Initial computation on app start
        viewModelScope.launch {
            try {
                getRecommendationsUseCase.refreshRecommendations()
            } catch (_: Exception) {}
        }
    }

    private fun groupMoviesIntoRails(
        movies: List<Movie>,
        progress: List<WatchProgress>,
        likedIds: List<String>,
        recommendations: List<Movie>
    ): List<MovieRail> {
        val rails = mutableListOf<MovieRail>()
        
        // Continue Watching — sorted by user's watch recency, NOT lastUpdated
        val allContinueWatchingMovies = progress
            .filter { it.completionPercent < 95f }
            .sortedByDescending { it.lastUpdated }
            .mapNotNull { p -> movies.find { it.id == p.movieId } }
        
        val continueWatchingMovies = allContinueWatchingMovies.take(10)
        val totalContinueWatching = allContinueWatchingMovies.size
        
        if (continueWatchingMovies.isNotEmpty()) {
            rails.add(MovieRail("Continue Watching", continueWatchingMovies, totalContinueWatching, seeMoreQuery = "history:*"))
        }

        // Liked Movies — preserves order from DB (most recently liked first)
        if (likedIds.isNotEmpty()) {
            val allLikedMovies = likedIds.mapNotNull { id -> movies.find { it.id == id } }
            val likedMoviesPreview = allLikedMovies.take(10)
            if (likedMoviesPreview.isNotEmpty()) {
                rails.add(MovieRail("Liked Movies", likedMoviesPreview, allLikedMovies.size, seeMoreQuery = "liked:*"))
            }
        }

        // Recommended for You — appears only when user has activity
        if (recommendations.isNotEmpty()) {
            rails.add(MovieRail(
                "Recommended for You",
                recommendations.take(10),
                recommendations.size,
                seeMoreQuery = "recommended:*"
            ))
        }

        // New Releases — sorted by last_updated (latest first)
        rails.add(MovieRail("New Releases", movies.sortedByLastUpdated().take(10), seeMoreQuery = "all:*"))
        
        // Genre rails — sorted by last_updated
        val allGenres = movies.flatMap { it.genres }.distinct()
        allGenres.take(5).forEach { genre ->
            val genreMovies = movies.filter { it.genres.contains(genre) }.sortedByLastUpdated().take(10)
            if (genreMovies.isNotEmpty()) {
                rails.add(MovieRail(genre, genreMovies, seeMoreQuery = "genre:$genre"))
            }
        }
        
        // Year rails — sorted by last_updated
        val years = movies.map { it.year }.distinct().sortedDescending()
        years.take(3).forEach { year ->
            val yearMovies = movies.filter { it.year == year }.sortedByLastUpdated().take(10)
            rails.add(MovieRail("Released in $year", yearMovies, seeMoreQuery = "year:$year"))
        }

        return rails
    }

    /**
     * Sorts movies by lastUpdated descending (latest first).
     * Movies with empty lastUpdated go to the end.
     */
    private fun List<Movie>.sortedByLastUpdated(): List<Movie> {
        return sortedWith(compareByDescending<Movie> { it.lastUpdated.isNotBlank() }
            .thenByDescending { it.lastUpdated })
    }
}
