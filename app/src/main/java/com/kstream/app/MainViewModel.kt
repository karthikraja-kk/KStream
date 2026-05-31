package com.kstream.app

import androidx.lifecycle.ViewModel
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.domain.StartupSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    userDataRepository: UserDataRepository,
    startupSyncManager: StartupSyncManager // triggers splash-time sync on creation
) : ViewModel() {
    val isFirstLaunchCompleted: Flow<Boolean> = userDataRepository.isFirstLaunchCompleted
}
