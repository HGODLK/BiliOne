package dev.openbili.webdemo.live

/**
 * 直播封面地址选取工具。
 *
 * 直播卡片在列表中展示封面时，优先使用关键帧图（keyframeUrl，通常更新、更贴近当前
 * 直播画面），仅当关键帧图缺失或为空白时才回退到静态封面 coverUrl。
 */

/**
 * 返回当前用于展示的直播封面地址。
 *
 * 优先取 [LiveSearchRoom.keyframeUrl]（非空白时），否则回退到 [LiveSearchRoom.coverUrl]，
 * 兜底返回空字符串以避免调用方拿到 null。
 */
internal fun LiveSearchRoom.currentDisplayCoverUrl(): String =
  keyframeUrl?.takeIf(String::isNotBlank) ?: coverUrl.orEmpty()
