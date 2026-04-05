package com.kstream.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.kstream.core.domain.usecases.GetMovieDetailUseCase
import com.kstream.core.domain.usecases.GetPosterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val getMovieDetailUseCase: GetMovieDetailUseCase,
    private val getPosterUseCase: GetPosterUseCase
) : ViewModel() {

    fun getMovieDetail(path: String) = getMovieDetailUseCase(path).asLiveData()
    
    fun getPoster(path: String) = getPosterUseCase(path).asLiveData()
}
