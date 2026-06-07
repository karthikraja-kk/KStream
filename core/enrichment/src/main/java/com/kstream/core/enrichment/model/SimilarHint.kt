package com.kstream.core.enrichment.model

/**
 * Lightweight hint about a TMDb "similar movie" — exposed publicly by
 * [com.kstream.core.enrichment.EnrichmentRepository.fetchSimilarHints] so
 * callers can intersect with their own local catalog.
 *
 *  - [title] is TMDb's localized title.
 *  - [year] is 0 when TMDb has no release date.
 */
data class SimilarHint(
    val tmdbId: Int,
    val title: String,
    val year: Int
)
