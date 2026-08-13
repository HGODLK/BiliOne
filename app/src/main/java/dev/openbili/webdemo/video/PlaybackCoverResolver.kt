package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.VideoInfo

/**
 * Resolves only real artwork. `pages.first_frame` is a decoded video frame rather than a cover and
 * is intentionally absent from [dev.openbili.webdemo.api.VideoPage]. Regular multi-P submissions
 * therefore fall back to the submission cover when Bilibili exposes no independent part artwork.
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
