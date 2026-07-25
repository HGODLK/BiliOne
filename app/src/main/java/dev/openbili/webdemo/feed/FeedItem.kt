package dev.openbili.webdemo.feed

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
)
