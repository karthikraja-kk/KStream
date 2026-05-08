package com.kstream.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class DownloadViewModel @Inject constructor(

    private val kstreamDownloadManager: com.kstream.feature.downloads.KStreamDownloadManager
) : ViewModel() {

    val downloads: Flow<List<Download>> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                trySend(downloadManager.currentDownloads.sortedByDescending { it.startTimeMs })
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                trySend(downloadManager.currentDownloads.sortedByDescending { it.startTimeMs })
            }

            override fun onIdle(downloadManager: DownloadManager) {
                trySend(downloadManager.currentDownloads.sortedByDescending { it.startTimeMs })
            }
        }
        
        kstreamDownloadManager.downloadManager.addListener(listener)
        trySend(kstreamDownloadManager.downloadManager.currentDownloads.sortedByDescending { it.startTimeMs })
        
        awaitClose {
            kstreamDownloadManager.downloadManager.removeListener(listener)
        }
    }

    fun pauseDownload(id: String) {
        kstreamDownloadManager.downloadManager.addDownload(
            kstreamDownloadManager.downloadManager.downloadIndex.getDownload(id)!!.request,
            Download.STOP_REASON_NONE // This is actually for starting, pausing is setting stop reason
        )
        // Correct way to pause in Media3:
        kstreamDownloadManager.downloadManager.setStopReason(id, 1) // Any non-zero reason pauses
    }

    fun resumeDownload(id: String) {
        kstreamDownloadManager.downloadManager.setStopReason(id, Download.STOP_REASON_NONE)
    }

    fun removeDownload(id: String) {
        kstreamDownloadManager.downloadManager.removeDownload(id)
    }

    fun getDownloadDir(): String {
        return kstreamDownloadManager.getDownloadDirectory()
    }
}
