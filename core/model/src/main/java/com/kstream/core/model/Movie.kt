package com.kstream.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Movie(
    val id: String,
    val movieName: String,
    val year: Int,
    val posterUrl: String,
    val duration: String,
    val synopsis: String,
    val director: List<String>,
    val castMembers: List<String>,
    val genres: List<String>,
    val rating: String,
    val language: String,
    val type: String,
    val slug: String
)

@Serializable
data class Media(
    val movieId: String,
    val quality: String,
    val fileSize: String,
    val downloadUrl1: String?,
    val downloadUrl2: String?,
    val watchUrl1: String?,
    val watchUrl2: String?
)

@Serializable
data class DownloadMetadata(
    val movieName: String,
    val posterUrl: String,
    val quality: String,
    val fileSize: String
)

data class MovieWithMedia(
    val movie: Movie,
    val media: List<Media>
)
