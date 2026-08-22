package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.feed.FeedItem

/** 生成可复制的公开视频页链接，避免把应用私有离线 URI 暴露给用户。 */
internal fun buildVideoShareUrl(info: VideoInfo?, item: FeedItem): String {
  val videoId = (info?.bvid ?: item.id).trim()
  if (
    videoId.startsWith("BV", ignoreCase = true) ||
      (videoId.startsWith("av", ignoreCase = true) &&
        videoId.drop(2).isNotBlank() &&
        videoId.drop(2).all(Char::isDigit))
  ) {
    return "https://www.bilibili.com/video/$videoId"
  }

  return item.videoUrl
    .trim()
    .takeIf { it.startsWith("http://") || it.startsWith("https://") }
    .orEmpty()
}
