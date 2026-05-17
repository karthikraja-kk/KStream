package com.kstream.core.network.model

import com.kstream.core.model.Media
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia

fun NetworkMovie.asExternalModel(baseUrl: String = "") = Movie(
    id = id,
    movieName = movieName,
    year = year ?: 0,
    posterUrl = resolvePosterUrl(posterUrl ?: "", baseUrl),
    duration = duration ?: "",
    synopsis = synopsis ?: "",
    director = director ?: emptyList(),
    castMembers = castMembers ?: emptyList(),
    genres = genres ?: emptyList(),
    rating = rating ?: "",
    language = language ?: "",
    type = type ?: "",
    slug = slug,
    lastUpdated = lastUpdated ?: ""
)

fun NetworkMedia.asExternalModel() = Media(
    movieId = movieId,
    quality = quality ?: "",
    fileSize = fileSize ?: "",
    downloadUrl1 = downloadUrl1,
    downloadUrl2 = downloadUrl2,
    watchUrl1 = watchUrl1,
    watchUrl2 = watchUrl2
)

fun NetworkMovieWithMedia.asExternalModel(baseUrl: String = "") = MovieWithMedia(
    movie = Movie(
        id = id,
        movieName = movieName,
        year = year ?: 0,
        posterUrl = resolvePosterUrl(posterUrl ?: "", baseUrl),
        duration = duration ?: "",
        synopsis = synopsis ?: "",
        director = director ?: emptyList(),
        castMembers = castMembers ?: emptyList(),
        genres = genres ?: emptyList(),
        rating = rating ?: "",
        language = language ?: "",
        type = type ?: "",
        slug = slug,
        lastUpdated = lastUpdated ?: ""
    ),
    media = media.map { it.asExternalModel() }
)

/**
 * Constructs a full poster URL from a relative path and base URL.
 * If posterUrl is already a full URL (starts with http), returns as-is.
 * If posterUrl is a relative path (starts with /), prepends the base URL.
 */
private fun resolvePosterUrl(posterUrl: String, baseUrl: String): String {
    if (posterUrl.isBlank()) return posterUrl
    if (posterUrl.startsWith("http")) return posterUrl
    if (baseUrl.isBlank()) return posterUrl
    return "${baseUrl.trimEnd('/')}$posterUrl"
}
