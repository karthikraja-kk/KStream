package com.kstream.core.model

data class SearchResult(
    val movies: List<Movie>,
    val suggestedQuery: String? = null,
    val isFuzzyMatch: Boolean = false
)
