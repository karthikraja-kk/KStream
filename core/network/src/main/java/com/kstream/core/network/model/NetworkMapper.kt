package com.kstream.core.network.model

import com.kstream.core.model.Media
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia

fun NetworkMovie.asExternalModel() = Movie(
    id = id,
    movieName = movieName,
    year = year,
    posterUrl = posterUrl,
    duration = duration,
    synopsis = synopsis,
    director = director,
    castMembers = castMembers,
    genres = genres,
    rating = rating,
    language = language,
    type = type,
    slug = slug
)

fun NetworkMedia.asExternalModel() = Media(
    movieId = movieId,
    quality = quality,
    fileSize = fileSize,
    downloadUrl1 = downloadUrl1,
    downloadUrl2 = downloadUrl2,
    watchUrl1 = watchUrl1,
    watchUrl2 = watchUrl2
)

fun NetworkMovieWithMedia.asExternalModel() = MovieWithMedia(
    movie = Movie(
        id = id,
        movieName = movieName,
        year = year,
        posterUrl = posterUrl,
        duration = duration,
        synopsis = synopsis,
        director = director,
        castMembers = castMembers,
        genres = genres,
        rating = rating,
        language = language,
        type = type,
        slug = slug
    ),
    media = media.map { it.asExternalModel() }
)
