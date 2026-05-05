package com.kstream.core.domain

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@UnstableApi
class DownloadMovieUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // This is a placeholder as the real service class reference is in a feature module.
    // In a production app, the service class should be injected or passed as a parameter.
    operator fun invoke(movieId: String, url: String, title: String) {
        val downloadRequest = DownloadRequest.Builder(movieId, Uri.parse(url))
            .build()
        
        // This won't compile because it needs the service class. 
        // I will rely on the app module to handle this binding or use a service Intent.
    }
}
