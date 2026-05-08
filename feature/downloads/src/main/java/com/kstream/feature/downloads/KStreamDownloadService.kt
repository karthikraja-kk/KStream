package com.kstream.feature.downloads

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.media3.exoplayer.offline.DownloadNotificationHelper

@UnstableApi
@AndroidEntryPoint
class KStreamDownloadService : DownloadService(
    1, // FOREGROUND_NOTIFICATION_ID
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    "kstream_download_channel",
    com.kstream.feature.downloads.R.string.download_channel_name,
    0
) {
    @Inject
    lateinit var kstreamDownloadManager: KStreamDownloadManager

    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = DownloadNotificationHelper(this, "kstream_download_channel")
    }

    override fun getDownloadManager(): DownloadManager {
        return kstreamDownloadManager.downloadManager
    }

    override fun getScheduler(): Scheduler? {
        return null // Add PlatformScheduler if needed
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}
