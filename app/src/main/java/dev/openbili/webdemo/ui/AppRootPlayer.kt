package dev.openbili.webdemo.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import dev.openbili.webdemo.PlaybackProgressStore
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerViewModel
import dev.openbili.webdemo.api.BiliApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Player interaction state that must survive recomposition while AppRoot moves between pages. */
internal class AppRootPlayerSessionState(
  initialShowDanmaku: Boolean = true,
) {
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
  /** Increments only for intentional timeline changes, so danmaku can bypass clock smoothing. */
  var danmakuPositionEpoch by mutableStateOf(0L)
    private set

  private var retainRestoredPlaybackEnd = false
  private var seekGeneration = 0L
  private var seekConfirmationJob: Job? = null
  private var speedBeforeTemporaryBoost: Float? = null

  fun previewSeek(playerViewModel: PlayerViewModel, targetMs: Long) {
    val player = playerViewModel.exoPlayer ?: return
    if (scrubPreviewMs == null && pendingSeekTargetMs == null) {
      playerViewModel.resetCdnBufferingDetectorForUserSeek()
      seekWasPlaying = player.isPlaying
      player.pause()
    }
    seekConfirmationJob?.cancel()
    pendingSeekTargetMs = null
    val target = targetMs.coerceAtLeast(0L)
    // A scrub pauses Media3, so the ordinary 200 ms player-position poll stops advancing the
    // overlay. Each new preview target is therefore an explicit danmaku timeline update rather
    // than waiting for the eventual committed seek; otherwise comments appear to disappear while
    // the thumb is dragged and only return after release.
    if (scrubPreviewMs != target) advanceDanmakuPositionEpoch()
    scrubPreviewMs = target
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
      // Keep the long-press boost active, but make its release return to the newly selected speed.
      speedBeforeTemporaryBoost = resolved
    }
  }

  /** Keeps a restored parent's end overlay mounted while its media is prepared back at the end. */
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
    seekConfirmationJob?.cancel()
    seekConfirmationJob = null
    seekGeneration++
    advanceDanmakuPositionEpoch()
    scrubPreviewMs = null
    pendingSeekTargetMs = null
    currentPositionMs = player?.currentPosition ?: currentPositionMs
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
    scrubPreviewMs = null
    pendingSeekTargetMs = target
    currentPositionMs = target
    seekConfirmationJob?.cancel()
    player.seekTo(target)
    seekConfirmationJob = scope.launch {
      // Remote DASH seeks are asynchronous. Keep the preview authoritative until Media3 reports
      // the target, so the progress bar and danmaku do not jump back to an older polling sample.
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
    seekGeneration++
    advanceDanmakuPositionEpoch()
    scrubPreviewMs = null
    pendingSeekTargetMs = null
    seekWasPlaying = false
  }

  private fun advanceDanmakuPositionEpoch() {
    danmakuPositionEpoch++
  }
}

/**
 * Owns player preparation, polling, background pause, history reporting, and first-frame reveal.
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
  onCommitPlaybackProgress: () -> Unit,
  onRevealTransitionSession: (CardTransitionSession, Boolean) -> Unit,
) {
  var appInForeground by
    androidx.compose.runtime.remember(lifecycleOwner) {
      mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

  LaunchedEffect(selectedVideoId) { sessionState.resetSeek() }

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

  DisposableEffect(Unit) { onDispose { playerViewModel.release() } }

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
  ) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> appInForeground = true
        Lifecycle.Event.ON_STOP -> {
          appInForeground = false
          onCommitPlaybackProgress()
          playerViewModel.pauseForBackground()
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
  ) {
    if (
      !isVideoScreen ||
        historyAid <= 0 ||
        historyCid <= 0 ||
        !appInForeground ||
        playerState !is PlayerState.Ready
    )
      return@LaunchedEffect
    while (true) {
      delay(2_000)
      if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        return@LaunchedEffect
      }
      val player = playerViewModel.exoPlayer
      if (player != null) {
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
              if (bangumiSubType > 0) {
                BiliApi.reportBangumiPlayback(
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
                BiliApi.reportPlayback(historyAid, historyCid, positionMs / 1000L)
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
