package com.kstream.core.common

/**
 * Single source of truth for the "Show HD only" filter.
 *
 * When the user enables Show HD only in Settings, only movies whose [com.kstream.core.model.Movie.type]
 * matches one of these whitelisted values (case-insensitive) are shown across discovery surfaces
 * (Home rows, Search, Browse, Continue Watching).
 *
 * Personal surfaces (Liked movies, Watch history, Details screen) deliberately ignore this filter
 * so users never lose access to titles they explicitly saved or already engaged with.
 */
object HdQualityFilter {

    /** Allowed `Movie.type` values when the filter is ON. */
    val ALLOWED_TYPES: Set<String> = setOf(
        "Original HD",
        "Original HD (V1)",
        "Bluray HD"
    )

    private val allowedLowercase: Set<String> = ALLOWED_TYPES.mapTo(HashSet()) { it.lowercase() }

    /** True if the given type passes the filter. Case-insensitive. Null/blank = does NOT pass. */
    fun isHd(type: String?): Boolean {
        if (type.isNullOrBlank()) return false
        return type.trim().lowercase() in allowedLowercase
    }
}
