package com.kstream.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkMovie(
    @SerialName("id") val id: String,
    @SerialName("movie_name") val movieName: String,
    @SerialName("year") val year: Int?,
    @SerialName("poster_url") val posterUrl: String?,
    @SerialName("duration") val duration: String?,
    @SerialName("synopsis") val synopsis: String?,
    @SerialName("director") val director: List<String>?,
    @SerialName("cast_members") val castMembers: List<String>?,
    @SerialName("genres") val genres: List<String>?,
    @SerialName("rating") val rating: String?,
    @SerialName("language") val language: String?,
    @SerialName("type") val type: String?,
    @SerialName("slug") val slug: String,
    @SerialName("movie_url") val movieUrl: String,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("created_at") val createdAt: Long? = null,
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class NetworkMovieWithMedia(
    @SerialName("id") val id: String,
    @SerialName("movie_name") val movieName: String,
    @SerialName("year") val year: Int?,
    @SerialName("poster_url") val posterUrl: String?,
    @SerialName("duration") val duration: String?,
    @SerialName("synopsis") val synopsis: String?,
    @SerialName("director") val director: List<String>?,
    @SerialName("cast_members") val castMembers: List<String>?,
    @SerialName("genres") val genres: List<String>?,
    @SerialName("rating") val rating: String?,
    @SerialName("language") val language: String?,
    @SerialName("type") val type: String?,
    @SerialName("slug") val slug: String,
    @SerialName("movie_url") val movieUrl: String? = null,
    @SerialName("last_updated") val lastUpdated: String? = null,
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("created_at") val createdAt: Long? = null,
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("media") val media: List<NetworkMedia> = emptyList()
)




