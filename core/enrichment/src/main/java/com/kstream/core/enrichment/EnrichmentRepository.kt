package com.kstream.core.enrichment

import com.kstream.core.enrichment.db.MovieEnrichmentDao
import com.kstream.core.enrichment.db.MovieEnrichmentEntity
import com.kstream.core.enrichment.match.MovieMatcher
import com.kstream.core.enrichment.model.EnrichedCast
import com.kstream.core.enrichment.model.EnrichedReview
import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.enrichment.model.SimilarHint
import com.kstream.core.enrichment.tmdb.TmdbClient
import com.kstream.core.enrichment.tmdb.TmdbMovieDetail
import com.kstream.core.model.Movie
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Public-facing enrichment API.
 *
 * Lookup contract:
 *  1. [observe] returns a cold Flow of cached enrichment (null if absent).
 *  2. [ensureCached] is fire-and-forget; if no row exists for the Movie, it
 *     searches TMDb, picks the best match, fetches the detail in ONE HTTP call
 *     (thanks to `append_to_response`), and persists if confidence ≥ 70.
 *  3. The cache is forever — TMDb metadata for a finished movie rarely changes
 *     in a way the user would care about; [refresh] re-runs unconditionally.
 *
 *  IMAGE PATHS in [MovieEnrichment] are FULL URLs (TMDB_IMAGE_BASE_URL + size +
 *  path); the UI doesn't need to know the base URL.
 */
@Singleton
class EnrichmentRepository @Inject internal constructor(
    private val dao: MovieEnrichmentDao,
    private val client: TmdbClient
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun observe(movie: Movie): Flow<MovieEnrichment?> {
        val key = keyFor(movie)
        return dao.observe(key).map { it?.toDomain(json) }
    }

    suspend fun get(movie: Movie): MovieEnrichment? {
        val key = keyFor(movie)
        return dao.get(key)?.toDomain(json)
    }

    /**
     * Batched cache lookup. Returns a map keyed by [Movie.id] so callers can
     * seed an in-memory map by movie id without having to recompute the
     * canonical enrichment key.
     */
    suspend fun getMany(movies: List<Movie>): Map<String, MovieEnrichment> {
        if (movies.isEmpty()) return emptyMap()
        val keyToMovieId = HashMap<String, String>(movies.size)
        movies.forEach { keyToMovieId[keyFor(it)] = it.id }
        val rows = dao.getAll(keyToMovieId.keys.toList())
        val out = HashMap<String, MovieEnrichment>(rows.size)
        rows.forEach { row ->
            val movieId = keyToMovieId[row.movieKey] ?: return@forEach
            out[movieId] = row.toDomain(json)
        }
        return out
    }

    /** Idempotent: does nothing if a cached row exists. */
    suspend fun ensureCached(movie: Movie): MovieEnrichment? {
        val key = keyFor(movie)
        dao.get(key)?.let { return it.toDomain(json) }
        return refresh(movie)
    }

    /** Force a fresh TMDb lookup + cache write. Returns null if no high-confidence match. */
    suspend fun refresh(movie: Movie): MovieEnrichment? {
        if (movie.movieName.isBlank()) return null
        val key = keyFor(movie)
        val search = client.searchMovie(movie.movieName, movie.year.takeIf { it > 0 }) ?: return null
        val best = MovieMatcher.bestMatch(movie.movieName, movie.year, search.results) ?: return null
        val (hit, confidence) = best
        if (confidence < MovieMatcher.AUTO_CACHE_THRESHOLD) return null
        val detail = client.fetchDetail(hit.id) ?: return null
        val posterUrl = hit.posterPath?.let { "${IMAGE_BASE}w342$it" }
        val entity = toEntity(key, confidence, detail, posterUrl)
        dao.upsert(entity)
        return entity.toDomain(json)
    }

    fun keyFor(movie: Movie): String {
        val t = movie.movieName.lowercase().trim()
        val y = movie.year.takeIf { it > 0 }?.toString() ?: ""
        return "$t|$y"
    }

    /**
     * Fetches the TMDb /similar list for a given TMDb id. Returns lightweight
     * hints (title + year) so callers can intersect with their own catalog.
     * Returns an empty list on any failure or if TMDb is not configured.
     */
    suspend fun fetchSimilarHints(tmdbId: Int, limit: Int = 20): List<SimilarHint> {
        val resp = client.fetchSimilar(tmdbId) ?: return emptyList()
        return resp.results
            .asSequence()
            .filter { it.title.isNotBlank() }
            .take(limit)
            .map { hit ->
                val y = hit.releaseDate?.take(4)?.toIntOrNull() ?: 0
                SimilarHint(tmdbId = hit.id, title = hit.title, year = y)
            }
            .toList()
    }

    private fun toEntity(key: String, confidence: Int, d: TmdbMovieDetail, posterUrl: String?): MovieEnrichmentEntity {
        val backdrops = buildBackdropUrls(d)
        val cast = buildCast(d)
        val logo = pickLogoUrl(d)
        val certification = pickUsCertification(d)
        val keywordsCsv = d.keywords?.keywords
            ?.asSequence()
            ?.mapNotNull { it.name.takeIf { n -> n.isNotBlank() } }
            ?.take(KEYWORDS_LIMIT)
            ?.joinToString("|")
            ?.takeIf { it.isNotBlank() }
        return MovieEnrichmentEntity(
            movieKey = key,
            tmdbId = d.id,
            confidence = confidence,
            tagline = d.tagline?.takeIf { it.isNotBlank() },
            overview = d.overview?.takeIf { it.isNotBlank() },
            logoUrl = logo,
            posterUrl = posterUrl,
            tmdbRating = d.voteAverage.takeIf { it > 0.0 },
            certification = certification,
            backdropsCsv = backdrops.joinToString("|").takeIf { it.isNotBlank() },
            castJson = if (cast.isEmpty()) null else json.encodeToString(
                ListSerializer(EnrichedCast.serializer()),
                cast
            ),
            reviewsJson = buildReviewsJson(d),
            collectionName = d.belongsToCollection?.name?.takeIf { it.isNotBlank() },
            budget = d.budget.takeIf { it > 0L },
            revenue = d.revenue.takeIf { it > 0L },
            keywordsCsv = keywordsCsv,
            fetchedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun buildBackdropUrls(d: TmdbMovieDetail): List<String> {
        val sized = "${IMAGE_BASE}original"
        val list = d.images?.backdrops?.mapNotNull { it.filePath?.let { p -> sized + p } }.orEmpty()
        if (list.isNotEmpty()) return list.take(10)
        return d.backdropPath?.let { listOf(sized + it) }.orEmpty()
    }

    private fun pickLogoUrl(d: TmdbMovieDetail): String? {
        val logos = d.images?.logos.orEmpty()
        if (logos.isEmpty()) return null
        val sized = "${IMAGE_BASE}w500"
        val best = logos
            .sortedWith(
                compareByDescending<com.kstream.core.enrichment.tmdb.TmdbImage> { it.voteAverage }
                    .thenByDescending { it.voteCount }
            )
            .firstOrNull()
        return best?.filePath?.let { sized + it }
    }

    private fun pickUsCertification(d: TmdbMovieDetail): String? {
        val groups = d.releaseDates?.results.orEmpty()
        val us = groups.firstOrNull { it.country.equals("US", ignoreCase = true) }
            ?: groups.firstOrNull()
        return us?.releaseDates
            ?.firstOrNull { !it.certification.isNullOrBlank() }
            ?.certification
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildCast(d: TmdbMovieDetail): List<EnrichedCast> {
        val raw = d.credits?.cast.orEmpty()
        val sized = "${IMAGE_BASE}w185"
        return raw
            .sortedBy { it.order }
            .take(CAST_LIMIT)
            .map {
                EnrichedCast(
                    name = it.name,
                    character = it.character?.takeIf { ch -> ch.isNotBlank() },
                    photoUrl = it.profilePath?.let { p -> sized + p },
                    order = it.order
                )
            }
    }

    private fun buildReviewsJson(d: TmdbMovieDetail): String? {
        val reviews = d.reviews?.results.orEmpty()
            .sortedByDescending { it.content.length }
            .take(REVIEW_LIMIT)
            .map {
                EnrichedReview(
                    author = it.author,
                    content = it.content,
                    rating = it.authorDetails?.rating,
                    createdAt = it.createdAt
                )
            }
        if (reviews.isEmpty()) return null
        return json.encodeToString(ListSerializer(EnrichedReview.serializer()), reviews)
    }

    companion object {
        private const val IMAGE_BASE = BuildConfig.TMDB_IMAGE_BASE_URL
        private const val CAST_LIMIT = 12
        private const val REVIEW_LIMIT = 5
        private const val KEYWORDS_LIMIT = 6
    }
}

private fun MovieEnrichmentEntity.toDomain(json: Json): MovieEnrichment {
    val backdrops = backdropsCsv?.split('|')?.filter { it.isNotBlank() }.orEmpty()
    val cast = castJson?.let {
        runCatching {
            json.decodeFromString(ListSerializer(EnrichedCast.serializer()), it)
        }.getOrNull()
    }.orEmpty()
    val reviews = reviewsJson?.let {
        runCatching {
            json.decodeFromString(ListSerializer(EnrichedReview.serializer()), it)
        }.getOrNull()
    }.orEmpty()
    return MovieEnrichment(
        tmdbId = tmdbId,
        confidence = confidence,
        tagline = tagline,
        overview = overview,
        logoUrl = logoUrl,
        posterUrl = posterUrl,
        tmdbRating = tmdbRating,
        certification = certification,
        backdrops = backdrops,
        cast = cast,
        reviews = reviews,
        collectionName = collectionName,
        budget = budget,
        revenue = revenue,
        keywords = keywordsCsv?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
        fetchedAtEpochMs = fetchedAtEpochMs
    )
}
