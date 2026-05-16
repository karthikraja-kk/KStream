package com.kstream.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.model.Download
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val customDownloadManager: CustomDownloadManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val downloads: Flow<List<Download>> = combine(
        downloadRepository.getDownloads(),
        _searchQuery
    ) { allDownloads, query ->
        if (query.isBlank()) {
            allDownloads
        } else {
            allDownloads.filter { it.title.contains(query, ignoreCase = true) }
        }.sortedByDescending { it.progress }
    }

    suspend fun checkFileExists(filePath: String): Boolean {
        return customDownloadManager.checkFileExists(filePath)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun removeDownload(id: String) {
        viewModelScope.launch {
            val download = downloadRepository.getDownload(id) ?: return@launch
            customDownloadManager.deleteDownload(download.movieId, download.quality)
        }
    }

    fun pauseDownload(id: String) {
        customDownloadManager.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch {
            customDownloadManager.resumeDownload(id) { }
        }
    }

    fun redownload(id: String) {
        viewModelScope.launch {
            val download = downloadRepository.getDownload(id) ?: return@launch
            // First delete existing if any
            customDownloadManager.deleteDownload(download.movieId, download.quality)
            // Then restart
            customDownloadManager.downloadMovie(
                movieId = download.movieId,
                quality = download.quality,
                url = download.downloadUrl,
                movieName = download.title,
                posterUrl = download.posterUrl,
                fileSize = download.fileSize,
                onProgress = { }
            )
        }
    }

    fun getDownloadDir(): String {
        return customDownloadManager.getDownloadDirectoryDisplay()
    }
}
