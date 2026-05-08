package com.kstream.feature.downloads

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@UnstableApi
class KStreamDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDataRepository: com.kstream.core.domain.repository.UserDataRepository
) {
    private val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context)
    
    // In a real app, we'd observe userDataRepository.downloadLocation and re-init cache
    private val downloadDirectory: File = context.getExternalFilesDir(null) ?: context.filesDir
    
    private val downloadCache: SimpleCache = SimpleCache(
        File(downloadDirectory, "downloads"),
        NoOpCacheEvictor(),
        databaseProvider
    )

    val downloadManager: DownloadManager = DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        DefaultHttpDataSource.Factory(),
        Executor { it.run() }
    )
    
    fun getDownloadDirectory(): String {
        return downloadDirectory.absolutePath
    }
}
