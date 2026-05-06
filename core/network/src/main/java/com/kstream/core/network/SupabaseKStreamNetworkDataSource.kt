package com.kstream.core.network

import android.util.Log
import com.kstream.core.network.model.NetworkMovie
import com.kstream.core.network.model.NetworkMovieWithMedia
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject

class SupabaseKStreamNetworkDataSource @Inject constructor(
    private val client: SupabaseClient
) : KStreamNetworkDataSource {

    override suspend fun getMovies(): List<NetworkMovie> {
        return try {
            Log.d("KStreamNetwork", "Fetching movies from Supabase...")
            val movies = client.postgrest["movies"]
                .select()
                .decodeList<NetworkMovie>()
            Log.d("KStreamNetwork", "Successfully fetched ${movies.size} movies")
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
                .select(columns = Columns.raw("*, media(*)")) {
                    filter {
                        eq("id", movieId)
                    }
                }
                .decodeSingleOrNull<NetworkMovieWithMedia>()
            Log.d("KStreamNetwork", "Found movie: ${movie?.movieName ?: "null"}")
            movie
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
}
