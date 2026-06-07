package com.kstream.tv.ui.home

import com.kstream.core.model.Movie
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide signal carrying the currently-focused movie in the Home rails.
 *
 * The hero subscribes and mirrors this movie (backdrop + title); rails publish
 * via [push]. Singleton-scoped so that the rails and hero fragments stay loosely
 * coupled (they don't need to share a ViewModel).
 *
 * Consumers should `.debounce(100.milliseconds)` to avoid thrashing the hero
 * during rapid D-pad scrolling.
 */
@Singleton
class FocusedMovieRelay @Inject constructor() {
    private val _focused = MutableStateFlow<Movie?>(null)
    val focused: StateFlow<Movie?> = _focused.asStateFlow()
    fun push(movie: Movie?) { _focused.value = movie }
}
