package com.kstream.core.domain

import javax.inject.Inject

data class DownloadRequestSpec(
    val movieId: String,
    val url: String,
    val title: String
)

class DownloadMovieUseCase @Inject constructor() {
    operator fun invoke(movieId: String, url: String, title: String): DownloadRequestSpec {
        return DownloadRequestSpec(
            movieId = movieId,
            url = url,
            title = title
        )
    }
}
