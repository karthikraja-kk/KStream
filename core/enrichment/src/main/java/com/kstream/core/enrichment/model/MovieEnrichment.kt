package com.kstream.core.enrichment.model

import kotlinx.serialization.Serializable

/**
 * Domain-level enriched view of a [com.kstream.core.model.Movie] augmented
 * with TMDb data. All fields are optional — the rest of the app degrades
 * gracefully when enrichment is absent or partial.
 */
data class MovieEnrichment(
    val tmdbId: Int,
    val confidence: Int,
    val tagline: String?,
    val overview: String?,
    val logoUrl: String?,
    val posterUrl: String?,
    val tmdbRating: Double?,
    val certification: String?,
    val backdrops: List<String>,
    val cast: List<EnrichedCast>,
    val reviews: List<EnrichedReview>,
    val collectionName: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val keywords: List<String> = emptyList(),
    val fetchedAtEpochMs: Long
)

@Serializable
data class EnrichedCast(
    val name: String,
    val character: String? = null,
    val photoUrl: String? = null,
    val order: Int = 0
)

@Serializable
data class EnrichedReview(
    val author: String,
    val content: String,
    val rating: Double? = null,
    val createdAt: String? = null
)
