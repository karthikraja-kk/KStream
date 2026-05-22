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
    override val downloadLocation: Flow<String> = dataStore.downloadLocation
    override val downloadLocationUri: Flow<String> = dataStore.downloadLocationUri
    override val recentSearches: Flow<List<String>> = dataStore.recentSearches

    override suspend fun setUsername(name: String) = dataStore.setUsername(name)
    override suspend fun setDownloadLocation(location: String) = dataStore.setDownloadLocation(location)
    override suspend fun setDownloadLocationUri(uri: String) = dataStore.setDownloadLocationUri(uri)
    override suspend fun setFirstLaunchCompleted(completed: Boolean) = dataStore.setFirstLaunchCompleted(completed)
    override suspend fun setWifiOnlyDownload(wifiOnly: Boolean) = dataStore.setWifiOnlyDownload(wifiOnly)
    override suspend fun addRecentSearch(query: String) = dataStore.addRecentSearch(query)
    override suspend fun clearRecentSearches() = dataStore.clearRecentSearches()
    override suspend fun clearAllData() = dataStore.clearAll()
}
