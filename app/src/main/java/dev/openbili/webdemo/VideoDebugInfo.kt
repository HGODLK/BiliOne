package dev.openbili.webdemo

/** Debug diagnostics collected during video WebView initialisation. */
data class VideoDebugInfo(
  val setUserAgent: String?,
  val actualUserAgent: String?,
  val videoUrl: String,
  val uaApplied: Boolean,
)
