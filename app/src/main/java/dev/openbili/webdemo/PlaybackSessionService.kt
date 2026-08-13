package dev.openbili.webdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Publishes the process-wide detail player to Android system media controls.
 *
 * The service never creates a second playback timeline: both it and the Activity obtain the same
 * application-scoped [PlayerViewModel] and therefore the same ExoPlayer instance. The Activity
 * continues to own the only PlayerView/SurfaceView used by the existing shared-element animations.
 */
@OptIn(UnstableApi::class)
class PlaybackSessionService : MediaSessionService() {
  private var mediaSession: MediaSession? = null
  private var notificationPlayer: Player? = null
  private var sessionActivity: PendingIntent? = null
  private val notificationPlayerListener =
    object : Player.Listener {
      override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        updateForegroundNotification()
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateForegroundNotification()
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateForegroundNotification()
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        updateForegroundNotification()
      }
    }

  override fun onCreate() {
    super.onCreate()
    // Keep a paused, prepared video available to Samsung's media panel. Without this, the default
    // Media3 notification manager leaves the foreground and One UI stops the idle service shortly
    // after the Activity's default "pause when leaving" transition.
    setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_ALWAYS)
    val player = playerForTarget((application as BiliApplication).playbackSessionTarget)
    createNotificationChannel()
    sessionActivity =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    mediaSession =
      MediaSession.Builder(this, player)
        .setSessionActivity(checkNotNull(sessionActivity))
        .build()
    notificationPlayer = player
    player.addListener(notificationPlayerListener)
    updateForegroundNotification()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    mediaSession

  override fun onUpdateNotification(
    session: MediaSession,
    startInForegroundRequired: Boolean,
  ) {
    updateForegroundNotification(session)
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    val app = application as BiliApplication
    if (app.playbackSessionTarget == PlaybackSessionTarget.MUSIC) {
      app.homeMusicPlayerViewModel.stopForTaskRemoval()
    } else {
      app.sharedPlayerViewModel.pauseForBackground()
      app.sharedPlayerViewModel.resetForWarmIdle(stopMediaSession = false)
    }
    pauseAllPlayersAndStopSelf()
  }

  override fun onDestroy() {
    notificationPlayer?.removeListener(notificationPlayerListener)
    notificationPlayer = null
    stopForeground(STOP_FOREGROUND_REMOVE)
    mediaSession?.release()
    mediaSession = null
    sessionActivity = null
    super.onDestroy()
  }

  private fun updateForegroundNotification(session: MediaSession? = mediaSession) {
    session ?: return
    val notification = buildPlaybackNotification(session)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun buildPlaybackNotification(session: MediaSession): Notification {
    val player = session.player
    val musicSession =
      (application as BiliApplication).playbackSessionTarget == PlaybackSessionTarget.MUSIC
    val metadata = player.mediaMetadata
    val title = metadata.title?.takeIf(CharSequence::isNotBlank) ?: getString(R.string.app_name)
    val artist =
      metadata.artist?.takeIf(CharSequence::isNotBlank)
        ?: if (musicSession) "音乐播放" else "视频播放"
    val toggleIntent =
      PendingIntent.getService(
        this,
        2,
        Intent(this, PlaybackSessionService::class.java).setAction(ACTION_TOGGLE_PLAYBACK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val actionIcon =
      if (player.isPlaying) android.R.drawable.ic_media_pause
      else android.R.drawable.ic_media_play
    val actionTitle = if (player.isPlaying) "暂停" else "播放"
    val builder =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
      } else {
        @Suppress("DEPRECATION") Notification.Builder(this)
      }
    builder
      .setSmallIcon(R.drawable.ic_launcher)
      .setContentTitle(title)
      .setContentText(artist)
      .setContentIntent(sessionActivity)
      .setCategory(Notification.CATEGORY_TRANSPORT)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setOnlyAlertOnce(true)
      .setOngoing(true)
      .setShowWhen(false)
    if (musicSession) {
      builder
        .addAction(
          Notification.Action.Builder(
              android.R.drawable.ic_media_previous,
              "上一首",
              serviceActionIntent(ACTION_PREVIOUS, requestCode = 1),
            )
            .build()
        )
        .addAction(Notification.Action.Builder(actionIcon, actionTitle, toggleIntent).build())
        .addAction(
          Notification.Action.Builder(
              android.R.drawable.ic_media_next,
              "下一首",
              serviceActionIntent(ACTION_NEXT, requestCode = 3),
            )
            .build()
        )
        .setStyle(
          Notification.MediaStyle()
            .setMediaSession(session.platformToken)
            .setShowActionsInCompactView(0, 1, 2)
        )
    } else {
      builder
        .addAction(Notification.Action.Builder(actionIcon, actionTitle, toggleIntent).build())
        .setStyle(
          Notification.MediaStyle()
            .setMediaSession(session.platformToken)
            .setShowActionsInCompactView(0)
        )
    }
    return builder.build()
  }

  private fun serviceActionIntent(action: String, requestCode: Int): PendingIntent =
    PendingIntent.getService(
      this,
      requestCode,
      Intent(this, PlaybackSessionService::class.java).setAction(action),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        "媒体播放",
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = "显示当前视频或音乐与系统播放控件"
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
      }
    manager.createNotificationChannel(channel)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val app = application as BiliApplication
    when (intent?.action) {
      ACTION_BIND_MUSIC -> bindTargetPlayer(PlaybackSessionTarget.MUSIC)
      ACTION_BIND_DETAIL -> bindTargetPlayer(PlaybackSessionTarget.DETAIL)
      ACTION_PREVIOUS -> app.homeMusicPlayerViewModel.playPrevious()
      ACTION_NEXT -> app.homeMusicPlayerViewModel.playNext()
      ACTION_TOGGLE_PLAYBACK -> {
        if (app.playbackSessionTarget == PlaybackSessionTarget.MUSIC) {
          app.homeMusicPlayerViewModel.togglePlayback()
        } else {
          mediaSession?.player?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
          }
        }
      }
    }
    updateForegroundNotification()
    return super.onStartCommand(intent, flags, startId)
  }

  private fun playerForTarget(target: PlaybackSessionTarget): Player {
    val app = application as BiliApplication
    return when (target) {
      PlaybackSessionTarget.DETAIL ->
        app.sharedPlayerViewModel.preparePlayer(publishSystemControls = false)
      PlaybackSessionTarget.MUSIC -> app.homeMusicPlayerViewModel.preparePlayer()
    }
  }

  private fun bindTargetPlayer(target: PlaybackSessionTarget) {
    val app = application as BiliApplication
    app.playbackSessionTarget = target
    val nextPlayer = playerForTarget(target)
    if (notificationPlayer === nextPlayer) return
    notificationPlayer?.removeListener(notificationPlayerListener)
    mediaSession?.player = nextPlayer
    notificationPlayer = nextPlayer
    nextPlayer.addListener(notificationPlayerListener)
  }

  companion object {
    fun ensureStarted(context: Context) {
      runCatching {
        ContextCompat.startForegroundService(
          context.applicationContext,
          Intent(context.applicationContext, PlaybackSessionService::class.java),
        )
      }
    }

    fun publishMusicPlayer(context: Context) {
      publishTarget(context, PlaybackSessionTarget.MUSIC, ACTION_BIND_MUSIC)
    }

    fun publishDetailPlayer(context: Context) {
      publishTarget(context, PlaybackSessionTarget.DETAIL, ACTION_BIND_DETAIL)
    }

    private fun publishTarget(
      context: Context,
      target: PlaybackSessionTarget,
      action: String,
    ) {
      val app = context.applicationContext as? BiliApplication
      app?.playbackSessionTarget = target
      runCatching {
        ContextCompat.startForegroundService(
          context.applicationContext,
          Intent(context.applicationContext, PlaybackSessionService::class.java).setAction(action),
        )
      }
    }

    fun stop(context: Context) {
      context.applicationContext.stopService(
        Intent(context.applicationContext, PlaybackSessionService::class.java)
      )
    }

    private const val NOTIFICATION_ID = 2_333
    private const val NOTIFICATION_CHANNEL_ID = "bilione_video_playback"
    private const val ACTION_TOGGLE_PLAYBACK =
      "dev.openbili.webdemo.action.TOGGLE_PLAYBACK"
    private const val ACTION_PREVIOUS = "dev.openbili.webdemo.action.PREVIOUS"
    private const val ACTION_NEXT = "dev.openbili.webdemo.action.NEXT"
    private const val ACTION_BIND_MUSIC = "dev.openbili.webdemo.action.BIND_MUSIC"
    private const val ACTION_BIND_DETAIL = "dev.openbili.webdemo.action.BIND_DETAIL"
  }
}
