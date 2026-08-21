package dev.openbili.webdemo.feed

/**
 * 信息流卡片与视频页占位共用的封面组件。
 */

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.compose.AsyncImage
import dev.openbili.webdemo.ui.VideoShapeTokens
import java.lang.ref.WeakReference
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 记住本进程内已完成的图片请求：快速滑动可直接复用（Coil 通常能从内存命中），
 * 真正的新图则保持暂停。
 */
internal object LoadedFeedImageRegistry {
  private const val MAX_ENTRIES = 512
  private const val DEFAULT_MAX_RETAINED_BITMAPS = 8
  private const val CONSTRAINED_MAX_RETAINED_BITMAPS = 2

  @Volatile private var maxRetainedBitmaps = DEFAULT_MAX_RETAINED_BITMAPS

  private data class Entry(
    val bitmap: WeakReference<Bitmap>?,
    val cropped: Boolean,
  )

  private val entries = LinkedHashMap<String, Entry>(MAX_ENTRIES, .75f, true)
  private val retainedBitmaps =
    LinkedHashMap<String, Bitmap>(DEFAULT_MAX_RETAINED_BITMAPS, .75f, true)
  private val bitmapWaiters = ConcurrentHashMap<String, MutableSet<CompletableDeferred<Bitmap>>>()

  fun configure(constrained: Boolean) {
    maxRetainedBitmaps =
      if (constrained) CONSTRAINED_MAX_RETAINED_BITMAPS else DEFAULT_MAX_RETAINED_BITMAPS
  }

  @Synchronized fun contains(url: String?): Boolean = !url.isNullOrBlank() && entries[url] != null

  @Synchronized
  fun bitmap(url: String?, requireUncropped: Boolean = false): Bitmap? {
    if (url.isNullOrBlank()) return null
    val entry = entries[url] ?: return null
    if (requireUncropped && entry.cropped) return null
    return retainedBitmaps[url] ?: entry.bitmap?.get()
  }

  @Synchronized
  fun markLoaded(
    url: String?,
    bitmap: Bitmap? = null,
    cropped: Boolean = true,
    retainBitmap: Boolean = false,
  ) {
    if (url.isNullOrBlank()) return
    val retainedBitmap = bitmap?.let(::WeakReference) ?: entries[url]?.bitmap
    entries[url] = Entry(retainedBitmap, cropped)
    if (retainBitmap && bitmap != null) {
      retainedBitmaps[url] = bitmap
      while (retainedBitmaps.size > maxRetainedBitmaps) {
        retainedBitmaps.remove(retainedBitmaps.keys.first())
      }
    }
    bitmap?.let { loaded ->
      bitmapWaiters.remove(url)?.forEach { waiter -> waiter.complete(loaded) }
    }
    bitmap?.let { PlaybackCoverRegistry.onLoaded(url, it) }
    while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
  }

  suspend fun awaitBitmap(
    url: String?,
    timeoutMs: Long,
    requireUncropped: Boolean = false,
  ): Bitmap? {
    if (url.isNullOrBlank()) return null
    bitmap(url, requireUncropped = requireUncropped)?.let {
      return it
    }
    val waiter = CompletableDeferred<Bitmap>()
    bitmapWaiters.computeIfAbsent(url) { ConcurrentHashMap.newKeySet() }.add(waiter)
    bitmap(url, requireUncropped = requireUncropped)?.let {
      waiter.complete(it)
    }
    return try {
      val loaded = withTimeoutOrNull(timeoutMs) { waiter.await() }
      if (requireUncropped) bitmap(url, requireUncropped = true) else loaded
    } finally {
      bitmapWaiters.computeIfPresent(url) { _, waiters ->
        waiters.remove(waiter)
        waiters.takeIf { it.isNotEmpty() }
      }
    }
  }
}

/** 转场外壳使用的全局闸门：在动画关键帧内阻止新的图片工作。 */
val LocalCoverImageLoadingEnabled = compositionLocalOf { true }

/** 只让最近请求过的播放结束封面在子页面间保持强可达。 */
internal object PlaybackCoverRegistry {
  private const val DEFAULT_MAX_ENTRIES = 8
  private const val CONSTRAINED_MAX_ENTRIES = 2
  @Volatile private var maxEntries = DEFAULT_MAX_ENTRIES
  private val requested = LinkedHashSet<String>()
  private val bitmaps = LinkedHashMap<String, Bitmap>(DEFAULT_MAX_ENTRIES, .75f, true)

  fun configure(constrained: Boolean) {
    maxEntries = if (constrained) CONSTRAINED_MAX_ENTRIES else DEFAULT_MAX_ENTRIES
  }

  fun requestRetention(url: String?) {
    if (url.isNullOrBlank()) return
    val cached = LoadedFeedImageRegistry.bitmap(url)
    synchronized(this) {
      requested += url
      cached?.let { bitmaps[url] = it }
      while (requested.size > maxEntries) requested.remove(requested.first())
      while (bitmaps.size > maxEntries) bitmaps.remove(bitmaps.keys.first())
    }
  }

  @Synchronized fun bitmap(url: String?): Bitmap? = if (url.isNullOrBlank()) null else bitmaps[url]

  @Synchronized
  fun onLoaded(url: String, bitmap: Bitmap) {
    if (url !in requested) return
    bitmaps[url] = bitmap
    while (bitmaps.size > maxEntries) bitmaps.remove(bitmaps.keys.first())
  }
}

/**
 * 信息流卡片与视频页占位共用的封面组件。所有请求都走 [CoverImageRequestFactory]，
 * 保证 Referer/请求头策略统一。
 */
@Composable
fun CoverImage(
  coverUrl: String?,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
  shape: Shape = VideoShapeTokens.Player,
  enforceAspectRatio: Boolean = true,
  requestWidth: Int = 672,
  requestHeight: Int = 378,
  loadKey: String = coverUrl.orEmpty(),
  bitmapCacheKey: String = coverUrl.orEmpty(),
  useOriginalSource: Boolean = false,
  alwaysLoad: Boolean = false,
  loadingEnabled: Boolean = true,
  retainBitmap: Boolean = false,
  onBitmapLoaded: (() -> Unit)? = null,
  placeholderColor: androidx.compose.ui.graphics.Color? = null,
  fadeIn: Boolean = true,
  contentScale: ContentScale = ContentScale.Crop,
) {
  val context = LocalContext.current
  val loadPolicy = LocalFeedImageLoadPolicy.current
  val globalLoadingEnabled = LocalCoverImageLoadingEnabled.current
  val previouslyLoaded =
    remember(bitmapCacheKey) { LoadedFeedImageRegistry.contains(bitmapCacheKey) }
  val requestProducesCroppedBitmap =
    coverImageRequestProducesCroppedBitmap(
      crop = contentScale == ContentScale.Crop,
      useOriginalSource = useOriginalSource,
    )
  val retainedBitmap =
    remember(coverUrl, bitmapCacheKey, contentScale, useOriginalSource) {
      if (contentScale == ContentScale.Crop && !useOriginalSource) {
        coverUrl?.takeIf { bitmapCacheKey == coverUrl }?.let(PlaybackCoverRegistry::bitmap)
          ?: LoadedFeedImageRegistry.bitmap(bitmapCacheKey)
      } else {
        LoadedFeedImageRegistry.bitmap(bitmapCacheKey, requireUncropped = true)
      }
    }
  var displayed by remember(coverUrl) { mutableStateOf(retainedBitmap != null) }
  val requestPermitted =
    displayed ||
      (loadingEnabled &&
        globalLoadingEnabled &&
        (alwaysLoad || previouslyLoaded || loadPolicy.permits(loadKey)))
  val imageAlpha by
    animateFloatAsState(
      targetValue = if (!fadeIn || displayed) 1f else 0f,
      animationSpec = tween(180),
      label = "coverImageAlpha",
    )
  val model =
    remember(
      coverUrl,
      requestWidth,
      requestHeight,
      requestPermitted,
      contentScale,
      useOriginalSource,
    ) {
      if (!requestPermitted || retainedBitmap != null) return@remember null
      CoverImageRequestFactory.request(
        coverUrl,
        coil3.request.ImageRequest.Builder(context),
        width = requestWidth,
        height = requestHeight,
        crop = contentScale == ContentScale.Crop,
        useOriginalSource = useOriginalSource,
      )
    }

  Box(
    modifier =
      modifier
        .then(if (enforceAspectRatio) Modifier.aspectRatio(16f / 9f) else Modifier)
        .clip(shape)
        .background(placeholderColor ?: MaterialTheme.colorScheme.surfaceVariant)
  ) {
    if (retainedBitmap != null) {
      onBitmapLoaded?.let { callback ->
        androidx.compose.runtime.LaunchedEffect(retainedBitmap, callback) {
          if (retainBitmap) {
            LoadedFeedImageRegistry.markLoaded(
              bitmapCacheKey,
              retainedBitmap,
              cropped = requestProducesCroppedBitmap,
              retainBitmap = true,
            )
          }
          callback()
        }
      }
      Image(
        bitmap = retainedBitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = Modifier.matchParentSize(),
        contentScale = contentScale,
      )
    } else {
      AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = Modifier.matchParentSize().graphicsLayer { alpha = imageAlpha },
        contentScale = contentScale,
        onSuccess = { state ->
          displayed = true
          LoadedFeedImageRegistry.markLoaded(
            bitmapCacheKey,
            (state.result.image as? BitmapImage)?.bitmap,
            cropped = requestProducesCroppedBitmap,
            retainBitmap = retainBitmap,
          )
          onBitmapLoaded?.invoke()
        },
      )
    }
  }
}
