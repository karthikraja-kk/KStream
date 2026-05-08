package com.kstream.core.model

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

data class Media(
    val movieId: String,
    val quality: String,
    val fileSize: String,
    val downloadUrl1: String? = null,
    val downloadUrl2: String? = null,
    val watchUrl1: String? = null,
    val watchUrl2: String? = null
)

data class MovieWithMedia(
    val movie: Movie,
    val media: List<Media> = emptyList()
)
