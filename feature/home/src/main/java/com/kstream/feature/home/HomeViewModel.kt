package com.kstream.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.common.toUserMessage
import com.kstream.core.domain.GetMoviesUseCase
import com.kstream.core.domain.SyncMoviesUseCase
import com.kstream.core.domain.StartupSyncManager
import com.kstream.core.domain.GetAllWatchProgressUseCase
import com.kstream.core.domain.GetRecommendationsUseCase
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.model.Movie
import com.kstream.core.model.WatchProgress
import com.kstream.core.domain.repository.LikedMovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val userName: String = "",
    val rails: List<MovieRail> = emptyList(),
    val heroMovies: List<Movie> = emptyList(),
    val watchProgressMap: Map<String, WatchProgress> = emptyMap(),
    val error: String? = null,
    val hdOnly: Boolean = false
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
    private val startupSyncManager: StartupSyncManager,
    private val getAllWatchProgressUseCase: GetAllWatchProgressUseCase,
    private val getRecommendationsUseCase: GetRecommendationsUseCase,
    private val likedMovieRepository: LikedMovieRepository,
    private val downloadRepository: DownloadRepository,
    private val userDataRepository: com.kstream.core.domain.repository.UserDataRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    
    private var lastSyncTime = 0L
    private val syncDebounceMs = 5000L
    private var isSyncing = false
    
    init {
        refreshContent()
        observeData()
        observeNetworkChanges()
        observeActivityForRecommendations()
    }

    private fun observeNetworkChanges() {
        isOnline
            .filter { online -> online }
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
            data class CombinedData(val movies: List<Movie>, val progress: List<WatchProgress>, val username: String, val likedIds: List<String>, val recommendations: List<Movie>)
            CombinedData(movies, progress, username, likedIds, recommendations)
        }.combine(
            combine(
                userDataRepository.isHdOnlyFilter.catch { emit(false) },
                userDataRepository.isCarouselEnabled.catch { emit(true) }
            ) { hd, carousel -> Pair(hd, carousel) }
        ) { data, prefs ->
            val (hdOnly, carouselEnabled) = prefs
            val filteredMovies = if (hdOnly) data.movies.filter { it.type.equals("Original HD", ignoreCase = true) } else data.movies
            val filteredRecs = if (hdOnly) data.recommendations.filter { it.type.equals("Original HD", ignoreCase = true) } else data.recommendations
            val rails = groupMoviesIntoRails(filteredMovies, data.progress, data.likedIds, filteredRecs)
            val heroMovies = if (carouselEnabled) buildHeroMovies(filteredMovies, filteredRecs) else emptyList()
            _uiState.update {
                it.copy(
                    isLoading = if (rails.isEmpty() && isSyncing) true else false,
                    userName = data.username,
                    rails = rails,
                    heroMovies = heroMovies,
                    watchProgressMap = data.progress.associateBy { p -> p.movieId },
                    error = if (rails.isNotEmpty()) null else it.error,
                    hdOnly = hdOnly
                )
            }
        }.flowOn(Dispatchers.Default).launchIn(viewModelScope)
    }

    fun refreshContent() {
        viewModelScope.launch {
            try {
                isSyncing = true
                // Only show loading if no cached data — otherwise show stale data immediately
                if (_uiState.value.rails.isEmpty()) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                } else {
                    _uiState.update { it.copy(error = null) }
                }
                lastSyncTime = System.currentTimeMillis()

                // Sync ensures latest movie data; recommendations refresh in parallel.
                kotlinx.coroutines.coroutineScope {
                    val splashSynced = startupSyncManager.awaitOrSkip()
                    if (!splashSynced) {
                        syncMoviesUseCase()
                    }

                    launch {
                        try { getRecommendationsUseCase.refreshRecommendations() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                if (isOnline.value && _uiState.value.rails.isEmpty()) {
                    _uiState.update { it.copy(error = e.toUserMessage(), isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } finally {
                isSyncing = false
            }
        }
    }

    fun toggleHdOnlyFilter() {
        viewModelScope.launch {
            userDataRepository.setHdOnlyFilter(!_uiState.value.hdOnly)
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
    }

    private fun buildHeroMovies(movies: List<Movie>, recommendations: List<Movie>): List<Movie> {
        val seen = mutableSetOf<String>()
        val hero = mutableListOf<Movie>()
        fun parseDate(s: String): Long = try { if (s.isNotBlank()) DATE_FORMAT.parse(s)?.time ?: 0L else 0L } catch (_: Exception) { 0L }

        // Top 5 from new releases
        movies.sortedByLastUpdated().take(5).forEach { m ->
            if (seen.add(m.id)) hero.add(m)
        }

        // Top 3 from recommendations
        recommendations.take(3).forEach { m ->
            if (seen.add(m.id)) hero.add(m)
        }

        // Top 3 highest-rated (exclude ratings >= 10)
        movies
            .filter { it.rating.isNotBlank() && (it.rating.toDoubleOrNull() ?: 0.0) < 10.0 }
            .sortedWith(compareByDescending<Movie> { it.rating.toDoubleOrNull() ?: 0.0 }.thenByDescending { parseDate(it.lastUpdated) })
            .take(3)
            .forEach { m -> if (seen.add(m.id)) hero.add(m) }

        return hero
    }

    private fun groupMoviesIntoRails(
        movies: List<Movie>,
        progress: List<WatchProgress>,
        likedIds: List<String>,
        recommendations: List<Movie>
    ): List<MovieRail> {
        val rails = mutableListOf<MovieRail>()
        
        // New Releases — sorted by last_updated (latest first)
        if (movies.isNotEmpty()) {
            rails.add(MovieRail("New Releases", movies.sortedByLastUpdated().take(10), seeMoreQuery = "all:*"))
        }

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

        // Recommended for You — appears only when user has activity
        if (recommendations.isNotEmpty()) {
            rails.add(MovieRail(
                "Recommended for You",
                recommendations.take(10),
                recommendations.size,
                seeMoreQuery = "recommended:*"
            ))
        }

        // Liked Movies — preserves order from DB (most recently liked first)
        if (likedIds.isNotEmpty()) {
            val allLikedMovies = likedIds.mapNotNull { id -> movies.find { it.id == id } }
            val likedMoviesPreview = allLikedMovies.take(10)
            if (likedMoviesPreview.isNotEmpty()) {
                rails.add(MovieRail("Liked Movies", likedMoviesPreview, allLikedMovies.size, seeMoreQuery = "liked:*"))
            }
        }
        
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
            if (yearMovies.isNotEmpty()) {
                rails.add(MovieRail("Released in $year", yearMovies, seeMoreQuery = "year:$year"))
            }
        }

        return rails
    }

    /**
     * Sorts movies by lastUpdated descending (latest first).
     * Movies with empty lastUpdated go to the end.
     */
    private fun List<Movie>.sortedByLastUpdated(): List<Movie> {
        return sortedByDescending { movie ->
            try {
                if (movie.lastUpdated.isNotBlank()) DATE_FORMAT.parse(movie.lastUpdated)?.time ?: 0L
                else 0L
            } catch (_: Exception) { 0L }
        }
    }

    companion object {
        private val DATE_FORMAT = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.ENGLISH)
    }
}
