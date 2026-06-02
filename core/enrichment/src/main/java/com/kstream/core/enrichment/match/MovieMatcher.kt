package com.kstream.core.enrichment.match

import com.kstream.core.enrichment.tmdb.TmdbSearchHit

/**
 * Scores TMDb search hits against the (title, year) we already know.
 *
 * Scoring (max ~110):
 *  - exact title match: +50
 *  - title startsWith / contains: +35 / +20
 *  - year matches: +30
 *  - year ±1: +10
 *  - popularity bonus (0..20)
 *  - has poster: +5
 *
 * Confidence threshold for auto-cache: 70.
 */
internal object MovieMatcher {

    const val AUTO_CACHE_THRESHOLD = 70

    fun bestMatch(
        title: String,
        year: Int?,
        candidates: List<TmdbSearchHit>
    ): Pair<TmdbSearchHit, Int>? {
        if (candidates.isEmpty()) return null
        val normTitle = normalize(title)
        val scored = candidates.map { hit -> hit to score(normTitle, year, hit) }
        return scored.maxByOrNull { it.second }
    }

    fun score(normTitle: String, year: Int?, hit: TmdbSearchHit): Int {
        val candidateTitle = normalize(hit.title.ifBlank { hit.originalTitle })
        var s = 0
        s += when {
            candidateTitle == normTitle -> 50
            candidateTitle.startsWith(normTitle) || normTitle.startsWith(candidateTitle) -> 35
            candidateTitle.contains(normTitle) || normTitle.contains(candidateTitle) -> 20
            else -> 0
        }
        val candidateYear = parseYear(hit.releaseDate)
        if (year != null && year > 0 && candidateYear != null) {
            val diff = kotlin.math.abs(candidateYear - year)
            when {
                diff == 0 -> s += 30
                diff == 1 -> s += 10
            }
        }
        s += popularityBonus(hit.popularity)
        if (!hit.posterPath.isNullOrBlank()) s += 5
        return s
    }

    private fun popularityBonus(pop: Double): Int {
        if (pop <= 0.0) return 0
        val raw = (kotlin.math.log10(pop + 1.0) * 6.0).toInt()
        return raw.coerceIn(0, 20)
    }

    private fun parseYear(releaseDate: String?): Int? {
        if (releaseDate.isNullOrBlank()) return null
        val y = releaseDate.take(4).toIntOrNull() ?: return null
        return y.takeIf { it in 1900..2100 }
    }

    private fun normalize(raw: String): String {
        val lower = raw.lowercase().trim()
        val noPunct = lower.replace(PUNCT_REGEX, " ")
        return noPunct.replace(WHITESPACE_REGEX, " ").trim()
    }

    private val PUNCT_REGEX = Regex("[\\p{Punct}]+")
    private val WHITESPACE_REGEX = Regex("\\s+")
}
