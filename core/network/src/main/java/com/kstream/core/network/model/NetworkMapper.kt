package com.kstream.core.network.model

import com.kstream.core.model.Media
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia

fun NetworkMovie.asExternalModel() = Movie(
    id = id,
    movieName = movieName,
    year = year ?: 0, // Provide default for Int?
    posterUrl = posterUrl ?: "", // Provide default for String?
    duration = duration ?: "", // Provide default for String?
    synopsis = synopsis ?: "", // Provide default for String?
    director = director ?: emptyList(), // Provide default for List<String>?
    castMembers = castMembers ?: emptyList(), // Provide default for List<String>?
    genres = genres ?: emptyList(), // Provide default for List<String>?
    rating = rating ?: "", // Provide default for String?
    language = language ?: "", // Provide default for String?
    type = type ?: "", // Provide default for String?
    slug = slug
)

fun NetworkMedia.asExternalModel() = Media(
    movieId = movieId,
    quality = quality ?: "", // Provide default for String?
    fileSize = fileSize ?: "", // Provide default for String?
    downloadUrl1 = downloadUrl1,
    downloadUrl2 = downloadUrl2,
    watchUrl1 = watchUrl1,
    watchUrl2 = watchUrl2
)

fun NetworkMovieWithMedia.asExternalModel() = MovieWithMedia(
    movie = Movie(
        id = id,
        movieName = movieName,
        year = year ?: 0, // Provide default for Int?
        posterUrl = posterUrl ?: "", // Provide default for String?
        duration = duration ?: "", // Provide default for String?
        synopsis = synopsis ?: "", // Provide default for String?
        director = director ?: emptyList(), // Provide default for List<String>?
        castMembers = castMembers ?: emptyList(), // Provide default for List<String>?
        genres = genres ?: emptyList(), // Provide default for List<String>?
        rating = rating ?: "", // Provide default for String?
        language = language ?: "", // Provide default for String?
        type = type ?: "", // Provide default for String?
        slug = slug
    ),
    media = media.map { it.asExternalModel() }
)
