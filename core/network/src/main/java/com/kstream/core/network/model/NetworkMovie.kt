package com.kstream.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkMovie(
    @SerialName("id") val id: String,
    @SerialName("movie_name") val movieName: String,
    @SerialName("year") val year: Int,
    @SerialName("poster_url") val posterUrl: String,
    @SerialName("duration") val duration: String,
    @SerialName("synopsis") val synopsis: String,
    @SerialName("director") val director: List<String>,
    @SerialName("cast_members") val castMembers: List<String>,
    @SerialName("genres") val genres: List<String>,
    @SerialName("rating") val rating: String,
    @SerialName("language") val language: String,
    @SerialName("type") val type: String,
    @SerialName("slug") val slug: String
)

@Serializable
data class NetworkMedia(
    @SerialName("movie_id") val movieId: String,
    @SerialName("quality") val quality: String,
    @SerialName("file_size") val fileSize: String,
    @SerialName("download_url_1") val downloadUrl1: String? = null,
    @SerialName("download_url_2") val downloadUrl2: String? = null,
    @SerialName("watch_url_1") val watchUrl1: String? = null,
    @SerialName("watch_url_2") val watchUrl2: String? = null
)

@Serializable
data class NetworkMovieWithMedia(
    @SerialName("id") val id: String,
    @SerialName("movie_name") val movieName: String,
    @SerialName("year") val year: Int,
    @SerialName("poster_url") val posterUrl: String,
    @SerialName("duration") val duration: String,
    @SerialName("synopsis") val synopsis: String,
    @SerialName("director") val director: List<String>,
    @SerialName("cast_members") val castMembers: List<String>,
    @SerialName("genres") val genres: List<String>,
    @SerialName("rating") val rating: String,
    @SerialName("language") val language: String,
    @SerialName("type") val type: String,
    @SerialName("slug") val slug: String,
    @SerialName("media") val media: List<NetworkMedia> = emptyList()
)
