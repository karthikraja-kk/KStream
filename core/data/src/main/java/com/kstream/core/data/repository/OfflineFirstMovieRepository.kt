package com.kstream.core.data.repository

import com.kstream.core.data.local.dao.MovieDao
import com.kstream.core.data.local.entities.MovieEntity
import com.kstream.core.network.KStreamNetworkDataSource
import com.kstream.core.network.model.asExternalModel
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia
import com.kstream.core.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstMovieRepository @Inject constructor(
    private val movieDao: MovieDao,
    private val network: KStreamNetworkDataSource
) : MovieRepository {

    override fun getMovies(): Flow<List<Movie>> {
        return movieDao.getMovies().map { entities ->
            entities.map { it.asExternalModel() }
        }
    }

    override suspend fun getMovieWithMedia(movieId: String): MovieWithMedia? {
        return network.getMovieWithMedia(movieId)?.asExternalModel()
    }

    override suspend fun syncMovies() {
        val networkMovies = network.getMovies()
        movieDao.insertMovies(networkMovies.map { it.asLocalEntity() })
    }

    override suspend fun searchMovies(query: String): List<Movie> {
        return network.searchMovies(query).map { it.asExternalModel() }
    }
}

private fun MovieEntity.asExternalModel() = Movie(
    id = id,
    movieName = movieName,
    year = year,
    posterUrl = posterUrl,
    duration = duration,
    synopsis = synopsis,
    director = director.split(","),
    castMembers = castMembers.split(","),
    genres = genres.split(","),
    rating = rating,
    language = language,
    type = type,
    slug = slug
)

private fun com.kstream.core.network.model.NetworkMovie.asLocalEntity() = MovieEntity(
    id = id,
    movieName = movieName,
    year = year,
    posterUrl = posterUrl,
    duration = duration,
    synopsis = synopsis,
    director = director.joinToString(","),
    castMembers = castMembers.joinToString(","),
    genres = genres.joinToString(","),
    rating = rating,
    language = language,
    type = type,
    slug = slug
)
