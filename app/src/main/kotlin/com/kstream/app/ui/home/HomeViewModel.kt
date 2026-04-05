package com.kstream.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.kstream.core.domain.usecases.GetAppSettingsUseCase
import com.kstream.core.domain.usecases.GetYearsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    getAppSettingsUseCase: GetAppSettingsUseCase,
    getYearsUseCase: GetYearsUseCase
) : ViewModel() {

    val settings = getAppSettingsUseCase().asLiveData()
    val years = getYearsUseCase().asLiveData()
}
