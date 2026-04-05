package com.kstream.core.network

import com.kstream.core.network.responses.*
import retrofit2.http.GET
import retrofit2.http.Query

interface WorkerApi {
    @GET("ping")
    suspend fun pingSource(@Query("url") url: String): PingResponse

    @GET("fetch")
    suspend fun fetchFolders(@Query("url") url: String): FolderResponse

    @GET("paginated")
    suspend fun fetchPaginatedFolders(@Query("url") url: String): PaginatedResponse

    @GET("movie")
    suspend fun fetchMovieDetail(@Query("url") url: String): MovieDetailResponse

    @GET("poster")
    suspend fun fetchPoster(@Query("url") url: String): PosterResponse
}
