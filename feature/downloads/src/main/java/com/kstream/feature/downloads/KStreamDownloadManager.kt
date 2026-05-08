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
import android.net.Uri
import kotlinx.coroutines.flow.firstOrNull

@Singleton
@UnstableApi
class KStreamDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userDataRepository: com.kstream.core.domain.repository.UserDataRepository
) {
    private val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context)
    
    private val downloadDirectory: File by lazy {
        val savedLocation = try {
            kotlinx.coroutines.runBlocking { 
                userDataRepository.downloadLocation.firstOrNull() 
            }
        } catch (e: Exception) {
            null
        }

        val baseDir = if (savedLocation.isNullOrBlank()) {
            // Default to Movies/KStream in app-specific external storage to avoid permission issues
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir
        } else {
            val file = File(Uri.parse(savedLocation).path ?: savedLocation)
            if (file.exists() || file.mkdirs()) {
                file
            } else {
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: context.filesDir
            }
        }
        
        val finalDir = File(baseDir, "KStream")
        if (!finalDir.exists()) finalDir.mkdirs()
        finalDir
    }
    
    private val downloadCache: SimpleCache by lazy {
        SimpleCache(
            File(downloadDirectory, "cache"),
            NoOpCacheEvictor(),
            databaseProvider
        )
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            context,
            databaseProvider,
            downloadCache,
            DefaultHttpDataSource.Factory(),
            Executor { it.run() }
        ).apply {
            maxParallelDownloads = 3
        }
    }
    
    fun getDownloadDirectory(): String {
        return downloadDirectory.absolutePath
    }
}

