package dev.openbili.webdemo.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import dev.openbili.webdemo.api.BiliReportApi
import dev.openbili.webdemo.PlaybackProgressStore
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 在 AppRoot 于页面间移动时，必须跨重组存活的播放器交互状态。 */
internal class AppRootPlayerSessionState(initialShowDanmaku: Boolean = true) {
  var currentPositionMs by mutableStateOf(0L)
  var scrubPreviewMs by mutableStateOf<Long?>(null)
  var pendingSeekTargetMs by mutableStateOf<Long?>(null)
  var seekWasPlaying by mutableStateOf(false)
  var isPlaying by mutableStateOf(false)
  var isBuffering by mutableStateOf(false)
  var showDanmaku by mutableStateOf(initialShowDanmaku)
  var playerReady by mutableStateOf(false)
  var playerControlsVisible by mutableStateOf(true)
  var playbackEnded by mutableStateOf(false)
  var playbackSpeed by mutableStateOf(1f)
  /** 只在有意的时间线变化时递增，让弹幕可以绕过时钟平滑。 */
  var danmakuPositionEpoch by mutableStateOf(0L)
    private set

  private var retainRestoredPlaybackEnd = false
  private var seekGeneration = 0L
  private var seekConfirmationJob: Job? = null
  private var scrubFrameSeekJob: Job? = null
  private var scrubStartPositionMs: Long? = null
  private var latestScrubFrameTargetMs: Long? = null
  private var lastScrubFrameSeekAtNanos = Long.MIN_VALUE
  private var speedBeforeTemporaryBoost: Float? = null
  private var playbackMediaKey: String? = null

  fun previewSeek(
    playerViewModel: PlayerViewModel,
    targetMs: Long,
    scope: CoroutineScope,
  ) {
    val player = playerViewModel.exoPlayer ?: return
    if (scrubPreviewMs == null && pendingSeekTargetMs == null) {
      playerViewModel.resetCdnBufferingDetectorForUserSeek()
      seekWasPlaying = player.isPlaying
      scrubStartPositionMs = player.currentPosition
      player.pause()
    }
    seekConfirmationJob?.cancel()
    pendingSeekTargetMs = null
    val target = targetMs.coerceAtLeast(0L)
    // 拖动会暂停 Media3，因此普通的 200 ms 播放器位置轮询不再推进覆盖层。每个新的
    // 预览目标因此都是一次显式的弹幕时间线更新，而不是等待最终提交的 seek；否则
    // 拖拽拇指时评论看似消失，只有松手后才回来。
    if (scrubPreviewMs != target) advanceDanmakuPositionEpoch()
    scrubPreviewMs = target
    requestScrubFrame(player, target, scope)
  }

  fun setTemporarySpeedBoost(playerViewModel: PlayerViewModel, active: Boolean) {
    val player = playerViewModel.exoPlayer
    if (active) {
      if (speedBeforeTemporaryBoost != null || player == null) return
      speedBeforeTemporaryBoost = player.playbackParameters.speed
      player.setPlaybackSpeed(3f)
    } else {
      val previous = speedBeforeTemporaryBoost ?: return
      speedBeforeTemporaryBoost = null
      player?.setPlaybackSpeed(previous)
    }
  }

  fun setPlaybackSpeed(playerViewModel: PlayerViewModel, speed: Float) {
    val resolved = speed.coerceIn(.5f, 2f)
    playbackSpeed = resolved
    if (speedBeforeTemporaryBoost == null) {
      playerViewModel.exoPlayer?.setPlaybackSpeed(resolved)
    } else {
      // 保持长按加速生效，但让它的释放回到新选的速度。
      speedBeforeTemporaryBoost = resolved
    }
  }

  fun resetPlaybackSpeedForMedia(playerViewModel: PlayerViewModel, mediaKey: String) {
    if (playbackMediaKey == mediaKey) return
    playbackMediaKey = mediaKey
    speedBeforeTemporaryBoost = null
    playbackSpeed = 1f
    playerViewModel.exoPlayer?.setPlaybackSpeed(1f)
  }

  /** 在媒体于结尾处重新准备期间，让恢复的父页保持其结束覆盖层挂载。 */
  fun restorePlaybackEnded(ended: Boolean) {
    playbackEnded = ended
    retainRestoredPlaybackEnd = ended
  }

  fun clearPlaybackEnded() {
    playbackEnded = false
    retainRestoredPlaybackEnd = false
  }

  fun updatePlaybackEndedFromPlayer(playerEnded: Boolean) {
    if (playerEnded) {
      playbackEnded = true
      retainRestoredPlaybackEnd = false
    } else if (!retainRestoredPlaybackEnd) {
      playbackEnded = false
    }
  }

  fun cancelSeekPreview(playerViewModel: PlayerViewModel) {
    val player = playerViewModel.exoPlayer
    val restorePositionMs = scrubStartPositionMs
    seekConfirmationJob?.cancel()
    seekConfirmationJob = null
    clearScrubFrameSeek()
    seekGeneration++
    advanceDanmakuPositionEpoch()
    scrubPreviewMs = null
    pendingSeekTargetMs = null
    if (player != null && restorePositionMs != null) {
      currentPositionMs = restorePositionMs
      player.seekTo(restorePositionMs)
    } else {
      currentPositionMs = player?.currentPosition ?: currentPositionMs
    }
    if (seekWasPlaying) player?.play()
    seekWasPlaying = false
  }

  fun commitSeek(
    playerViewModel: PlayerViewModel,
    targetMs: Long,
    durationSeconds: Long,
    scope: CoroutineScope,
  ) {
    val player = playerViewModel.exoPlayer ?: return
    if (scrubPreviewMs == null && pendingSeekTargetMs == null) {
      playerViewModel.resetCdnBufferingDetectorForUserSeek()
      seekWasPlaying = player.isPlaying
      player.pause()
    }
    val target = targetMs.coerceIn(0L, (durationSeconds * 1000L).coerceAtLeast(0L))
    val generation = ++seekGeneration
    advanceDanmakuPositionEpoch()
    clearScrubFrameSeek()
    scrubPreviewMs = null
    pendingSeekTargetMs = target
    currentPositionMs = target
    seekConfirmationJob?.cancel()
    player.seekTo(target)
    seekConfirmationJob = scope.launch {
      // 远端 DASH seek 是异步的。保持预览权威直到 Media3 上报目标位置，
      // 让进度条和弹幕不会跳回较旧的轮询采样。
      val deadline = System.nanoTime() + 1_500_000_000L
      delay(40)
      while (
        generation == seekGeneration &&
          kotlin.math.abs(player.currentPosition - target) > 750L &&
          System.nanoTime() < deadline
      ) {
        delay(40)
      }
      if (generation != seekGeneration || pendingSeekTargetMs != target) return@launch
      currentPositionMs = player.currentPosition
      pendingSeekTargetMs = null
      if (seekWasPlaying) player.play()
      seekWasPlaying = false
      seekConfirmationJob = null
    }
  }

  fun resetSeek() {
    seekConfirmationJob?.cancel()
    seekConfirmationJob = null
    clearScrubFrameSeek()
    seekGeneration++
    advanceDanmakuPositionEpoch()
    scrubPreviewMs = null
    pendingSeekTargetMs = null
    seekWasPlaying = false
  }

  /**
   * 暂停的 ExoPlayer 会渲染 seek 到达的那一帧，让视频在拖拽进度拇指时双向跟随。
   * 指针输入可以以 120 Hz 到达，因此把它合并成一小段尾随流，而不是要求远端 DASH
   * 源执行每一个中间 seek。进度条和弹幕通过 [scrubPreviewMs] 保持即时。
   */
  private fun requestScrubFrame(player: Player, targetMs: Long, scope: CoroutineScope) {
    latestScrubFrameTargetMs = targetMs
    val nowNanos = System.nanoTime()
    val delayMs = scrubFrameSeekDelayMs(lastScrubFrameSeekAtNanos, nowNanos)
    if (scrubFrameSeekJob == null && delayMs == 0L) {
      player.seekTo(targetMs)
      lastScrubFrameSeekAtNanos = nowNanos
      return
    }

    scrubFrameSeekJob?.cancel()
    scrubFrameSeekJob = scope.launch {
      if (delayMs > 0L) delay(delayMs)
      val latestTarget = latestScrubFrameTargetMs ?: return@launch
      player.seekTo(latestTarget)
      lastScrubFrameSeekAtNanos = System.nanoTime()
      scrubFrameSeekJob = null
    }
  }

  private fun clearScrubFrameSeek() {
    scrubFrameSeekJob?.cancel()
    scrubFrameSeekJob = null
    scrubStartPositionMs = null
    latestScrubFrameTargetMs = null
    lastScrubFrameSeekAtNanos = Long.MIN_VALUE
  }

  private fun advanceDanmakuPositionEpoch() {
    danmakuPositionEpoch++
  }
}

private const val SCRUB_FRAME_SEEK_INTERVAL_NANOS = 80_000_000L

internal fun scrubFrameSeekDelayMs(lastSeekAtNanos: Long, nowNanos: Long): Long {
  if (lastSeekAtNanos == Long.MIN_VALUE) return 0L
  val elapsedNanos = (nowNanos - lastSeekAtNanos).coerceAtLeast(0L)
  return ((SCRUB_FRAME_SEEK_INTERVAL_NANOS - elapsedNanos).coerceAtLeast(0L) + 999_999L) /
    1_000_000L
}

/**
 * 负责播放器准备、轮询、后台暂停、历史上报和首帧展示。
 */
@Composable
internal fun AppRootPlayerEffects(
  context: Context,
  lifecycleOwner: LifecycleOwner,
  isVideoScreen: Boolean,
  selectedVideoId: String?,
  playerActivationId: String?,
  playerState: PlayerState,
  renderedVideoId: String?,
  keepMediaWhileInFeed: Boolean,
  playerViewModel: PlayerViewModel,
  sessionState: AppRootPlayerSessionState,
  transitionSession: CardTransitionSession?,
  historyAid: Long,
  historyCid: Long,
  historyDuration: Long,
  historyStartTimestamp: Long,
  bangumiSubType: Int,
  bangumiEpisodeId: Long,
  bangumiSeasonId: Long,
  loggedIn: Boolean,
  pauseWhenLeavingApp: Boolean,
  onCommitPlaybackProgress: () -> Unit,
  onRevealTransitionSession: (CardTransitionSession, Boolean) -> Unit,
) {
  var appInForeground by
    androidx.compose.runtime.remember(lifecycleOwner) {
      mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

  LaunchedEffect(selectedVideoId) { sessionState.resetSeek() }

  val playbackMediaKey = selectedVideoId?.let { id ->
    "$id:${historyCid.takeIf { it > 0L } ?: bangumiEpisodeId.takeIf { it > 0L } ?: 0L}"
  }
  LaunchedEffect(playbackMediaKey) {
    playbackMediaKey?.let { sessionState.resetPlaybackSpeedForMedia(playerViewModel, it) }
  }

  LaunchedEffect(isVideoScreen, playerActivationId, selectedVideoId, keepMediaWhileInFeed) {
    if (isVideoScreen && playerActivationId == selectedVideoId) {
      playerViewModel.preparePlayer()
      sessionState.playerReady = true
      if (playerState is PlayerState.Ready) playerViewModel.playInitialStream()
    } else if (!isVideoScreen && !keepMediaWhileInFeed) {
      sessionState.playerReady = false
      playerViewModel.resetForWarmIdle()
    }
  }

  LaunchedEffect(playerState) {
    if (playerState is PlayerState.Ready) {
      playerViewModel.playInitialStream()
      sessionState.setPlaybackSpeed(playerViewModel, sessionState.playbackSpeed)
    }
  }

  LaunchedEffect(playerState) {
    if (playerState is PlayerState.Ready) {
      delay(500)
      while (true) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
          delay(500)
          continue
        }
        val player = playerViewModel.exoPlayer
        if (player != null) {
          if (sessionState.scrubPreviewMs == null && sessionState.pendingSeekTargetMs == null) {
            sessionState.currentPositionMs = player.currentPosition
          }
          sessionState.isPlaying = player.isPlaying
          sessionState.isBuffering = player.playbackState == Player.STATE_BUFFERING
          sessionState.updatePlaybackEndedFromPlayer(player.playbackState == Player.STATE_ENDED)
        }
        delay(200)
      }
    }
  }

  DisposableEffect(
    lifecycleOwner,
    historyAid,
    historyCid,
    historyDuration,
    historyStartTimestamp,
    bangumiSubType,
    bangumiEpisodeId,
    bangumiSeasonId,
    loggedIn,
    pauseWhenLeavingApp,
  ) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          appInForeground = true
          playerViewModel.setAppInForeground(true)
          playerViewModel.exitBackgroundAudioMode()
          onCommitPlaybackProgress()
        }
        Lifecycle.Event.ON_STOP -> {
          appInForeground = false
          playerViewModel.setAppInForeground(false)
          onCommitPlaybackProgress()
          if (pauseWhenLeavingApp) playerViewModel.pauseForBackground()
          else playerViewModel.enterBackgroundAudioMode()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(
    isVideoScreen,
    historyAid,
    historyCid,
    bangumiSubType,
    bangumiEpisodeId,
    bangumiSeasonId,
    playerState,
    loggedIn,
    appInForeground,
    pauseWhenLeavingApp,
  ) {
    if (
      !isVideoScreen ||
        historyAid <= 0 ||
        historyCid <= 0 ||
        (!appInForeground && pauseWhenLeavingApp) ||
        playerState !is PlayerState.Ready
    )
      return@LaunchedEffect
    while (true) {
      delay(2_000)
      if (
        pauseWhenLeavingApp &&
          !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
      ) {
        return@LaunchedEffect
      }
      val player = playerViewModel.exoPlayer
      if (
        player != null &&
          playerViewModel.isPlaybackIdentityActive(selectedVideoId, historyAid, historyCid) &&
          dev.openbili.webdemo.isPlaybackSnapshotValid(
            expectedMediaId = selectedVideoId,
            actualMediaId = player.currentMediaItem?.mediaId,
            playbackState = player.playbackState,
            requireReady = true,
          )
      ) {
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        PlaybackProgressStore.save(
          context.applicationContext,
          historyAid,
          historyCid,
          positionMs,
          historyDuration * 1000L,
        )
        if (loggedIn) {
          withContext(Dispatchers.IO) {
            runCatching {
              if (bangumiSubType > 0 && bangumiEpisodeId > 0L && bangumiSeasonId > 0L) {
                BiliReportApi.reportBangumiPlayback(
                  aid = historyAid,
                  cid = historyCid,
                  episodeId = bangumiEpisodeId,
                  seasonId = bangumiSeasonId,
                  playedSeconds = positionMs / 1000L,
                  durationSeconds = historyDuration,
                  startTimestamp = historyStartTimestamp,
                  subType = bangumiSubType,
                )
              } else {
                BiliReportApi.reportPlayback(historyAid, historyCid, positionMs / 1000L)
              }
            }
          }
        }
      }
    }
  }

  LaunchedEffect(playerState, renderedVideoId, transitionSession, selectedVideoId) {
    val session = transitionSession ?: return@LaunchedEffect
    if (session.phase != SessionPhase.WAITING_FIRST_FRAME) return@LaunchedEffect
    if (selectedVideoId != session.item.id) return@LaunchedEffect
    if (renderedVideoId != session.item.id && playerState !is PlayerState.Error)
      return@LaunchedEffect
    onRevealTransitionSession(session, false)
  }

  LaunchedEffect(transitionSession?.token, transitionSession?.phase) {
    val session = transitionSession ?: return@LaunchedEffect
    if (session.phase != SessionPhase.WAITING_FIRST_FRAME) return@LaunchedEffect
    delay(FIRST_FRAME_REVEAL_TIMEOUT_MS)
    if (
      transitionSession.token == session.token &&
        session.phase == SessionPhase.WAITING_FIRST_FRAME &&
        !session.reverseRequested
    ) {
      onRevealTransitionSession(session, true)
    }
  }
}
