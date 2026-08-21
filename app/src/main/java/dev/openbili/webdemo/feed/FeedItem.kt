package dev.openbili.webdemo.feed

import dev.openbili.webdemo.api.VideoPage

/**
 * 信息流卡片的数据模型。
 *
 * 这是推荐、热门、历史、稍后再看、缓存等多个页面共用的最小视频卡片模型，字段均按
 * 可空或带默认值设计，以适配不同数据源（网页提取、接口返回、本地离线）。
 */
data class FeedItem(
  val id: String,
  val title: String,
  val videoUrl: String,
  val coverUrl: String,
  val uploader: String?,
  val playCount: String?,
  val duration: String?,
  val uploaderFace: String? = null,
  val uploaderMid: Long = 0,
  val danmakuCount: Long = 0,
  val publishedAt: Long = 0,
  val description: String = "",
  val favoriteResourceId: Long = 0L,
  val favoriteResourceType: Int = 0,
  val playbackPage: VideoPage? = null,
)
