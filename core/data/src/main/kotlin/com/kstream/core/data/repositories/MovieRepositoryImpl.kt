package com.kstream.core.data.repositories

import com.google.gson.Gson
import com.kstream.core.data.db.daos.MovieDao
import com.kstream.core.data.db.daos.YearDao
import com.kstream.core.data.db.entities.YearEntity
import com.kstream.core.data.paging.MoviePagingSource
import com.kstream.core.domain.models.*
import com.kstream.core.network.WorkerApi
import kotlinx.coroutines.flow.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val api: WorkerApi,
    private val movieDao: MovieDao,
    private val yearDao: YearDao,
    private val gson: Gson
) : MovieRepository {

    // Improved SWR implementation
    override fun getMovieDetail(path: String): Flow<Resource<MovieDetail>> = flow {
        emit(Resource.Loading)
        val cached = movieDao.getMovieByPath(path)
        if (cached != null) {
            emit(Resource.Success(cached.toDomain(gson), fromCache = true))
            if (System.currentTimeMillis() - cached.cachedAt > 24 * 60 * 60 * 1000) {
                try {
                    val response = api.fetchMovieDetail(path)
                    val entity = response.toEntity(path, gson)
                    movieDao.insertWithEviction(entity)
                    emit(Resource.Success(entity.toDomain(gson), fromCache = false))
                } catch (e: Exception) {
                    // Silently fail if we have stale data
                }
            } else {
                movieDao.updateLastAccessed(path)
            }
        } else {
            try {
                val response = api.fetchMovieDetail(path)
                val entity = response.toEntity(path, gson)
                movieDao.insertWithEviction(entity)
                emit(Resource.Success(entity.toDomain(gson), fromCache = false))
            } catch (e: Exception) {
                emit(Resource.Error(e.message ?: "Network error"))
            }
        }
    }

        // I'll adjust this to emit the fresh data.
    }

    // Improved SWR implementation
    override fun getMovieDetail(path: String): Flow<Resource<MovieDetail>> = flow {
        emit(Resource.Loading)
        val cached = movieDao.getMovieByPath(path)
        if (cached != null) {
            emit(Resource.Success(cached.toDomain(gson), fromCache = true))
            if (System.currentTimeMillis() - cached.cachedAt > 24 * 60 * 60 * 1000) {
                try {
                    val response = api.fetchMovieDetail(path)
                    val entity = response.toEntity(path, gson)
                    movieDao.insertWithEviction(entity)
                    emit(Resource.Success(entity.toDomain(gson), fromCache = false))
                } catch (e: Exception) {
                    // Silently fail if we have stale data
                }
            } else {
                movieDao.updateLastAccessed(path)
            }
        } else {
            try {
                val response = api.fetchMovieDetail(path)
                val entity = response.toEntity(path, gson)
                movieDao.insertWithEviction(entity)
                emit(Resource.Success(entity.toDomain(gson), fromCache = false))
            } catch (e: Exception) {
                emit(Resource.Error(e.message ?: "Network error"))
            }
        }
    }

    override fun getMoviesByYear(year: String): Flow<PagingData<MovieItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 5,
                enablePlaceholders = true
            ),
            pagingSourceFactory = { MoviePagingSource(api, movieDao, year) }
        ).flow
    }

    override fun getYears(): Flow<Resource<List<YearItem>>> = flow {
        emit(Resource.Loading)
        // Simple implementation for now
        try {
            val response = api.fetchFolders("") // Base URL fetch for years
            val years = response.folders.map { FolderItem ->
                YearItem(FolderItem.name.replace("/", ""), 0)
            }
            emit(Resource.Success(years, fromCache = false))
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Error fetching years"))
        }
    }

    override suspend fun pingSource(url: String): Boolean {
        return try {
            api.pingSource(url).ok
        } catch (e: Exception) {
            false
        }
    }
}
