package com.kstream.tv.ui.home

import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.model.Movie
import kotlin.random.Random

/**
 * Builds a chat-bubble "fact" sentence for the home preview from TMDb
 * enrichment data. The intended UX:
 *
 *   ╭─╮  Hey Aravind, did you know Inception's
 *   │K│  tagline is "Your mind is the scene of the crime"?
 *   ╰─╯
 *
 * Selection: among all kinds whose data is present, **one is picked at
 * random** so the user doesn't always see the same line for the same
 * movie. The pick is stable per `(movieId + a per-render seed)` — the
 * caller passes `seed` so it can be regenerated only on a new focus event
 * (and stay stable across enrichment-driven re-renders to avoid flicker).
 *
 * Returns null when no fact can be built — caller should hide the bubble.
 */
object FactBubble {

    /** Greeting fallback when the user hasn't set a name (or set "Guest"). */
    private const val GUEST_NAME = "Guest"

    /** Personalized openers — picked deterministically per (movie, seed). */
    private val NAMED_OPENERS = listOf(
        "Hey {name}, did you know",
        "Did you know, {name}?",
        "Quick one, {name} —",
        "{name}, fun fact:"
    )

    /** Anonymous openers used when no name is available. */
    private val GUEST_OPENERS = listOf(
        "Did you know?",
        "Fun fact:",
        "Quick one —",
        "Heads up:"
    )

    data class Fact(
        val greeting: String,
        val body: CharSequence
    )

    /**
     * Selects a fact at random from whatever is available on [enrichment].
     * Returns null when none of the supported kinds yield data.
     */
    fun build(
        movie: Movie,
        enrichment: MovieEnrichment?,
        username: String?,
        seed: Long
    ): Fact? {
        if (enrichment == null) return null
        val candidates = collectCandidates(movie, enrichment)
        if (candidates.isEmpty()) return null

        val random = Random(seed)
        val body = candidates[random.nextInt(candidates.size)]
        val greeting = greetingFor(username, random)
        return Fact(greeting = greeting, body = body)
    }

    /** Builds every sentence the enrichment can support. */
    private fun collectCandidates(movie: Movie, e: MovieEnrichment): List<String> {
        val out = ArrayList<String>(4)

        e.tagline?.trim()?.takeIf { it.isNotBlank() }?.let { tagline ->
            // Strip surrounding quotes if TMDb already wrapped it.
            val clean = tagline.trim('"', '“', '”', ' ')
            out += "${movie.movieName}'s tagline is \"$clean\"."
        }

        e.collectionName?.trim()?.takeIf { it.isNotBlank() }?.let { collection ->
            // TMDb collection names usually end in "Collection" already; if
            // not, append it so it reads as a noun phrase.
            val phrase = if (collection.endsWith("Collection", ignoreCase = true)) {
                collection
            } else {
                "the $collection"
            }
            out += "${movie.movieName} is part of $phrase."
        }

        val budget = e.budget ?: 0L
        val revenue = e.revenue ?: 0L
        if (budget > 0L && revenue > 0L) {
            out += "${movie.movieName} earned ${formatMoney(revenue)} on a " +
                "${formatMoney(budget)} budget."
        }

        if (e.keywords.isNotEmpty()) {
            val pick = e.keywords.take(KEYWORDS_PICK)
            val joined = humanJoin(pick)
            out += "${movie.movieName} is all about $joined."
        }

        return out
    }

    private fun greetingFor(username: String?, random: Random): String {
        val raw = username?.trim().orEmpty()
        val isNamed = raw.isNotEmpty() && !raw.equals(GUEST_NAME, ignoreCase = true)
        return if (isNamed) {
            val name = if (raw.length > NAME_MAX) raw.substring(0, NAME_MAX) + "…" else raw
            NAMED_OPENERS[random.nextInt(NAMED_OPENERS.size)].replace("{name}", name)
        } else {
            GUEST_OPENERS[random.nextInt(GUEST_OPENERS.size)]
        }
    }

    /** Formats a USD amount as "$160M", "$2.4B", etc. */
    private fun formatMoney(amount: Long): String {
        if (amount <= 0L) return ""
        return when {
            amount >= 1_000_000_000L -> "$%.1fB".format(amount / 1_000_000_000.0)
            amount >= 1_000_000L -> "$${amount / 1_000_000L}M"
            amount >= 1_000L -> "$${amount / 1_000L}K"
            else -> "$$amount"
        }
    }

    /** Joins ["a", "b", "c"] → "a, b and c". */
    private fun humanJoin(items: List<String>): String {
        if (items.size <= 1) return items.joinToString()
        val head = items.dropLast(1).joinToString(", ")
        return "$head and ${items.last()}"
    }

    private const val NAME_MAX = 18
    private const val KEYWORDS_PICK = 3
}
