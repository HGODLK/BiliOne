package dev.openbili.webdemo.video

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal object CommentAvatarPaletteCache {
  private val values = LinkedHashMap<String, List<Color>>(24, .75f, true)
  private val faceLocks = ConcurrentHashMap<String, Mutex>()
  private val extractionPermits = Semaphore(2)

  @Synchronized fun get(face: String): List<Color>? = values[face]

  @Synchronized
  fun put(face: String, colors: List<Color>) {
    if (face.isBlank() || colors.isEmpty()) return
    values[face] = colors
  }

  /** 把昂贵的头像取色结果限制在活动评论视口范围内。 */
  @Synchronized
  fun retainOnly(faces: Set<String>) {
    values.keys.removeAll { it !in faces }
    faceLocks.keys.removeAll { it !in faces }
  }

  suspend fun resolve(face: String, extract: suspend () -> List<Color>): List<Color> {
    if (face.isBlank()) return emptyList()
    values[face]?.let {
      return it
    }
    return faceLocks.getOrPut(face, ::Mutex).withLock {
      values[face]?.let {
        return@withLock it
      }
      val colors = extractionPermits.withPermit { extract() }
      put(face, colors)
      colors
    }
  }
}

internal const val COMMENT_VIEWPORT_BUFFER = 6

/** 返回视口及其有界预取边距内需要加载的评论下标。 */
internal fun commentViewportWindow(
  totalCount: Int,
  firstVisibleIndex: Int,
  lastVisibleIndex: Int,
  buffer: Int = COMMENT_VIEWPORT_BUFFER,
): IntRange? {
  if (totalCount <= 0 || lastVisibleIndex < 0) return null
  val first = firstVisibleIndex.coerceIn(0, totalCount - 1)
  val last = lastVisibleIndex.coerceIn(first, totalCount - 1)
  return (first - buffer).coerceAtLeast(0)..(last + buffer).coerceAtMost(totalCount - 1)
}

internal suspend fun prefetchCommentAvatarPalettes(
  context: Context,
  comments: List<CommentItem>,
) {
  comments
    .asSequence()
    .map(CommentItem::face)
    .filter(String::isNotBlank)
    .distinct()
    .filter { CommentAvatarPaletteCache.get(it) == null }
    .toList()
    .chunked(2)
    .forEach { faces ->
      coroutineScope {
        faces.forEach { face ->
          launch {
            try {
              CommentAvatarPaletteCache.resolve(face) {
                val result =
                  context.imageLoader.execute(
                    ImageRequest.Builder(context)
                      .data(face)
                      .size(64, 64)
                      .allowHardware(false)
                      .build()
                  )
                val bitmap = (result.image as? BitmapImage)?.bitmap
                if (bitmap == null) emptyList()
                else {
                  LoadedFeedImageRegistry.markLoaded(face)
                  withContext(Dispatchers.Default) { extractAvatarDominantColors(bitmap) }
                }
              }
            } catch (cancelled: CancellationException) {
              throw cancelled
            } catch (_: Exception) {
              // 后续滚动窗口可以重试失败的头像请求。
            }
          }
        }
      }
    }
}

internal data class AvatarColorBucket(
  var count: Int = 0,
  var red: Long = 0L,
  var green: Long = 0L,
  var blue: Long = 0L,
)

internal fun extractAvatarDominantColors(bitmap: Bitmap): List<Color> {
  val source =
    runCatching {
        if (
          android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        )
          bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        else bitmap
      }
      .getOrDefault(bitmap)
  // 16x16 采样对双色卡片渐变在视觉上无差别，并避免新评论行进入视口时成千上万次
  // Bitmap#getPixel 调用。
  val stepX = (source.width / 16).coerceAtLeast(1)
  val stepY = (source.height / 16).coerceAtLeast(1)
  val buckets = HashMap<Int, AvatarColorBucket>()
  runCatching {
      var y = 0
      while (y < source.height) {
        var x = 0
        while (x < source.width) {
          val pixel = source.getPixel(x, y)
          if (android.graphics.Color.alpha(pixel) >= 160) {
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
            buckets.getOrPut(key, ::AvatarColorBucket).apply {
              count++
              this.red += red
              this.green += green
              this.blue += blue
            }
          }
          x += stepX
        }
        y += stepY
      }
    }
    .getOrElse {
      return emptyList()
    }

  val selected = mutableListOf<Color>()
  buckets.values
    .sortedByDescending { it.count }
    .forEach { bucket ->
      if (bucket.count <= 0 || selected.size >= 2) return@forEach
      val candidate =
        Color(
          (bucket.red / bucket.count).toInt(),
          (bucket.green / bucket.count).toInt(),
          (bucket.blue / bucket.count).toInt(),
        )
      if (selected.none { avatarColorDistance(it, candidate) < 72f }) selected += candidate
    }
  val base = selected.firstOrNull() ?: return emptyList()
  if (selected.size < 2) selected += lerp(base, Color.White, .22f)
  return selected.take(2)
}

internal fun avatarColorDistance(first: Color, second: Color): Float {
  val red = (first.red - second.red) * 255f
  val green = (first.green - second.green) * 255f
  val blue = (first.blue - second.blue) * 255f
  return kotlin.math.sqrt(red * red + green * green + blue * blue)
}

internal fun readableCommentCardColor(color: Color, surface: Color, darkTheme: Boolean): Color {
  // 保持头像颜色可辨认，但给深色表面更多视觉权重，避免亮头像把整张评论卡变成
  // 发光面板。
  var result = lerp(color, surface, if (darkTheme) .76f else .55f)
  if (darkTheme) {
    while (result.luminance() > .10f) result = lerp(result, surface, .22f)
    while (result.luminance() < .038f) result = lerp(result, Color.White, .05f)
  } else {
    while (result.luminance() < .30f) result = lerp(result, Color.White, .14f)
  }
  return result
}
