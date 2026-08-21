package dev.openbili.webdemo.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.LocalFeedImageLoadPolicy
import dev.openbili.webdemo.feed.LocalLimitImageLoadingSpeed
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val videoPaletteLoadSemaphore = Semaphore(2)
private val priorityVideoPaletteLoadSemaphore = Semaphore(4)
private const val VIDEO_PALETTE_START_DELAY_MS = 700L

internal data class VideoCardContentColors(
  val primary: Color,
  val secondary: Color,
)

internal val LocalVideoCardContentColors = staticCompositionLocalOf {
  VideoCardContentColors(primary = Color.Black, secondary = Color.Black)
}

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
  backgroundAlpha: Float = 1f,
  allowDynamicPalette: Boolean = true,
  dynamicPaletteAllowed: State<Boolean>? = null,
  prioritizePaletteLoad: Boolean = false,
  paletteRequestWidth: Int = 672,
  paletteRequestHeight: Int = 378,
  /** 播放器画面外背景也复用本组件，但不应受卡片外观开关影响。 */
  useColorfulCardsPreference: Boolean = true,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val colorfulCardsEnabled =
    !useColorfulCardsPreference || LocalColorfulCardsEnabled.current
  val imageLoadPolicy = LocalFeedImageLoadPolicy.current
  val limitLoadingSpeed = LocalLimitImageLoadingSpeed.current
  val normalizedUrl = coverUrl.orEmpty()
  val paletteLoadAllowed = imageLoadPolicy.permits(loadKey)
  var dominantColors by
    remember(normalizedUrl) {
      mutableStateOf(VideoCoverPaletteCache.get(normalizedUrl))
    }
  LaunchedEffect(
    normalizedUrl,
    allowDynamicPalette,
    colorfulCardsEnabled,
    paletteLoadAllowed,
    limitLoadingSpeed,
  ) {
    if (
      !colorfulCardsEnabled ||
        !allowDynamicPalette ||
        !paletteLoadAllowed ||
        normalizedUrl.isBlank() ||
        dominantColors != null
    ) {
      return@LaunchedEffect
    }
    if (limitLoadingSpeed) {
      // 可选的限流器为较慢的设备保留旧的保守调度。
      delay(VIDEO_PALETTE_START_DELAY_MS)
      dynamicPaletteAllowed?.let { allowed -> snapshotFlow { allowed.value }.first { it } }
    }
    // 优先使用 CoverImage 已解码出的确切位图。这同步了视觉展示，
    // 并避免在正常、不受限的路径上做第二次 Coil 解码。
    val sharedBitmap =
      LoadedFeedImageRegistry.bitmap(normalizedUrl)
        ?: if (limitLoadingSpeed) {
          LoadedFeedImageRegistry.awaitBitmap(normalizedUrl, timeoutMs = 180L)
        } else null
    val paletteSemaphore =
      if (prioritizePaletteLoad && !limitLoadingSpeed) priorityVideoPaletteLoadSemaphore
      else videoPaletteLoadSemaphore
    val colors = paletteSemaphore.withPermit {
      sharedBitmap?.let { bitmap ->
        withContext(Dispatchers.Default) { extractVideoCoverDominantColors(bitmap) }
      }
        ?: loadVideoCoverThemeColors(
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
    remember(dominantColors, surface, colorfulCardsEnabled) {
      videoCardGradientColors(
        if (colorfulCardsEnabled) dominantColors.orEmpty() else emptyList(),
        surface,
      )
    }
  val paletteAnimationMillis = if (limitLoadingSpeed) 320 else 180
  val start by
    animateColorAsState(
      targetColors.first,
      tween(paletteAnimationMillis),
      label = "videoCardGradientStart",
    )
  val end by
    animateColorAsState(
      targetColors.second,
      tween(paletteAnimationMillis),
      label = "videoCardGradientEnd",
    )
  val contentColors = videoCardContentColors(start, end)
  Box(
    modifier.background(
      Brush.horizontalGradient(
        listOf(
          start.copy(alpha = backgroundAlpha.coerceIn(0f, 1f)),
          end.copy(alpha = backgroundAlpha.coerceIn(0f, 1f)),
        )
      )
    )
  ) {
    CompositionLocalProvider(
      LocalContentColor provides contentColors.primary,
      LocalVideoCardContentColors provides contentColors,
    ) {
      content()
    }
  }
}

internal fun videoCardContentColors(start: Color, end: Color): VideoCardContentColors {
  val middle = lerp(start, end, .5f)
  val backgrounds = listOf(start, middle, end)
  fun minimumContrast(foreground: Color): Float = backgrounds.minOf { background ->
    videoCardContrastRatio(foreground, background)
  }

  val primary =
    if (minimumContrast(Color.Black) >= minimumContrast(Color.White)) Color.Black else Color.White
  val secondaryCandidate = lerp(primary, middle, .12f)
  val secondary = if (minimumContrast(secondaryCandidate) >= 4.5f) secondaryCandidate else primary
  return VideoCardContentColors(primary = primary, secondary = secondary)
}

internal fun videoCardContrastRatio(first: Color, second: Color): Float {
  val firstLuminance = first.luminance()
  val secondLuminance = second.luminance()
  val lighter = maxOf(firstLuminance, secondLuminance)
  val darker = minOf(firstLuminance, secondLuminance)
  return (lighter + .05f) / (darker + .05f)
}

/** 为非卡片环境作品图加载视频卡片使用的相同两个主封面颜色。 */
@Composable
internal fun rememberVideoCoverThemeColors(coverUrl: String): List<Color> {
  val context = LocalContext.current
  var colors by
    remember(coverUrl) { mutableStateOf(VideoCoverPaletteCache.get(coverUrl).orEmpty()) }
  LaunchedEffect(coverUrl) {
    if (coverUrl.isBlank() || colors.isNotEmpty()) return@LaunchedEffect
    val sharedBitmap = LoadedFeedImageRegistry.bitmap(coverUrl)
    val loaded =
      sharedBitmap?.let { bitmap ->
        withContext(Dispatchers.Default) { extractVideoCoverDominantColors(bitmap) }
      } ?: loadVideoCoverThemeColors(context, coverUrl, 672, 378)
    if (!loaded.isNullOrEmpty()) {
      VideoCoverPaletteCache.put(coverUrl, loaded)
      colors = loaded
    }
  }
  return colors
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
  var result = lerp(color, surface, if (darkTheme) .68f else .44f)
  if (darkTheme) {
    while (result.luminance() > .13f) result = lerp(result, surface, .18f)
    while (result.luminance() < .038f) result = lerp(result, Color.White, .05f)
  } else {
    while (result.luminance() < .26f) result = lerp(result, Color.White, .12f)
  }
  return result
}

internal fun adjacentVideoColor(color: Color): Color {
  val hsv = FloatArray(3)
  android.graphics.Color.colorToHSV(color.toArgb(), hsv)
  hsv[0] = (hsv[0] + 24f) % 360f
  hsv[1] = hsv[1].coerceIn(.28f, .82f)
  hsv[2] = hsv[2].coerceIn(.34f, .88f)
  return Color(android.graphics.Color.HSVToColor(hsv))
}
