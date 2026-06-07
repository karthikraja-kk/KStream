package com.kstream.tv.ui.home.presenter

/**
 * Sentinel item appended to the end of a Home rail's adapter to render a
 * "See more →" tile. Clicking it routes to SearchActivity with [query].
 */
data class SeeMoreCard(
    val query: String,
    val railTitle: String
)
