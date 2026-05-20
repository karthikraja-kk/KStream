package com.kstream.core.common

import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy string matching utilities using Levenshtein distance.
 * Used to find movies even when the user query has typos.
 */
object FuzzySearch {

    /**
     * Computes the Levenshtein (edit) distance between two strings.
     * Returns the minimum number of single-character edits (insertions,
     * deletions, substitutions) required to change [a] into [b].
     */
    fun levenshteinDistance(a: String, b: String): Int {
        val la = a.length
        val lb = b.length
        if (la == 0) return lb
        if (lb == 0) return la

        // Use two rows instead of full matrix — O(min(m,n)) space
        var prev = IntArray(lb + 1) { it }
        var curr = IntArray(lb + 1)

        for (i in 1..la) {
            curr[0] = i
            for (j in 1..lb) {
                val cost = if (a[i - 1].lowercaseChar() == b[j - 1].lowercaseChar()) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,      // deletion
                    curr[j - 1] + 1,  // insertion
                    prev[j - 1] + cost // substitution
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[lb]
    }

    /**
     * Returns a similarity score between 0.0 and 1.0 for two strings.
     * 1.0 = identical, 0.0 = completely different.
     */
    fun similarity(a: String, b: String): Double {
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshteinDistance(a, b).toDouble() / maxLen
    }

    /**
     * Finds the best fuzzy match of [query] within [text] by sliding a
     * window of query-length across the text. Returns the highest
     * similarity score found.
     *
     * Handles partial matching, e.g. query "avngers" against
     * "Avengers: Endgame" matches the "Avengers" substring.
     */
    fun bestSubstringSimilarity(query: String, text: String): Double {
        val q = query.lowercase().trim()
        val t = text.lowercase().trim()

        if (q.isEmpty() || t.isEmpty()) return 0.0
        if (q.length > t.length) return similarity(q, t)

        // Try whole-string similarity first
        var best = similarity(q, t)

        // Slide a window across the text, checking substrings
        val windowSize = q.length
        for (i in 0..(t.length - windowSize)) {
            val end = min(i + windowSize + (windowSize / 3), t.length)
            val substring = t.substring(i, end)
            val score = similarity(q, substring)
            if (score > best) best = score
        }

        return best
    }

    /**
     * Scores a multi-word query against a movie title.
     * Splits the query into words and finds the best match for each
     * word in the title, then averages the scores.
     *
     * Handles queries like "avenges endgam" → "Avengers: Endgame".
     */
    fun scoreQuery(query: String, movieName: String): Double {
        val queryWords = query.lowercase().trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val title = movieName.lowercase().trim()

        if (queryWords.isEmpty() || title.isEmpty()) return 0.0

        // Single-word query: use substring similarity
        if (queryWords.size == 1) {
            return bestSubstringSimilarity(queryWords[0], title)
        }

        // Multi-word query: score each word, average the results
        val wordScores = queryWords.map { word -> bestSubstringSimilarity(word, title) }
        return wordScores.average()
    }
}
