package com.kstream.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kstream.core.common.NetworkMonitor
import com.kstream.core.domain.repository.DownloadRepository
import com.kstream.core.model.Download
import com.kstream.core.model.DownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadSortOption(val label: String) {
    DATE_DESC("Latest First"),
    DATE_ASC("Oldest First"),
    ALPHABET_ASC("A → Z"),
    ALPHABET_DESC("Z → A"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    STATUS("Status")
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val customDownloadManager: CustomDownloadManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(DownloadSortOption.DATE_DESC)
    val sortOption: StateFlow<DownloadSortOption> = _sortOption.asStateFlow()

    init {
        // Auto-resume paused downloads when connectivity returns
        networkMonitor.isOnline
            .distinctUntilChanged()
            .filter { it }
            .drop(1) // Skip initial emission
            .onEach { resumePausedDownloads() }
            .launchIn(viewModelScope)
    }

    private fun resumePausedDownloads() {
        viewModelScope.launch {
            try {
                val paused = downloadRepository.getDownloads().first()
                    .filter { it.status == DownloadStatus.PAUSED }
                paused.forEach { download ->
                    customDownloadManager.resumeDownload(download.id) { }
                }
            } catch (_: Exception) { }
        }
    }

    val downloads: Flow<List<Download>> = combine(
        downloadRepository.getDownloads(),
        _searchQuery,
        _sortOption
    ) { allDownloads, query, sort ->
        val filtered = if (query.isBlank()) {
            allDownloads
        } else {
            allDownloads.filter { it.title.contains(query, ignoreCase = true) }
        }
        applySorting(filtered, sort)
    }

    private fun applySorting(downloads: List<Download>, option: DownloadSortOption): List<Download> {
        return when (option) {
            DownloadSortOption.DATE_DESC -> downloads.sortedByDescending { it.createdAt }
            DownloadSortOption.DATE_ASC -> downloads.sortedBy { it.createdAt }
            DownloadSortOption.ALPHABET_ASC -> downloads.sortedBy { it.title.lowercase() }
            DownloadSortOption.ALPHABET_DESC -> downloads.sortedByDescending { it.title.lowercase() }
            DownloadSortOption.SIZE_DESC -> downloads.sortedByDescending { it.totalBytes }
            DownloadSortOption.SIZE_ASC -> downloads.sortedBy { it.totalBytes }
            DownloadSortOption.STATUS -> {
                val statusOrder = mapOf(
                    com.kstream.core.model.DownloadStatus.DOWNLOADING to 0,
                    com.kstream.core.model.DownloadStatus.QUEUED to 1,
                    com.kstream.core.model.DownloadStatus.PAUSED to 2,
                    com.kstream.core.model.DownloadStatus.COMPLETED to 3,
                    com.kstream.core.model.DownloadStatus.FAILED to 4,
                    com.kstream.core.model.DownloadStatus.DELETED to 5
                )
                downloads.sortedBy { statusOrder[it.status] ?: 99 }
            }
        }
    }

    fun onSortChange(option: DownloadSortOption) {
        _sortOption.value = option
    }

    suspend fun checkFileExists(filePath: String): Boolean {
        return customDownloadManager.checkFileExists(filePath)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun removeDownload(id: String) {
        viewModelScope.launch {
            try {
                val download = downloadRepository.getDownload(id) ?: return@launch
                customDownloadManager.deleteDownload(download.movieId, download.quality)
            } catch (_: Exception) { }
        }
    }

    fun pauseDownload(id: String) {
        try {
            customDownloadManager.pauseDownload(id)
        } catch (_: Exception) { }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch {
            try {
                customDownloadManager.resumeDownload(id) { }
            } catch (_: Exception) { }
        }
    }

    fun redownload(id: String) {
        viewModelScope.launch {
            try {
                val download = downloadRepository.getDownload(id) ?: return@launch
                customDownloadManager.deleteDownload(download.movieId, download.quality)
                customDownloadManager.downloadMovie(
                    movieId = download.movieId,
                    quality = download.quality,
                    url = download.downloadUrl,
                    movieName = download.title,
                    posterUrl = download.posterUrl,
                    fileSize = download.fileSize,
                    onProgress = { }
                )
            } catch (_: Exception) { }
        }
    }

    fun getDownloadDir(): String {
        return customDownloadManager.getDownloadDirectoryDisplay()
    }
}
