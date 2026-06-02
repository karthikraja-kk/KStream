package com.kstream.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.domain.StartupSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    startupSyncManager: StartupSyncManager // triggers splash-time sync on creation
) : ViewModel() {
    val isFirstLaunchCompleted: Flow<Boolean> = userDataRepository.isFirstLaunchCompleted
    val isLiteMode: Flow<Boolean> = userDataRepository.isLiteMode

    fun setLiteMode(enabled: Boolean) {
        viewModelScope.launch {
            userDataRepository.setLiteMode(enabled)
        }
    }
}
