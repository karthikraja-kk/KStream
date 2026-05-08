package com.kstream.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kstream.core.model.DownloadMetadata
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class DownloadViewModel @Inject constructor(

    private val kstreamDownloadManager: com.kstream.feature.downloads.KStreamDownloadManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _downloadSpeeds = MutableStateFlow<Map<String, Long>>(emptyMap()) // ID to bytes/sec
    private val _lastBytes = mutableMapOf<String, Long>()
    private val _lastTime = mutableMapOf<String, Long>()

    private fun getAllDownloads(): List<Download> {
        return kstreamDownloadManager.downloadManager.downloadIndex.getDownloads().use { cursor ->
            val list = mutableListOf<Download>()
            while (cursor.moveToNext()) {
                list.add(cursor.download)
            }
            list.sortedByDescending { it.startTimeMs }
        }
    }

    val downloads: Flow<List<Download>> = combine(
        callbackFlow {
            val listener = object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    trySend(getAllDownloads())
                }

                override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                    trySend(getAllDownloads())
                }

                override fun onIdle(downloadManager: DownloadManager) {
                    trySend(getAllDownloads())
                }
            }
            
            kstreamDownloadManager.downloadManager.addListener(listener)
            
            // Use a ticker to update progress and calculate speeds
            val tickerJob = launch {
                while (true) {
                    val currentDownloads = kstreamDownloadManager.downloadManager.currentDownloads
                    calculateSpeeds(currentDownloads)
                    trySend(getAllDownloads())
                    delay(1000)
                }
            }
            
            trySend(getAllDownloads())
            
            awaitClose {
                kstreamDownloadManager.downloadManager.removeListener(listener)
                tickerJob.cancel()
            }
        },
        _searchQuery
    ) { allDownloads, query ->
        if (query.isBlank()) {
            allDownloads
        } else {
            allDownloads.filter { d ->
                try {
                    val metadata = Json.decodeFromString<DownloadMetadata>(d.request.data.decodeToString())
                    metadata.movieName.contains(query, ignoreCase = true)
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    private fun calculateSpeeds(currentDownloads: List<Download>) {
        val now = System.currentTimeMillis()
        val newSpeeds = mutableMapOf<String, Long>()
        
        currentDownloads.forEach { d ->
            val id = d.request.id
            val lastByte = _lastBytes[id] ?: d.bytesDownloaded
            val lastT = _lastTime[id] ?: d.startTimeMs
            
            val diffBytes = d.bytesDownloaded - lastByte
            val diffTime = now - lastT
            
            if (diffTime > 0) {
                val speed = (diffBytes * 1000) / diffTime
                newSpeeds[id] = speed
            }
            
            _lastBytes[id] = d.bytesDownloaded
            _lastTime[id] = now
        }
        _downloadSpeeds.value = newSpeeds
    }

    fun getRemainingTime(id: String, bytesRemaining: Long): Long {
        val speed = _downloadSpeeds.value[id] ?: return -1L
        if (speed <= 0) return -1L
        return bytesRemaining / speed
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun pauseDownload(id: String) {
        kstreamDownloadManager.downloadManager.setStopReason(id, 1)
    }

    fun resumeDownload(id: String) {
        kstreamDownloadManager.downloadManager.setStopReason(id, Download.STOP_REASON_NONE)
    }

    fun removeDownload(id: String) {
        kstreamDownloadManager.downloadManager.removeDownload(id)
        _lastBytes.remove(id)
        _lastTime.remove(id)
    }

    fun getDownloadDir(): String {
        return kstreamDownloadManager.getDownloadDirectory()
    }
}

