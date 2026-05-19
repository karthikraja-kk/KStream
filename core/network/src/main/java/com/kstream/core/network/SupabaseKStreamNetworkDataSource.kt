package com.kstream.core.network

import android.util.Log
import com.kstream.core.network.model.NetworkMovie
import com.kstream.core.network.model.NetworkMovieWithMedia
import com.kstream.core.network.model.NetworkMedia
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.call.body
import javax.inject.Inject

class SupabaseKStreamNetworkDataSource @Inject constructor(
    private val client: SupabaseClient
) : KStreamNetworkDataSource {

    override suspend fun getMovies(): List<NetworkMovie> {
        return try {
            Log.d("KStreamNetwork", "Fetching movies from Supabase...")
            val response = client.postgrest["movies"].select()
            val movies = response.decodeList<NetworkMovie>()
            Log.d("KStreamNetwork", "Successfully fetched ${movies.size} movies. Raw data: ${response.data}")
            movies
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error fetching movies: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getMovieWithMedia(movieId: String): NetworkMovieWithMedia? {
        return try {
            Log.d("KStreamNetwork", "Fetching movie details for ID: $movieId")
            val movie = client.postgrest["movies"]
                .select {
                    filter {
                        eq("id", movieId)
                    }
                }
                .decodeSingleOrNull<NetworkMovieWithMedia>()
            
            if (movie == null) {
                Log.d("KStreamNetwork", "Movie not found")
                return null
            }
            
            val media = client.postgrest["media"]
                .select {
                    filter {
                        eq("movie_id", movieId)
                    }
                }
                .decodeList<NetworkMedia>()
            
            Log.d("KStreamNetwork", "Found movie: ${movie.movieName}, media count: ${media.size}")
            movie.copy(media = media)
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error fetching movie details: ${e.message}", e)
            throw e
        }
    }

    override suspend fun searchMovies(query: String): List<NetworkMovie> {
        return try {
            Log.d("KStreamNetwork", "Searching movies for query: $query")
            val movies = client.postgrest["movies"]
                .select {
                    filter {
                        ilike("movie_name", "%$query%")
                    }
                }
                .decodeList<NetworkMovie>()
            Log.d("KStreamNetwork", "Found ${movies.size} movies for search query")
            movies
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error searching movies: ${e.message}", e)
            throw e
        }
    }

    override suspend fun getBaseUrl(): String {
        return try {
            Log.d("KStreamNetwork", "Fetching base URL from source table...")
            val response = client.postgrest["source"]
                .select {
                    filter {
                        eq("key", "base_url")
                    }
                }
            val result = response.decodeList<SourceEntry>()
            val url = result.firstOrNull()?.url ?: ""
            Log.d("KStreamNetwork", "Base URL: $url")
            url
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error fetching base URL: ${e.message}", e)
            ""
        }
    }

    override suspend fun refreshMovieMedia(slug: String): RefreshMediaResult {
        return try {
            Log.d("KStreamNetwork", "Calling refresh-media Edge Function for slug: $slug")

            val response = client.functions.invoke("refresh-media") {
                body = kotlinx.serialization.json.buildJsonObject {
                    put("slug", kotlinx.serialization.json.JsonPrimitive(slug))
                }
            }
            val bodyStr = response.body<String>()
            Log.d("KStreamNetwork", "Edge Function response: $bodyStr")

            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val parsed = json.decodeFromString<RefreshMediaResponse>(bodyStr)

            when (parsed.status) {
                "queued" -> {
                    Log.d("KStreamNetwork", "Refresh queued for slug: $slug")
                    RefreshMediaResult.Queued
                }
                "processing" -> {
                    Log.d("KStreamNetwork", "Refresh already processing for slug: $slug")
                    RefreshMediaResult.Processing
                }
                "done", "cached" -> {
                    Log.d("KStreamNetwork", "Refresh complete (${parsed.status}) for slug: $slug")
                    RefreshMediaResult.Done
                }
                "failed" -> {
                    Log.e("KStreamNetwork", "Refresh failed: ${parsed.error}")
                    RefreshMediaResult.Failed(parsed.error ?: "Refresh failed")
                }
                else -> {
                    Log.e("KStreamNetwork", "Unknown refresh status: ${parsed.status}")
                    RefreshMediaResult.Failed(parsed.error ?: "Unknown error")
                }
            }
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error refreshing media: ${e.message}", e)
            RefreshMediaResult.Failed(e.message ?: "Network error")
        }
    }
    override suspend fun triggerMovieScan(): ScanTriggerResponse {
        return try {
            Log.d("KStreamNetwork", "Calling trigger-scan Edge Function")
            val response = client.functions.invoke("trigger-scan")
            val bodyStr = response.body<String>()
            Log.d("KStreamNetwork", "Trigger scan response: $bodyStr")

            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<ScanTriggerResponse>(bodyStr)
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error triggering scan: ${e.message}", e)
            ScanTriggerResponse(status = "error", error = e.message)
        }
    }

    override suspend fun getScanStatus(): ScanStatusEntry? {
        return try {
            val result = client.postgrest["refresh_status"]
                .select {
                    order("refresh_time", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(1)
                }
                .decodeList<ScanStatusEntry>()
            result.firstOrNull()
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error fetching scan status: ${e.message}", e)
            null
        }
    }

    override suspend fun getLatestCompletedScanStatus(): ScanStatusEntry? {
        return try {
            val result = client.postgrest["refresh_status"]
                .select {
                    filter {
                        eq("status", "completed")
                    }
                    order("refresh_time", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(1)
                }
                .decodeList<ScanStatusEntry>()
            result.firstOrNull()
        } catch (e: Exception) {
            Log.e("KStreamNetwork", "Error fetching latest completed scan status: ${e.message}", e)
            null
        }
    }
}

@kotlinx.serialization.Serializable
private data class SourceEntry(
    @kotlinx.serialization.SerialName("key") val key: String,
    @kotlinx.serialization.SerialName("url") val url: String
)

@kotlinx.serialization.Serializable
private data class RefreshMediaResponse(
    val status: String? = null,
    val error: String? = null,
    val media: kotlinx.serialization.json.JsonElement? = null
)
