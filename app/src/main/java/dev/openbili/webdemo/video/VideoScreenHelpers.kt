package dev.openbili.webdemo.video

import dev.openbili.webdemo.ui.ControlVideoMode

internal data class PlaybackContinuationTarget(
  val key: String,
  val title: String,
  val coverUrl: String,
  val countdownSeconds: Int,
  val onSelect: () -> Unit,
)

internal fun shouldTakePlaybackEndControl(
  controlMode: Boolean,
  playbackPageForeground: Boolean,
  currentMode: ControlVideoMode,
  playerSurfaceFocused: Boolean,
  alreadyOwned: Boolean,
): Boolean =
  controlMode &&
    playbackPageForeground &&
    (alreadyOwned ||
      currentMode != ControlVideoMode.PAGE_NAVIGATION ||
      playerSurfaceFocused)
