package com.kstream.core.enrichment.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TmdbSearchResponse(
    val page: Int = 0,
    val results: List<TmdbSearchHit> = emptyList(),
    @SerialName("total_results") val totalResults: Int = 0
)

@Serializable
internal data class TmdbSearchHit(
    val id: Int,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    val popularity: Double = 0.0,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0
)

@Serializable
internal data class TmdbMovieDetail(
    val id: Int,
    val title: String = "",
    val tagline: String? = null,
    val overview: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val budget: Long = 0L,
    val revenue: Long = 0L,
    @SerialName("belongs_to_collection") val belongsToCollection: TmdbCollection? = null,
    val keywords: TmdbKeywordsPage? = null,
    val credits: TmdbCredits? = null,
    val images: TmdbImages? = null,
    val reviews: TmdbReviewsPage? = null,
    @SerialName("release_dates") val releaseDates: TmdbReleaseDatesPage? = null
)

@Serializable
internal data class TmdbCollection(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
internal data class TmdbKeywordsPage(
    val keywords: List<TmdbKeyword> = emptyList()
)

@Serializable
internal data class TmdbKeyword(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
internal data class TmdbCredits(
    val cast: List<TmdbCastMember> = emptyList()
)

@Serializable
internal data class TmdbCastMember(
    val id: Int = 0,
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = 0
)

@Serializable
internal data class TmdbImages(
    val backdrops: List<TmdbImage> = emptyList(),
    val logos: List<TmdbImage> = emptyList()
)

@Serializable
internal data class TmdbImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
internal data class TmdbReviewsPage(
    val results: List<TmdbReview> = emptyList()
)

@Serializable
internal data class TmdbReview(
    val id: String = "",
    val author: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("author_details") val authorDetails: TmdbReviewAuthorDetails? = null
)

@Serializable
internal data class TmdbReviewAuthorDetails(
    val rating: Double? = null
)

@Serializable
internal data class TmdbReleaseDatesPage(
    val results: List<TmdbReleaseDatesByCountry> = emptyList()
)

@Serializable
internal data class TmdbReleaseDatesByCountry(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val releaseDates: List<TmdbReleaseDate> = emptyList()
)

@Serializable
internal data class TmdbReleaseDate(
    val certification: String? = null,
    val type: Int = 0,
    @SerialName("release_date") val releaseDate: String? = null
)
