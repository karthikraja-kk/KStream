package com.kstream.tv.ui.liked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.repository.LikedMovieRepository
import com.kstream.core.domain.repository.MovieRepository
import com.kstream.tv.ui.personal.PersonalMovieItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LikedMoviesUiState(
    val items: List<PersonalMovieItem> = emptyList(),
    val selectMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
)

@HiltViewModel
class LikedMoviesViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val likedMovieRepository: LikedMovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LikedMoviesUiState())
    val uiState: StateFlow<LikedMoviesUiState> = _uiState.asStateFlow()

    init {
        observeLikedMovies()
    }

    private fun observeLikedMovies() {
        combine(
            movieRepository.getMovies(),
            likedMovieRepository.getAllLikedMovieIds()
        ) { movies, likedIds ->
            val movieMap = movies.associateBy { it.id }
            likedIds.map { id ->
                val movie = movieMap[id]
                PersonalMovieItem(
                    id = id,
                    title = movie?.movieName ?: "Unknown movie",
                    posterUrl = movie?.posterUrl.orEmpty(),
                    badge = "",
                    movie = movie
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .catch { emit(emptyList()) }
            .onEach { items ->
                _uiState.update { state ->
                    val visibleIds = items.mapTo(mutableSetOf()) { it.id }
                    val selected = state.selectedIds.intersect(visibleIds)
                    state.copy(
                        items = items,
                        selectedIds = selected,
                        selectMode = state.selectMode && items.isNotEmpty(),
                        isLoading = false,
                        isEmpty = items.isEmpty()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun enterSelectMode(initialId: String? = null) {
        _uiState.update { state ->
            if (state.items.isEmpty()) return@update state
            state.copy(
                selectMode = true,
                selectedIds = initialId?.let { state.selectedIds + it } ?: state.selectedIds
            )
        }
    }

    fun toggleSelection(movieId: String) {
        _uiState.update { state ->
            val selected = if (movieId in state.selectedIds) state.selectedIds - movieId else state.selectedIds + movieId
            state.copy(selectMode = true, selectedIds = selected)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectMode = true, selectedIds = it.items.mapTo(mutableSetOf()) { item -> item.id }) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun exitSelectMode() {
        _uiState.update { it.copy(selectMode = false, selectedIds = emptySet()) }
    }

    fun unlikeSelected() {
        val ids = _uiState.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            likedMovieRepository.unlikeAll(ids)
            exitSelectMode()
            _events.tryEmit("Unliked ${ids.size}")
        }
    }

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: kotlinx.coroutines.flow.SharedFlow<String> = _events
}
