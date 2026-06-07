package com.kstream.core.enrichment.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent enrichment row. Keyed by `movieKey` = "title|year" (lowercased,
 * trimmed) so we can resolve enrichment for a Movie without touching the
 * frozen Movie schema.
 *
 * `backdropsCsv`, `castJson`, `reviewsJson` are kept as TEXT blobs because
 * enrichment is always read whole for one movie at a time.
 */
@Entity(tableName = "movie_enrichment")
data class MovieEnrichmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "movie_key") val movieKey: String,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int,
    @ColumnInfo(name = "confidence") val confidence: Int,
    @ColumnInfo(name = "tagline") val tagline: String?,
    @ColumnInfo(name = "overview") val overview: String?,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    @ColumnInfo(name = "poster_url") val posterUrl: String?,
    @ColumnInfo(name = "tmdb_rating") val tmdbRating: Double?,
    @ColumnInfo(name = "certification") val certification: String?,
    @ColumnInfo(name = "backdrops") val backdropsCsv: String?,
    @ColumnInfo(name = "cast_json") val castJson: String?,
    @ColumnInfo(name = "reviews_json") val reviewsJson: String?,
    @ColumnInfo(name = "collection_name") val collectionName: String? = null,
    @ColumnInfo(name = "budget") val budget: Long? = null,
    @ColumnInfo(name = "revenue") val revenue: Long? = null,
    @ColumnInfo(name = "keywords") val keywordsCsv: String? = null,
    @ColumnInfo(name = "fetched_at_ms") val fetchedAtEpochMs: Long
)
