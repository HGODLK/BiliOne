package dev.openbili.webdemo.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val videoPaletteLoadSemaphore = Semaphore(2)
private const val VIDEO_PALETTE_START_DELAY_MS = 700L

private object VideoCoverPaletteCache {
  private val values = ConcurrentHashMap<String, List<Color>>()

  fun get(url: String): List<Color>? = values[url]

  fun put(url: String, colors: List<Color>) {
    if (url.isNotBlank() && colors.isNotEmpty()) values[url] = colors
  }
}

@Composable
fun VideoCardGradient(
  coverUrl: String?,
  modifier: Modifier = Modifier,
  loadKey: String = coverUrl.orEmpty(),
  overlayStyle: Boolean = false,
  allowDynamicPalette: Boolean = true,
  dynamicPaletteAllowed: State<Boolean>? = null,
  paletteRequestWidth: Int = 672,
  paletteRequestHeight: Int = 378,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val imageLoadPolicy = LocalFeedImageLoadPolicy.current
  val normalizedUrl = coverUrl.orEmpty()
  val paletteLoadAllowed = imageLoadPolicy.permits(loadKey)
  var dominantColors by
    remember(normalizedUrl) {
      mutableStateOf(VideoCoverPaletteCache.get(normalizedUrl))
    }
  LaunchedEffect(normalizedUrl, allowDynamicPalette, paletteLoadAllowed) {
    if (
      !allowDynamicPalette ||
        !paletteLoadAllowed ||
        normalizedUrl.isBlank() ||
        dominantColors != null
    ) {
      return@LaunchedEffect
    }
    // Do not compete with the first visible cover decodes. The gradient still appears shortly
    // after the card settles. Observing scroll state from this coroutine avoids recomposing every
    // visible card when a gesture starts or ends.
    delay(VIDEO_PALETTE_START_DELAY_MS)
    dynamicPaletteAllowed?.let { allowed -> snapshotFlow { allowed.value }.first { it } }
    val colors =
      videoPaletteLoadSemaphore
      .withPermit {
        loadVideoCoverThemeColors(
          context,
          normalizedUrl,
          paletteRequestWidth,
          paletteRequestHeight,
        )
      }
    if (colors != null) {
      VideoCoverPaletteCache.put(normalizedUrl, colors)
      dynamicPaletteAllowed?.let { allowed -> snapshotFlow { allowed.value }.first { it } }
      dominantColors = colors
    }
  }

  val surface = if (overlayStyle) Color(0xFF171A1F) else MaterialTheme.colorScheme.surface
  val targetColors =
    remember(dominantColors, surface) {
      videoCardGradientColors(dominantColors.orEmpty(), surface)
    }
  val start by animateColorAsState(targetColors.first, tween(320), label = "videoCardGradientStart")
  val end by animateColorAsState(targetColors.second, tween(320), label = "videoCardGradientEnd")
  Box(modifier.background(Brush.horizontalGradient(listOf(start, end)))) { content() }
}

private suspend fun loadVideoCoverThemeColors(
  context: Context,
  coverUrl: String,
  requestWidth: Int,
  requestHeight: Int,
): List<Color>? {
  return try {
    val request =
      CoverImageRequestFactory.request(
        coverUrl,
        ImageRequest.Builder(context),
        width = requestWidth,
        height = requestHeight,
      )
    val result = context.imageLoader.execute(request)
    val bitmap = (result.image as? BitmapImage)?.bitmap ?: return null
    withContext(Dispatchers.Default) { extractVideoCoverDominantColors(bitmap) }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    null
  }
}

internal data class VideoColorBucket(
  var count: Int = 0,
  var red: Long = 0L,
  var green: Long = 0L,
  var blue: Long = 0L,
  var saturationTotal: Float = 0f,
)

internal fun extractVideoCoverDominantColors(bitmap: Bitmap): List<Color> {
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
  val stepX = (source.width / 48).coerceAtLeast(1)
  val stepY = (source.height / 27).coerceAtLeast(1)
  val buckets = HashMap<Int, VideoColorBucket>()
  runCatching {
      var y = 0
      while (y < source.height) {
        var x = 0
        while (x < source.width) {
          val pixel = source.getPixel(x, y)
          if (android.graphics.Color.alpha(pixel) >= 180) {
            val red = android.graphics.Color.red(pixel)
            val green = android.graphics.Color.green(pixel)
            val blue = android.graphics.Color.blue(pixel)
            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            val saturation = if (max == 0) 0f else (max - min) / max.toFloat()
            val brightness = max / 255f
            if (brightness in .10f..0.94f) {
              val key = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
              buckets.getOrPut(key, ::VideoColorBucket).apply {
                count++
                this.red += red
                this.green += green
                this.blue += blue
                saturationTotal += saturation
              }
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
    .sortedByDescending { bucket ->
      val averageSaturation = bucket.saturationTotal / bucket.count.coerceAtLeast(1)
      bucket.count * (1f + averageSaturation * .55f)
    }
    .forEach { bucket ->
      if (bucket.count <= 0 || selected.size >= 2) return@forEach
      val candidate =
        Color(
          (bucket.red / bucket.count).toInt(),
          (bucket.green / bucket.count).toInt(),
          (bucket.blue / bucket.count).toInt(),
        )
      if (selected.none { videoColorDistance(it, candidate) < 64f }) selected += candidate
    }
  val first = selected.firstOrNull() ?: return emptyList()
  if (selected.size < 2) selected += adjacentVideoColor(first)
  return selected.take(2)
}

internal fun videoCardGradientColors(
  dominantColors: List<Color>,
  surface: Color,
): Pair<Color, Color> {
  if (dominantColors.isEmpty()) return surface to surface
  val darkSurface = surface.luminance() < .22f
  val first = readableVideoCardColor(dominantColors.first(), surface, darkSurface)
  val secondSource = dominantColors.getOrNull(1) ?: adjacentVideoColor(dominantColors.first())
  return first to readableVideoCardColor(secondSource, surface, darkSurface)
}

internal fun videoColorDistance(first: Color, second: Color): Float {
  val red = (first.red - second.red) * 255f
  val green = (first.green - second.green) * 255f
  val blue = (first.blue - second.blue) * 255f
  return kotlin.math.sqrt(red * red + green * green + blue * blue)
}

private fun readableVideoCardColor(color: Color, surface: Color, darkTheme: Boolean): Color {
  var result = lerp(color, surface, if (darkTheme) .76f else .55f)
  if (darkTheme) {
    while (result.luminance() > .10f) result = lerp(result, surface, .22f)
    while (result.luminance() < .038f) result = lerp(result, Color.White, .05f)
  } else {
    while (result.luminance() < .30f) result = lerp(result, Color.White, .14f)
  }
  return result
}

internal fun adjacentVideoColor(color: Color): Color {
  val hsv = FloatArray(3)
  android.graphics.Color.colorToHSV(color.toArgb(), hsv)
  hsv[0] = (hsv[0] + 16f) % 360f
  hsv[1] = hsv[1].coerceIn(.22f, .78f)
  hsv[2] = hsv[2].coerceIn(.34f, .88f)
  return Color(android.graphics.Color.HSVToColor(hsv))
}
