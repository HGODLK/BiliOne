package dev.openbili.webdemo.offline

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import dev.openbili.webdemo.MainActivity
import dev.openbili.webdemo.R

@OptIn(UnstableApi::class)
class OfflineDownloadService :
  DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    NOTIFICATION_CHANNEL_ID,
    R.string.offline_download_channel_name,
    R.string.offline_download_channel_description,
  ) {
  private val notificationHelper by lazy {
    DownloadNotificationHelper(this, NOTIFICATION_CHANNEL_ID)
  }

  override fun getDownloadManager(): DownloadManager = OfflineMediaManager.get(this).downloadManager

  override fun getScheduler(): Scheduler = PlatformScheduler(this, SCHEDULER_JOB_ID)

  override fun getForegroundNotification(
    downloads: List<Download>,
    notMetRequirements: Int,
  ): Notification =
    notificationHelper.buildProgressNotification(
      this,
      R.drawable.ic_status_offline,
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      ),
      getString(R.string.offline_download_notification_message),
      downloads,
      notMetRequirements,
    )

  private companion object {
    const val FOREGROUND_NOTIFICATION_ID = 41_201
    const val SCHEDULER_JOB_ID = 41_202
    const val NOTIFICATION_CHANNEL_ID = "offline_downloads"
  }
}
