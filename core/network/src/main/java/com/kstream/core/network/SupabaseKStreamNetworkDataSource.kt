package com.kstream.core.network

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
        return client.postgrest["movies"]
            .select(columns = Columns.raw("id, movie_name, year, poster_url, duration, synopsis, director, cast_members, genres, rating, language, type, slug"))
            .decodeList<NetworkMovie>()
    }

    override suspend fun getMovieWithMedia(movieId: String): NetworkMovieWithMedia? {
        return client.postgrest["movies"]
            .select(columns = Columns.raw("*, media(*)")) {
                filter {
                    eq("id", movieId)
                }
            }
            .decodeSingleOrNull<NetworkMovieWithMedia>()
    }

    override suspend fun searchMovies(query: String): List<NetworkMovie> {
        return client.postgrest["movies"]
            .select {
                filter {
                    ilike("movie_name", "%$query%")
                }
            }
            .decodeList<NetworkMovie>()
    }
}
