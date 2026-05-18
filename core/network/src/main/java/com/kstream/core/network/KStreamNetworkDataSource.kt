package com.kstream.core.network

import com.kstream.core.network.model.NetworkMedia
import com.kstream.core.network.model.NetworkMovie
import com.kstream.core.network.model.NetworkMovieWithMedia

interface KStreamNetworkDataSource {
    suspend fun getMovies(): List<NetworkMovie>
    suspend fun getMovieWithMedia(movieId: String): NetworkMovieWithMedia?
    suspend fun searchMovies(query: String): List<NetworkMovie>
    suspend fun getBaseUrl(): String
    suspend fun refreshMovieMedia(movieId: String): List<NetworkMedia>
    suspend fun triggerMovieScan(): ScanTriggerResponse
    suspend fun getScanStatus(): ScanStatusEntry?
    suspend fun getLatestCompletedScanStatus(): ScanStatusEntry?
}

@kotlinx.serialization.Serializable
data class ScanTriggerResponse(
    val status: String,
    @kotlinx.serialization.SerialName("triggered_at") val triggeredAt: String? = null,
    val message: String? = null,
    @kotlinx.serialization.SerialName("last_run") val lastRun: String? = null,
    val error: String? = null
)

@kotlinx.serialization.Serializable
data class ScanStatusEntry(
    val id: String,
    @kotlinx.serialization.SerialName("refresh_time") val refreshTime: String,
    val status: String,
    @kotlinx.serialization.SerialName("trigger_by") val triggerBy: String? = null
)
