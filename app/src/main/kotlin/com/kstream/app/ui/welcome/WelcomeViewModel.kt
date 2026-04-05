package com.kstream.app.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.models.AppSettings
import com.kstream.core.domain.usecases.UpdateAppSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val updateAppSettingsUseCase: UpdateAppSettingsUseCase
) : ViewModel() {

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            updateAppSettingsUseCase(settings)
        }
    }
}
