package dev.openbili.webdemo

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 对旧式或小堆内存设备应用保守的图片预算。
 *
 * 一些面向控制器或旧式的设备暴露的每进程堆远小于其总 RAM 所暗示的大小。把该策略与
 * 屏幕布局分开，可以在图片解码、缓存和转场保留都处于该堆预算之内的同时，保留正常
 * 的界面。
 */
internal object DevicePerformancePolicy {
  private const val CONSTRAINED_MEMORY_CLASS_MB = 256
  private const val LEGACY_MAX_IMAGE_WIDTH = 960
  private const val LEGACY_MAX_IMAGE_HEIGHT = 540
  private const val CONSTRAINED_IMAGE_MEMORY_CACHE_BYTES = 8L * 1024 * 1024
  private const val DEFAULT_DANMAKU_TEXT_CACHE_BYTES = 24L * 1024 * 1024
  private const val CONSTRAINED_DANMAKU_TEXT_CACHE_BYTES = 8L * 1024 * 1024
  private const val DEFAULT_DANMAKU_IMAGE_CACHE_BYTES = 16L * 1024 * 1024
  private const val CONSTRAINED_DANMAKU_IMAGE_CACHE_BYTES = 4L * 1024 * 1024

  @Volatile private var constrainedImageMode = false

  val isConstrainedImageMode: Boolean
    get() = constrainedImageMode

  /** Coil 和弹幕栅格使用同一份进程堆预算，避免 256 MiB 设备同时保留多份位图。 */
  val imageMemoryCacheBytes: Long
    get() =
      if (constrainedImageMode) CONSTRAINED_IMAGE_MEMORY_CACHE_BYTES
      else 32L * 1024 * 1024

  val danmakuTextCacheBytes: Long
    get() =
      if (constrainedImageMode) CONSTRAINED_DANMAKU_TEXT_CACHE_BYTES
      else DEFAULT_DANMAKU_TEXT_CACHE_BYTES

  val danmakuImageCacheBytes: Long
    get() =
      if (constrainedImageMode) CONSTRAINED_DANMAKU_IMAGE_CACHE_BYTES
      else DEFAULT_DANMAKU_IMAGE_CACHE_BYTES

  fun configure(context: Context) {
    val memoryClass =
      (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.memoryClass
        ?: Int.MAX_VALUE
    constrainedImageMode =
      Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || memoryClass <= CONSTRAINED_MEMORY_CLASS_MB
  }

  fun imageRequestSize(width: Int, height: Int): ImageRequestSize =
    constrainedImageRequestSize(width, height, constrainedImageMode)

  internal fun constrainedImageRequestSize(
    width: Int,
    height: Int,
    constrained: Boolean,
  ): ImageRequestSize {
    require(width > 0 && height > 0) { "Image size must be positive." }
    if (!constrained) return ImageRequestSize(width, height)

    val scale =
      min(
        1f,
        min(
          LEGACY_MAX_IMAGE_WIDTH.toFloat() / width,
          LEGACY_MAX_IMAGE_HEIGHT.toFloat() / height,
        ),
      )
    return ImageRequestSize(
      width = (width * scale).roundToInt().coerceAtLeast(1),
      height = (height * scale).roundToInt().coerceAtLeast(1),
    )
  }
}

internal data class ImageRequestSize(
  val width: Int,
  val height: Int,
)
