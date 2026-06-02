package com.kstream.core.enrichment.tmdb

import com.kstream.core.enrichment.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Thin OkHttp + kotlinx.serialization wrapper around TMDb v3.
 *
 *  - All calls run on [Dispatchers.IO].
 *  - JSON parser is `ignoreUnknownKeys = true` and `isLenient = true` — TMDb
 *    evolves; we never want a schema drift to crash the app.
 *  - Returns null on any HTTP / parse failure (caller treats null as
 *    "no enrichment available right now").
 */
internal class TmdbClient(
    private val apiKey: String = BuildConfig.TMDB_API_KEY,
    private val baseUrl: String = BuildConfig.TMDB_BASE_URL,
    private val client: OkHttpClient = defaultClient()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun searchMovie(title: String, year: Int?): TmdbSearchResponse? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val urlBuilder = (baseUrl + "search/movie").toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("query", title)
                .addQueryParameter("include_adult", "false")
            if (year != null && year > 0) urlBuilder.addQueryParameter("year", year.toString())
            val req = Request.Builder().url(urlBuilder.build()).get().build()
            return@withContext runCatching {
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val body = res.body?.string() ?: return@use null
                    json.decodeFromString(TmdbSearchResponse.serializer(), body)
                }
            }.getOrNull()
        }

    suspend fun fetchDetail(movieId: Int): TmdbMovieDetail? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val url = (baseUrl + "movie/$movieId").toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("append_to_response", "credits,images,reviews,release_dates")
                .addQueryParameter("include_image_language", "en,null")
                .build()
            val req = Request.Builder().url(url).get().build()
            return@withContext runCatching {
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val body = res.body?.string() ?: return@use null
                    json.decodeFromString(TmdbMovieDetail.serializer(), body)
                }
            }.getOrNull()
        }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
