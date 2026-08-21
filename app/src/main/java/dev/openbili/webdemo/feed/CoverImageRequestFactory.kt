package dev.openbili.webdemo.feed

import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import dev.openbili.webdemo.DevicePerformancePolicy
import dev.openbili.webdemo.UrlPolicy

/**
 * 集中构造 Coil 3 的封面图片请求，让 Referer/请求头策略不散落在各个卡片组件里。
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
    val requestedSize = DevicePerformancePolicy.imageRequestSize(width, height)
    val imageUrl =
      if (useOriginalSource) rawUrl?.let(UrlPolicy::normalizeImageUrl)
      else optimizedCoverImageUrl(rawUrl, requestedSize.width, requestedSize.height, crop)
    // 平板网格是三列：解码服务端原图（常为 1920px+）会让首屏滚动的图片抖动占满
    // UI 线程；672x378 对一张卡片足够清晰。这些封面还参与保留的 Compose
    // GraphicsLayer（毛玻璃与共享元素转场）：软解位图可避免 RenderProxy 在录制这些
    // 图层时把硬件位图拷回 CPU，改由 Skia 在首次绘制时上传并缓存。
    return builder
      .data(imageUrl)
      .size(requestedSize.width, requestedSize.height)
      .precision(Precision.INEXACT)
      .allowHardware(false)
      .build()
  }
}

/**
 * `ContentScale.Crop` 只是绘制时裁切；只有走服务端派生图请求时，解码后的 Bitmap
 * 才真的缺少原图边缘。共享转场需要依赖这个区分来保留完整海报。
 */
internal fun coverImageRequestProducesCroppedBitmap(
  crop: Boolean,
  useOriginalSource: Boolean,
): Boolean = crop && !useOriginalSource

internal fun optimizedCoverImageUrl(
  rawUrl: String?,
  width: Int,
  height: Int,
  crop: Boolean,
): String? {
  val url = rawUrl?.let(UrlPolicy::normalizeImageUrl) ?: return null
  if (!url.contains("hdslb.com/")) return url
  // 部分接口响应已带服务端裁切的小图派生 `@..._1c`：始终从原始路径重建它，让
  // Fit 请求能恢复完整海报。
  val base = url.substringBefore('?').substringBefore('@')
  val query = url.substringAfter('?', "")
  val suffix = if (crop) "@${width}w_${height}h_1c.webp" else "@${width}w.webp"
  return "$base$suffix" + if (query.isBlank()) "" else "?$query"
}
