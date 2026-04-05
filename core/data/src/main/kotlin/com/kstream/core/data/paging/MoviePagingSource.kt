package com.kstream.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.kstream.core.data.db.daos.MovieDao
import com.kstream.core.domain.models.MovieItem
import com.kstream.core.network.WorkerApi
import java.io.IOException

class MoviePagingSource(
    private val api: WorkerApi,
    private val movieDao: MovieDao,
    private val year: String
) : PagingSource<Int, MovieItem>() {

    override fun getRefreshKey(state: PagingState<Int, MovieItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MovieItem> {
        val position = params.key ?: 1
        return try {
            val response = api.fetchPaginatedFolders("$year?page=$position")
            val movies = response.folders.map { 
                MovieItem(
                    path = it.href,
                    title = it.name.replace("/", ""),
                    year = year,
                    posterUrl = null // Poster fetch happens separately
                )
            }
            
            LoadResult.Page(
                data = movies,
                prevKey = if (position == 1) null else position - 1,
                nextKey = if (response.hasNextPage) position + 1 else null
            )
        } catch (exception: IOException) {
            LoadResult.Error(exception)
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }
}
