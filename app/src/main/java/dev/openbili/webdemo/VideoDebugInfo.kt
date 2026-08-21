package dev.openbili.webdemo

/** 视频 WebView 初始化期间收集的调试诊断信息。 */
data class VideoDebugInfo(
  val setUserAgent: String?,
  val actualUserAgent: String?,
  val videoUrl: String,
  val uaApplied: Boolean,
)
