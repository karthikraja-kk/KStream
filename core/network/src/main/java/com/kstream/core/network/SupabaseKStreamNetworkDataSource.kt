package com.kstream.core.network

import android.util.Log
import com.kstream.core.network.model.NetworkMovie
import com.kstream.core.network.model.NetworkMovieWithMedia
import com.kstream.core.network.model.NetworkMedia
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
}
