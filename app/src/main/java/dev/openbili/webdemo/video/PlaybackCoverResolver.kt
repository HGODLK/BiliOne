package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.VideoInfo

/**
 * 只解析真实封面。`pages.first_frame` 是解码出的视频帧而非封面，刻意不放进
 * [dev.openbili.webdemo.api.VideoPage]；普通多 P 投稿在 B 站未提供独立分 P 封面时
 * 因此回退到投稿封面。
 */
internal fun resolvePlaybackCoverUrl(
  currentEpisodeCoverUrl: String,
  videoInfo: VideoInfo?,
  currentCid: Long,
  fallbackItemCoverUrl: String,
): String {
  val collectionEpisodeCover =
    videoInfo
      ?.collection
      ?.episodes
      ?.firstOrNull { episode -> currentCid > 0L && episode.cid == currentCid }
      ?.coverUrl
      .orEmpty()
      .ifBlank {
        videoInfo
          ?.collection
          ?.episodes
          ?.firstOrNull { episode -> episode.bvid == videoInfo.bvid }
          ?.coverUrl
          .orEmpty()
      }
  return currentEpisodeCoverUrl
    .ifBlank { collectionEpisodeCover }
    .ifBlank { videoInfo?.coverUrl.orEmpty() }
    .ifBlank { fallbackItemCoverUrl }
}
