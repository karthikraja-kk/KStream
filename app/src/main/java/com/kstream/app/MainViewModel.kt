package com.kstream.app

import androidx.lifecycle.ViewModel
import com.kstream.core.data.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    userDataRepository: UserDataRepository
) : ViewModel() {
    val isFirstLaunchCompleted: Flow<Boolean> = userDataRepository.isFirstLaunchCompleted
}
