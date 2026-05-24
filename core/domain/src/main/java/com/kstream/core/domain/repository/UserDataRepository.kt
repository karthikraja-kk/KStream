package com.kstream.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    val username: Flow<String>
    val isFirstLaunchCompleted: Flow<Boolean>
    val isWifiOnlyDownload: Flow<Boolean>
    val downloadLocation: Flow<String>
    val downloadLocationUri: Flow<String>
    val recentSearches: Flow<List<String>>
    val isHdOnlyFilter: Flow<Boolean>
    val isCarouselEnabled: Flow<Boolean>

    suspend fun setUsername(name: String)
    suspend fun setDownloadLocation(location: String)
    suspend fun setDownloadLocationUri(uri: String)
    suspend fun setFirstLaunchCompleted(completed: Boolean)

    suspend fun setWifiOnlyDownload(wifiOnly: Boolean)
    suspend fun setHdOnlyFilter(hdOnly: Boolean)
    suspend fun setCarouselEnabled(enabled: Boolean)
    suspend fun addRecentSearch(query: String)
    suspend fun clearRecentSearches()
    suspend fun clearAllData()
}
