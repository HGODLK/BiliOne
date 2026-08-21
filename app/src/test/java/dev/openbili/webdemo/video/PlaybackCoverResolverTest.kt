package dev.openbili.webdemo.video

import dev.openbili.webdemo.api.VideoCollection
import dev.openbili.webdemo.api.VideoCollectionEpisode
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCoverResolverTest {
  @Test
  fun currentBangumiEpisodeCoverHasHighestPriority() {
    val info = videoInfo(videoCover = "video-cover", collectionCover = "collection-cover")

    assertEquals(
      "episode-cover",
      resolvePlaybackCoverUrl(
        currentEpisodeCoverUrl = "episode-cover",
        videoInfo = info,
        currentCid = 202L,
        fallbackItemCoverUrl = "item-cover",
      ),
    )
  }

  @Test
  fun currentCollectionEpisodeIsMatchedByCidBeforeBvid() {
    val info =
      videoInfo(videoCover = "video-cover", collectionCover = "same-bvid-cover")
        .copy(
          collection =
            VideoCollection(
              id = 1L,
              title = "合集",
              episodes =
                listOf(
                  episode(
                    bvid = "BV-current",
                    cid = 101L,
                    title = "上一集",
                    coverUrl = "same-bvid-cover",
                  ),
                  episode(
                    bvid = "BV-next",
                    cid = 202L,
                    title = "当前集",
                    coverUrl = "current-cid-cover",
                  ),
                ),
            )
        )

    assertEquals(
      "current-cid-cover",
      resolvePlaybackCoverUrl("", info, 202L, "item-cover"),
    )
  }

  @Test
  fun regularMultiPartFallsBackToSubmissionCover() {
    val info = videoInfo(videoCover = "submission-cover", collectionCover = "")

    assertEquals(
      "submission-cover",
      resolvePlaybackCoverUrl("", info, 202L, "item-cover"),
    )
  }

  private fun videoInfo(videoCover: String, collectionCover: String): VideoInfo =
    VideoInfo(
      bvid = "BV-current",
      aid = 1L,
      cid = 101L,
      title = "标题",
      coverUrl = videoCover,
      uploaderName = "UP",
      uploaderFace = "",
      uploaderMid = 2L,
      durationSeconds = 120L,
      playCount = 0L,
      danmakuCount = 0L,
      replyCount = 0L,
      likeCount = 0L,
      coinCount = 0L,
      favoriteCount = 0L,
      shareCount = 0L,
      publishedAt = 0L,
      desc = "",
      pages =
        listOf(
          VideoPage(page = 1, cid = 101L, part = "P1", durationSeconds = 60L),
          VideoPage(page = 2, cid = 202L, part = "P2", durationSeconds = 60L),
        ),
      collection =
        collectionCover.takeIf(String::isNotBlank)?.let { cover ->
          VideoCollection(
            id = 1L,
            title = "合集",
            episodes =
              listOf(
                episode(
                  bvid = "BV-current",
                  cid = 101L,
                  title = "当前视频",
                  coverUrl = cover,
                )
              ),
          )
        },
    )

  private fun episode(
    bvid: String,
    cid: Long,
    title: String,
    coverUrl: String,
  ): VideoCollectionEpisode =
    VideoCollectionEpisode(
      bvid = bvid,
      cid = cid,
      title = title,
      coverUrl = coverUrl,
      durationSeconds = 60L,
      uploaderName = "UP",
      uploaderFace = "",
      uploaderMid = 2L,
      playCount = 0L,
      danmakuCount = 0L,
      publishedAt = 0L,
    )
}
