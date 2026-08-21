package dev.openbili.webdemo.video

import androidx.compose.ui.focus.FocusRequester

/**
 * 番剧触屏播放页在 [ControlVideoMode.PAGE_NAVIGATION] 下的附加页面焦点。
 *
 * 播放器仍然复用通用视频控制链；这个小契约只描述其下方两张番剧大卡与播放器之间的
 * 空间关系，避免为番剧复制另一套播放控制状态机。
 */
internal data class BangumiLowerPanelControlFocus(
  val detail: FocusRequester,
  val episodes: FocusRequester,
  val player: FocusRequester,
)
