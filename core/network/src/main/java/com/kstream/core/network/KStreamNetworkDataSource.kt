package com.kstream.core.network

import com.kstream.core.network.model.NetworkMovie
import com.kstream.core.network.model.NetworkMovieWithMedia

interface KStreamNetworkDataSource {
    suspend fun getMovies(): List<NetworkMovie>
    suspend fun getMovieWithMedia(movieId: String): NetworkMovieWithMedia?
    suspend fun searchMovies(query: String): List<NetworkMovie>
}
