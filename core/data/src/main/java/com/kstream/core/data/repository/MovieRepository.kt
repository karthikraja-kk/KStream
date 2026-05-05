package com.kstream.core.data.repository

import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    fun getMovies(): Flow<List<Movie>>
    suspend fun getMovieWithMedia(movieId: String): MovieWithMedia?
    suspend fun syncMovies()
    suspend fun searchMovies(query: String): List<Movie>
}
