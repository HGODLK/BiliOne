package dev.openbili.webdemo.live

import dev.openbili.webdemo.api.DanmakuItem

/**
 * 直播弹幕「开播后渲染」过滤策略。
 *
 * 直播聊天在播放器真正起播前就可能已开始。若在用户首次点击播放时把积压的整段历史弹幕
 * 一次性渲染出来，文字测量与位图准备就会去和视频解码器的首帧争抢资源，拖慢首帧出画。
 * 因此这里在播放与首帧都就绪后，只回放一小段「回头看」窗口内的弹幕；之后到达的弹幕
 * 照常放行。
 */

/**
 * 过滤出开播后允许渲染的弹幕子集。
 *
 * 仅保留时间戳不早于 [cutoff]（= 渲染起点减去回看窗口）的弹幕；若渲染起点未知则直接
 * 返回空列表，避免在播放尚未定位时把整段历史弹幕倾泻到屏幕上。
 */
internal fun liveDanmakuAfterPlaybackStart(
  items: List<DanmakuItem>,
  renderStartPositionMs: Long?,
  lookbackMs: Long = LIVE_DANMAKU_STARTUP_LOOKBACK_MS,
): List<DanmakuItem> {
  val start = renderStartPositionMs ?: return emptyList()
  val cutoff = (start - lookbackMs.coerceAtLeast(0L)).coerceAtLeast(0L)
  return items.filter { it.timeMs >= cutoff }
}

/** 开播后允许回看的弹幕时间窗口，单位毫秒。 */
internal const val LIVE_DANMAKU_STARTUP_LOOKBACK_MS = 600L
