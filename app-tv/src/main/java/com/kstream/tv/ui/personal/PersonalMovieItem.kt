package com.kstream.tv.ui.personal

import com.kstream.core.model.Movie

data class PersonalMovieItem(
    val id: String,
    val title: String,
    val posterUrl: String,
    val badge: String,
    val movie: Movie? = null
)
