package com.kstream.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * A previously-resolved direct playable URL (Cloudflare R2 presigned) for a
 * movie+quality. `download.php` gateway tokens die in ~3 minutes, but the R2
 * URL they redirect to is valid for ~48h — caching it gives us a
 * "single refresh = long-lived link" behaviour.
 */
data class ResolvedMediaUrl(
    val url: String,
    val expiresAt: Long
)

interface UserDataRepository {
    val username: Flow<String>
    val isFirstLaunchCompleted: Flow<Boolean>
    val isWifiOnlyDownload: Flow<Boolean>
    val downloadLocation: Flow<String>
    val recentSearches: Flow<List<String>>
    val isHdOnlyFilter: Flow<Boolean>
    val isCarouselEnabled: Flow<Boolean>
    val isLiteMode: Flow<Boolean>
    val videoEngine: Flow<String>

    suspend fun setUsername(name: String)
    suspend fun setDownloadLocation(location: String)
    suspend fun setFirstLaunchCompleted(completed: Boolean)

    suspend fun setWifiOnlyDownload(wifiOnly: Boolean)
    suspend fun setHdOnlyFilter(hdOnly: Boolean)
    suspend fun setCarouselEnabled(enabled: Boolean)
    suspend fun setLiteMode(enabled: Boolean)
    suspend fun setVideoEngine(engine: String)
    suspend fun addRecentSearch(query: String)
    suspend fun clearRecentSearches()
    suspend fun getResolvedMediaUrl(movieId: String, quality: String): ResolvedMediaUrl?
    suspend fun setResolvedMediaUrl(movieId: String, quality: String, url: String, expiresAt: Long)
    suspend fun clearAllData()
}
