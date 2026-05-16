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

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        userDataRepository.username
            .onEach { username -> _uiState.update { it.copy(username = username) } }
            .launchIn(viewModelScope)
    }

    fun onUsernameChange(newName: String) {
        _uiState.update { it.copy(username = newName) }
    }

    fun saveUsername() {
        viewModelScope.launch {
            userDataRepository.setUsername(_uiState.value.username)
        }
    }
}
