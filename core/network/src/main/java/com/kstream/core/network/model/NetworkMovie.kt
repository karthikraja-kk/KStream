package com.kstream.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkMovie(
    @SerialName("id") val id: String,
    @SerialName("movie_name") val movieName: String,
    @SerialName("year") val year: Int?, // Made nullable
    @SerialName("poster_url") val posterUrl: String?, // Made nullable
    @SerialName("duration") val duration: String?, // Made nullable
    @SerialName("synopsis") val synopsis: String?, // Already nullable
    @SerialName("director") val director: List<String>?, // Made nullable
    @SerialName("cast_members") val castMembers: List<String>?, // Made nullable
    @SerialName("genres") val genres: List<String>?, // Made nullable
    @SerialName("rating") val rating: String?, // Made nullable
    @SerialName("language") val language: String?, // Made nullable
    @SerialName("type") val type: String?, // Made nullable
    @SerialName("slug") val slug: String,
    @SerialName("movie_url") val movieUrl: String,
    // Apply the custom serializer for created_at and updated_at
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("created_at") val createdAt: Long? = null,
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class NetworkMovieWithMedia(
    @SerialName("id") val id: String,
    @SerialName("movie_name") val movieName: String,
    @SerialName("year") val year: Int?, // Made nullable
    @SerialName("poster_url") val posterUrl: String?, // Made nullable
    @SerialName("duration") val duration: String?, // Made nullable
    @SerialName("synopsis") val synopsis: String?, // Already nullable
    @SerialName("director") val director: List<String>?, // Made nullable
    @SerialName("cast_members") val castMembers: List<String>?, // Made nullable
    @SerialName("genres") val genres: List<String>?, // Made nullable
    @SerialName("rating") val rating: String?, // Made nullable
    @SerialName("language") val language: String?, // Made nullable
    @SerialName("type") val type: String?, // Made nullable
    @SerialName("slug") val slug: String,
    @SerialName("movie_url") val movieUrl: String? = null,
    // Apply the custom serializer for created_at and updated_at
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("created_at") val createdAt: Long? = null,
    @Serializable(with = DateTimeAsLongSerializer::class)
    @SerialName("updated_at") val updatedAt: Long? = null,
    // If 'media' itself can be null from API, change to:
    // @SerialName("media") val media: List<NetworkMedia>? = null
    // Otherwise, List<NetworkMedia> is fine if it's always an empty list []
    @SerialName("media") val media: List<NetworkMedia> = emptyList()
)




