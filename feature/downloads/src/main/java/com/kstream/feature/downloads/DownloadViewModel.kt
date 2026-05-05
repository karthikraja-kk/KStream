package com.kstream.feature.downloads

import androidx.annotation.OptIn
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

@HiltViewModel
@OptIn(UnstableApi::class)
class DownloadViewModel @Inject constructor(
    private val kstreamDownloadManager: KStreamDownloadManager
) : ViewModel() {

    val downloads: Flow<List<Download>> = callbackFlow {
        val listener = object : DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                trySend(downloadManager.currentDownloads)
            }

            override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
                trySend(downloadManager.currentDownloads)
            }
        }
        
        kstreamDownloadManager.downloadManager.addListener(listener)
        trySend(kstreamDownloadManager.downloadManager.currentDownloads)
        
        awaitClose {
            kstreamDownloadManager.downloadManager.removeListener(listener)
        }
    }
}
