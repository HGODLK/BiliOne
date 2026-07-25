package dev.openbili.webdemo.feed

import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import dev.openbili.webdemo.UrlPolicy

/**
 * Centralises Coil 3 request construction for feed-cover images so Referer/header policy is not
 * scattered across individual card composables.
 */
object CoverImageRequestFactory {

  private const val REFERER = "https://www.bilibili.com/"

  fun request(
    rawUrl: String?,
    builder: ImageRequest.Builder,
    width: Int = 672,
    height: Int = 378,
    crop: Boolean = true,
    useOriginalSource: Boolean = false,
  ): ImageRequest {
    val imageUrl =
      if (useOriginalSource) rawUrl?.let(UrlPolicy::normalizeImageUrl)
      else optimizedCoverImageUrl(rawUrl, width, height, crop)
    // The tablet grid displays three columns. Decoding server originals (often 1920px+) makes
    // first-scroll image churn dominate the UI thread; 672x378 is sharp enough for a card.
    // These covers participate in retained Compose GraphicsLayers (glass and shared-element
    // transitions). Software bitmaps avoid RenderProxy copying hardware bitmaps back to the CPU
    // whenever such a layer is recorded; Skia uploads and caches them on first draw instead.
    return builder
      .data(imageUrl)
      .size(width, height)
      .precision(Precision.INEXACT)
      .allowHardware(false)
      .build()
  }
}

internal fun optimizedCoverImageUrl(
  rawUrl: String?,
  width: Int,
  height: Int,
  crop: Boolean,
): String? {
  val url = rawUrl?.let(UrlPolicy::normalizeImageUrl) ?: return null
  if (!url.contains("hdslb.com/")) return url
  // Some API responses already carry a small, server-cropped `@..._1c` derivative. Always rebuild
  // it from the original path so a Fit request can recover the complete poster.
  val base = url.substringBefore('?').substringBefore('@')
  val query = url.substringAfter('?', "")
  val suffix = if (crop) "@${width}w_${height}h_1c.webp" else "@${width}w.webp"
  return "$base$suffix" + if (query.isBlank()) "" else "?$query"
}
