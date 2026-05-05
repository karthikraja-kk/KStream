package com.kstream.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val username: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = userDataRepository.username
        .map { SettingsUiState(username = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun onUsernameChange(newName: String) {
        viewModelScope.launch {
            userDataRepository.setUsername(newName)
        }
    }
}
