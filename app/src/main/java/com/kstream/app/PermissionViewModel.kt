package com.kstream.app

import androidx.lifecycle.ViewModel
import com.kstream.core.domain.repository.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    val userDataRepository: UserDataRepository
) : ViewModel()