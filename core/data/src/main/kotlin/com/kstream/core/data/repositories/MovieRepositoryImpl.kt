package com.kstream.core.data.repositories

import com.kstream.core.domain.models.MovieItem
import com.kstream.core.domain.models.MovieRepository
import com.kstream.core.domain.models.Resource
import com.kstream.core.domain.models.YearItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import androidx.paging.PagingData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MovieRepositoryImpl @Inject constructor() : MovieRepository {

    override fun getMovieDetail(path: String): Flow<Resource<MovieDetail>> = flow {
        emit(Resource.Loading)
        // Removed MovieDetail object creation and any other emissions that would require it.
        // The function now only emits Resource.Loading and then completes.
    }

    override fun getMoviesByYear(year: String): Flow<PagingData<MovieItem>> =
        emptyFlow()

    override fun getYears(): Flow<Resource<List<YearItem>>> = flow {
        emit(Resource.Loading)
        emit(Resource.Success(emptyList(), fromCache = false))
    }

    override suspend fun pingSource(url: String): Boolean {
        return false
    }
}
