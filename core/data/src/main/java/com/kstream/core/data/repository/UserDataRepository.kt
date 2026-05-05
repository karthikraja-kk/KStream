package com.kstream.core.data.repository

import com.kstream.core.data.local.KStreamDataStore
import com.kstream.core.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DefaultUserDataRepository @Inject constructor(
    private val dataStore: KStreamDataStore
) : UserDataRepository {
    override val username: Flow<String> = dataStore.username
    override val isFirstLaunchCompleted: Flow<Boolean> = dataStore.isFirstLaunchCompleted
    override val isWifiOnlyDownload: Flow<Boolean> = dataStore.isWifiOnlyDownload

    override suspend fun setUsername(name: String) = dataStore.setUsername(name)
    override suspend fun setFirstLaunchCompleted(completed: Boolean) = dataStore.setFirstLaunchCompleted(completed)
    override suspend fun setWifiOnlyDownload(wifiOnly: Boolean) = dataStore.setWifiOnlyDownload(wifiOnly)
}
