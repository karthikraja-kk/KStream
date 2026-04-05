package com.kstream.app.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kstream.core.domain.models.MovieItem
import com.kstream.core.domain.usecases.GetMoviesByYearUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class YearViewModel @Inject constructor(
    private val getMoviesByYearUseCase: GetMoviesByYearUseCase
) : ViewModel() {

    fun getMovies(year: String): Flow<PagingData<MovieItem>> {
        return getMoviesByYearUseCase(year).cachedIn(viewModelScope)
    }
}
