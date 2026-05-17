package com.kstream.feature.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository
) : ViewModel() {

    private val _username = MutableStateFlow("Guest")
    val username: StateFlow<String> = _username.asStateFlow()

    fun onUsernameChange(newName: String) {
        _username.value = newName
    }

    fun onContinueClick(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                userDataRepository.setUsername(_username.value)
                userDataRepository.setFirstLaunchCompleted(true)
            } catch (_: Exception) { }
            onComplete()
        }
    }
}
