package dev.openbili.webdemo.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import dev.openbili.webdemo.DevicePerformancePolicy
import dev.openbili.webdemo.feed.CoverImageRequestFactory
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BackgroundLuminanceProfile(
  val top: Float,
  val middle: Float,
  val bottom: Float,
)

internal data class BackgroundImageLayer(val model: Any, val incoming: Boolean)

internal fun backgroundImageLayers(
  displayedModel: Any?,
  incomingModel: Any?,
): List<BackgroundImageLayer> = buildList {
  displayedModel?.let { add(BackgroundImageLayer(it, incoming = false)) }
  incomingModel
    ?.takeIf { it != displayedModel }
    ?.let { add(BackgroundImageLayer(it, incoming = true)) }
}

/** 在播放页界面元素使用的垂直区域中采样实际解码出的背景。 */
@Composable
internal fun rememberBackgroundLuminanceProfile(model: Any?): BackgroundLuminanceProfile? {
  val context = LocalContext.current.applicationContext
  var retainedProfile by remember { mutableStateOf<BackgroundLuminanceProfile?>(null) }
  LaunchedEffect(model, context) {
    if (model == null) {
      retainedProfile = null
      return@LaunchedEffect
    }
    val resolved =
      try {
        val image =
          context.imageLoader
            .execute(
              ImageRequest.Builder(context)
                .data(model)
                .size(192, 192)
                .precision(Precision.INEXACT)
                .allowHardware(false)
                .build()
            )
            .image as? BitmapImage
        image?.bitmap?.let { bitmap ->
          withContext(Dispatchers.Default) { extractBackgroundLuminanceProfile(bitmap) }
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }
    resolved?.let { retainedProfile = it }
  }
  return if (model == null) null else retainedProfile
}

internal fun videoBackgroundForeground(
  luminance: Float?,
  darkMode: Boolean,
  fallback: Color,
): Color =
  when {
    darkMode -> Color.White
    luminance == null -> fallback
    luminance >= .52f -> Color.Black
    else -> Color.White
  }

/** 浅色页面保持模糊作品图原样；深色页面则强烈压暗它，以获得稳定的白色文本。 */
internal fun videoBackgroundScrim(
  profile: BackgroundLuminanceProfile?,
  darkMode: Boolean,
): Brush {
  if (!darkMode) return Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
  val topAlpha = (.74f - (profile?.top ?: .5f) * .14f).coerceIn(.60f, .72f)
  val middleAlpha = (.70f - (profile?.middle ?: .5f) * .12f).coerceIn(.58f, .69f)
  val bottomAlpha = (.76f - (profile?.bottom ?: .5f) * .12f).coerceIn(.63f, .74f)
  return Brush.verticalGradient(
    colorStops =
      arrayOf(
        0f to Color.Black.copy(alpha = topAlpha),
        .42f to Color.Black.copy(alpha = middleAlpha),
        1f to Color.Black.copy(alpha = bottomAlpha),
      )
  )
}

private fun extractBackgroundLuminanceProfile(bitmap: Bitmap): BackgroundLuminanceProfile {
  val sectionTotals = DoubleArray(3)
  val sectionCounts = IntArray(3)
  val stepX = (bitmap.width / 32).coerceAtLeast(1)
  val stepY = (bitmap.height / 32).coerceAtLeast(1)
  var y = stepY / 2
  while (y < bitmap.height) {
    var x = stepX / 2
    while (x < bitmap.width) {
      val pixel = bitmap.getPixel(x, y)
      if (android.graphics.Color.alpha(pixel) >= 128) {
        val red = android.graphics.Color.red(pixel) / 255.0
        val green = android.graphics.Color.green(pixel) / 255.0
        val blue = android.graphics.Color.blue(pixel) / 255.0
        val luminance = .2126 * red + .7152 * green + .0722 * blue
        val section = ((y.toFloat() / bitmap.height) * 3f).toInt().coerceIn(0, 2)
        sectionTotals[section] += luminance
        sectionCounts[section]++
      }
      x += stepX
    }
    y += stepY
  }
  fun average(index: Int): Float =
    if (sectionCounts[index] == 0) .5f
    else (sectionTotals[index] / sectionCounts[index]).toFloat().coerceIn(0f, 1f)
  return BackgroundLuminanceProfile(
    top = average(0),
    middle = average(1),
    bottom = average(2),
  )
}

/**
 * 在不应用任何实时 Compose 模糊的情况下解析页面背景。模糊变体按有界尺寸解码，
 * 在工作线程上处理一次，并从应用私有缓存中复用。
 */
@Composable
internal fun rememberStaticBackgroundModel(source: String, blurred: Boolean): Any? {
  val context = LocalContext.current.applicationContext
  var retainedBlurredModel by remember {
    mutableStateOf<Any?>(
      if (blurred && (source.startsWith("http://") || source.startsWith("https://"))) {
        StaticBackgroundImageStore.peekCached(context, source)
      } else null
    )
  }
  LaunchedEffect(source, blurred, context) {
    when {
      source.isBlank() -> retainedBlurredModel = null
      !blurred -> Unit
      else -> {
        // 解析可能涉及磁盘 I/O 或一次性位图处理。保持最后解码出的背景挂载，
        // 直到其替代者完全就绪，让浅色页面基底永远不能在播放条目之间泄漏出来。
        StaticBackgroundImageStore.resolve(context, source)?.let { resolved ->
          retainedBlurredModel = resolved
        }
      }
    }
  }
  return when {
    source.isBlank() -> null
    !blurred -> source
    else -> retainedBlurredModel
  }
}

/**
 * 让上一个已解码位图保持可见，直到下一个就绪，然后在两者之间淡入淡出。
 */
@Composable
internal fun CrossfadeBackgroundImage(
  model: Any?,
  modifier: Modifier = Modifier,
  contentScale: ContentScale = ContentScale.Crop,
  transitionMillis: Int = 300,
  onDisplayed: ((Any) -> Unit)? = null,
) {
  val context = LocalContext.current
  var displayedModel by remember { mutableStateOf<Any?>(null) }
  var incomingModel by remember { mutableStateOf<Any?>(null) }
  var decodedIncomingModel by remember { mutableStateOf<Any?>(null) }
  val incomingAlpha = remember { Animatable(0f) }

  LaunchedEffect(model) {
    if (model != null && model != displayedModel && model != incomingModel) {
      incomingAlpha.snapTo(0f)
      decodedIncomingModel = null
      incomingModel = model
    }
  }
  LaunchedEffect(decodedIncomingModel) {
    val decoded = decodedIncomingModel ?: return@LaunchedEffect
    if (decoded != incomingModel) return@LaunchedEffect
    incomingAlpha.snapTo(0f)
    incomingAlpha.animateTo(1f, tween(transitionMillis.coerceAtLeast(1)))
    displayedModel = decoded
    incomingModel = null
    decodedIncomingModel = null
    incomingAlpha.snapTo(0f)
    onDisplayed?.invoke(decoded)
  }

  Box(modifier) {
    backgroundImageLayers(displayedModel, incomingModel).forEach { layer ->
      // 把两个角色都放在同一个按键调用点。当进入中的图片完成淡入并被提升为
      // displayedModel 时，Compose 保留已解码的 AsyncImage，而不是移除它并在一帧后
      // 挂载一个相同的替代品。
      key(layer.model) {
        val request =
          remember(layer.model, context, DevicePerformancePolicy.isConstrainedImageMode) {
            if (!DevicePerformancePolicy.isConstrainedImageMode) {
              layer.model
            } else {
              val size = DevicePerformancePolicy.imageRequestSize(1920, 1080)
              ImageRequest.Builder(context)
                .data(layer.model)
                .size(size.width, size.height)
                .precision(Precision.INEXACT)
                .allowHardware(false)
                .build()
            }
          }
        AsyncImage(
          model = request,
          contentDescription = null,
          modifier =
            Modifier.fillMaxSize().graphicsLayer {
              alpha = if (layer.incoming) incomingAlpha.value else 1f
            },
          contentScale = contentScale,
          onSuccess = {
            if (layer.incoming && incomingModel == layer.model) {
              decodedIncomingModel = layer.model
            }
          },
        )
      }
    }
  }
}

private object StaticBackgroundImageStore {
  private const val VERSION = 2
  private const val MAX_EDGE = 720
  private const val BLUR_RADIUS = 18
  private const val BLUR_PASSES = 3

  fun peekCached(context: Context, source: String): File? {
    val directory = File(context.cacheDir, "static_backgrounds")
    val output = File(directory, "${sha256("$VERSION|$source")}.webp")
    return output.takeIf { it.isFile && it.length() > 0L }
  }

  suspend fun resolve(context: Context, source: String): File? =
    withContext(Dispatchers.IO) {
      try {
        val directory = File(context.cacheDir, "static_backgrounds")
        if (!directory.exists() && !directory.mkdirs()) return@withContext null
        val fingerprint = sourceFingerprint(context, source)
        val output = File(directory, "${sha256("$VERSION|$fingerprint")}.webp")
        if (output.isFile && output.length() > 0L) return@withContext output

        val request =
          if (source.startsWith("http://") || source.startsWith("https://")) {
            CoverImageRequestFactory.request(
              source,
              ImageRequest.Builder(context),
              width = MAX_EDGE,
              height = MAX_EDGE,
              crop = false,
              useOriginalSource = true,
            )
          } else {
            ImageRequest.Builder(context)
              .data(source)
              .size(MAX_EDGE, MAX_EDGE)
              .precision(Precision.INEXACT)
              .allowHardware(false)
              .build()
          }
        val loaded =
          context.imageLoader.execute(request).image as? BitmapImage ?: return@withContext null
        val bounded = loaded.bitmap.toBoundedSoftwareBitmap(MAX_EDGE) ?: return@withContext null
        val blurred = staticBoxBlur(bounded, BLUR_RADIUS, BLUR_PASSES)
        if (bounded !== loaded.bitmap && bounded !== blurred && !bounded.isRecycled)
          bounded.recycle()

        val temporary = File(directory, "${output.name}.${System.nanoTime()}.tmp")
        temporary.outputStream().buffered().use { stream ->
          @Suppress("DEPRECATION") blurred.compress(Bitmap.CompressFormat.WEBP, 88, stream)
        }
        if (blurred !== loaded.bitmap && !blurred.isRecycled) blurred.recycle()
        if (!temporary.renameTo(output)) {
          temporary.copyTo(output, overwrite = true)
          temporary.delete()
        }
        prune(directory, keep = output)
        output.takeIf { it.isFile && it.length() > 0L }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }
    }

  private fun sourceFingerprint(context: Context, source: String): String {
    val uri = runCatching { Uri.parse(source) }.getOrNull() ?: return source
    if (uri.scheme != "content") return source
    val projection =
      arrayOf(
        OpenableColumns.SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
      )
    val metadata =
      runCatching {
          context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use ""
            buildString {
              repeat(cursor.columnCount) { index ->
                append('|')
                append(runCatching { cursor.getString(index) }.getOrNull().orEmpty())
              }
            }
          }
        }
        .getOrNull()
        .orEmpty()
    return source + metadata
  }

  private fun Bitmap.toBoundedSoftwareBitmap(maxEdge: Int): Bitmap? {
    val source =
      if (config == Bitmap.Config.ARGB_8888 && isMutable) this
      else copy(Bitmap.Config.ARGB_8888, true) ?: return null
    val largest = maxOf(source.width, source.height).coerceAtLeast(1)
    if (largest <= maxEdge) return source
    val scale = maxEdge.toFloat() / largest
    val scaled =
      Bitmap.createScaledBitmap(
        source,
        (source.width * scale).toInt().coerceAtLeast(1),
        (source.height * scale).toInt().coerceAtLeast(1),
        true,
      )
    if (source !== this && source !== scaled && !source.isRecycled) source.recycle()
    return scaled
  }

  private fun staticBoxBlur(bitmap: Bitmap, radius: Int, passes: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    var input =
      IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
    var output = IntArray(input.size)
    repeat(passes.coerceAtLeast(1)) {
      horizontalBlur(input, output, width, height, radius)
      val swap = input
      input = output
      output = swap
      verticalBlur(input, output, width, height, radius)
      val verticalSwap = input
      input = output
      output = verticalSwap
    }
    return Bitmap.createBitmap(input, width, height, Bitmap.Config.ARGB_8888)
  }

  private fun horizontalBlur(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
  ) {
    val red = IntArray(width + 1)
    val green = IntArray(width + 1)
    val blue = IntArray(width + 1)
    for (y in 0 until height) {
      val row = y * width
      for (x in 0 until width) {
        val color = input[row + x]
        red[x + 1] = red[x] + android.graphics.Color.red(color)
        green[x + 1] = green[x] + android.graphics.Color.green(color)
        blue[x + 1] = blue[x] + android.graphics.Color.blue(color)
      }
      for (x in 0 until width) {
        val start = (x - radius).coerceAtLeast(0)
        val end = (x + radius + 1).coerceAtMost(width)
        val count = end - start
        output[row + x] =
          android.graphics.Color.rgb(
            (red[end] - red[start]) / count,
            (green[end] - green[start]) / count,
            (blue[end] - blue[start]) / count,
          )
      }
    }
  }

  private fun verticalBlur(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
  ) {
    val red = IntArray(height + 1)
    val green = IntArray(height + 1)
    val blue = IntArray(height + 1)
    for (x in 0 until width) {
      for (y in 0 until height) {
        val color = input[y * width + x]
        red[y + 1] = red[y] + android.graphics.Color.red(color)
        green[y + 1] = green[y] + android.graphics.Color.green(color)
        blue[y + 1] = blue[y] + android.graphics.Color.blue(color)
      }
      for (y in 0 until height) {
        val start = (y - radius).coerceAtLeast(0)
        val end = (y + radius + 1).coerceAtMost(height)
        val count = end - start
        output[y * width + x] =
          android.graphics.Color.rgb(
            (red[end] - red[start]) / count,
            (green[end] - green[start]) / count,
            (blue[end] - blue[start]) / count,
          )
      }
    }
  }

  private fun prune(directory: File, keep: File) {
    directory
      .listFiles()
      ?.filter { it != keep && it.isFile }
      ?.sortedByDescending(File::lastModified)
      ?.drop(31)
      ?.forEach(File::delete)
  }

  private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
      ""
    ) { byte ->
      "%02x".format(byte.toInt() and 0xff)
    }
}
