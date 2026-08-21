package dev.openbili.webdemo.video

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerSubtitleState
import dev.openbili.webdemo.WebViewConfigurator
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.BiliEmote
import dev.openbili.webdemo.api.BiliEmotePackage
import dev.openbili.webdemo.api.BiliHttpClient
import dev.openbili.webdemo.api.CommentImage
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.CommentNavigationTarget
import dev.openbili.webdemo.api.CommentSort
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.FavoriteFolder
import dev.openbili.webdemo.api.FollowingGroup
import dev.openbili.webdemo.api.MentionSuggestion
import dev.openbili.webdemo.api.PlayUrlData
import dev.openbili.webdemo.api.PremiumAudioMode
import dev.openbili.webdemo.api.VideoEngagement
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.api.remainingVideoCoins
import dev.openbili.webdemo.api.videoCoinLimit
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.PlaybackCoverRegistry
import dev.openbili.webdemo.offline.OfflineCacheChooserDialog
import dev.openbili.webdemo.offline.OfflineMediaKind
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.offline.OfflineMediaRequest
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.subtitleStateForMedia
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.CONTROL_DOUBLE_CONFIRM_TIMEOUT_MS
import dev.openbili.webdemo.ui.CONTROL_SEEK_STEP_MS
import dev.openbili.webdemo.ui.ControlVideoMode
import dev.openbili.webdemo.ui.ControlVideoSurfaceAction
import dev.openbili.webdemo.ui.CrossfadeBackgroundImage
import dev.openbili.webdemo.ui.LocalControlMode
import dev.openbili.webdemo.ui.LocalVideoCardContentColors
import dev.openbili.webdemo.ui.SessionPhase
import dev.openbili.webdemo.ui.StableBoundsTracker
import dev.openbili.webdemo.ui.TransitionPreparationBarrier
import dev.openbili.webdemo.ui.TransitionPreparationResult
import dev.openbili.webdemo.ui.TransitionReadySignal
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.controlFocusOutline
import dev.openbili.webdemo.ui.navigationBringIntoViewTarget
import dev.openbili.webdemo.ui.rememberBackgroundLuminanceProfile
import dev.openbili.webdemo.ui.rememberNavigationBringIntoViewRequester
import dev.openbili.webdemo.ui.rememberStaticBackgroundModel
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.ui.resolveControlVideoSurfaceAction
import dev.openbili.webdemo.ui.videoBackgroundForeground
import dev.openbili.webdemo.ui.videoBackgroundScrim
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FullscreenLockButton(
  locked: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  androidx.compose.material3.IconButton(
    onClick = onClick,
    modifier = modifier.size(48.dp),
  ) {
    Icon(
      imageVector = if (locked) Icons.Default.Lock else Icons.Default.LockOpen,
      contentDescription = if (locked) "解锁播放器控制" else "锁定播放器控制",
      modifier = Modifier.size(24.dp),
      tint = Color.White,
    )
  }
}

internal class CommentImagePreviewSession(
  val image: CommentImage,
  val sourceBounds: Rect,
) {
  val progress = Animatable(0f)
  val preparation =
    TransitionPreparationBarrier(
      setOf(
        TransitionReadySignal.SOURCE_BOUNDS,
        TransitionReadySignal.IMAGE_READY,
        TransitionReadySignal.TARGET_MOUNTED,
        TransitionReadySignal.TARGET_BOUNDS_STABLE,
      )
    )
  val targetBoundsTracker = StableBoundsTracker()
  var phase by mutableStateOf(SessionPhase.PREPARING)
  var preparationTimedOut by mutableStateOf(false)

  init {
    preparation.markReady(TransitionReadySignal.SOURCE_BOUNDS)
  }
}

internal fun Modifier.floatingPlayerLayout(
  progress: Float,
  sourceBounds: Rect,
  targetInsetPx: Int,
): Modifier = layout { measurable, constraints ->
  val parentWidth = constraints.maxWidth.coerceAtLeast(1)
  val parentHeight = constraints.maxHeight.coerceAtLeast(1)
  val targetLeft = targetInsetPx.coerceAtMost(parentWidth / 2)
  val targetTop = targetInsetPx.coerceAtMost(parentHeight / 2)
  val targetWidth = (parentWidth - targetLeft * 2).coerceAtLeast(1)
  val targetHeight = (parentHeight - targetTop * 2).coerceAtLeast(1)
  val hasSource = sourceBounds.width > 0f && sourceBounds.height > 0f
  val animationProgress = progress.coerceIn(0f, 1f)
  val startLeft = if (hasSource) sourceBounds.left else targetLeft.toFloat()
  val startTop = if (hasSource) sourceBounds.top else targetTop.toFloat()
  val startWidth = if (hasSource) sourceBounds.width else targetWidth.toFloat()
  val startHeight = if (hasSource) sourceBounds.height else targetHeight.toFloat()
  val left = (startLeft + (targetLeft - startLeft) * animationProgress).roundToInt()
  val top = (startTop + (targetTop - startTop) * animationProgress).roundToInt()
  val width =
    (startWidth + (targetWidth - startWidth) * animationProgress)
      .roundToInt()
      .coerceIn(1, parentWidth)
  val height =
    (startHeight + (targetHeight - startHeight) * animationProgress)
      .roundToInt()
      .coerceIn(1, parentHeight)
  val placeable = measurable.measure(Constraints.fixed(width, height))
  layout(parentWidth, parentHeight) { placeable.place(left, top) }
}

internal fun commentImageStartScale(
  sourceBounds: Rect,
  targetWidth: Float,
  targetHeight: Float,
): Float {
  if (
    sourceBounds.width <= 1f || sourceBounds.height <= 1f || targetWidth <= 1f || targetHeight <= 1f
  )
    return .92f
  return minOf(sourceBounds.width / targetWidth, sourceBounds.height / targetHeight)
    .coerceIn(.05f, 1f)
}

internal fun commentImagePanLimit(
  imageSize: Float,
  viewportSize: Float,
  scale: Float,
): Float = maxOf(0f, (imageSize * scale - viewportSize) / 2f)

internal fun isLongCommentImage(width: Int, height: Int): Boolean =
  width > 0 && height.toLong() * 2L >= width.toLong() * 5L

internal data class CommentImageThumbnailSpec(
  val url: String,
  val widthPx: Int,
  val heightPx: Int,
)

internal const val COMMENT_IMAGE_THUMBNAIL_MAX_EDGE_PX = 480

internal fun commentImageThumbnailSpec(
  rawUrl: String,
  imageWidth: Int,
  imageHeight: Int,
  targetWidthPx: Int,
  targetHeightPx: Int,
  crop: Boolean,
): CommentImageThumbnailSpec {
  val boundedTargetWidth = targetWidthPx.coerceIn(1, COMMENT_IMAGE_THUMBNAIL_MAX_EDGE_PX)
  val boundedTargetHeight = targetHeightPx.coerceIn(1, COMMENT_IMAGE_THUMBNAIL_MAX_EDGE_PX)
  val validImageSize = imageWidth > 0 && imageHeight > 0
  val width =
    if (!crop && validImageSize) {
      minOf(
          boundedTargetWidth.toFloat(),
          boundedTargetHeight.toFloat() * imageWidth / imageHeight,
        )
        .toInt()
        .coerceAtLeast(1)
    } else {
      boundedTargetWidth
    }
  val height =
    if (!crop && validImageSize) {
      (width.toFloat() * imageHeight / imageWidth).toInt().coerceIn(1, boundedTargetHeight)
    } else {
      boundedTargetHeight
    }
  val originalUrl = fullResolutionCommentImageUrl(rawUrl)
  val host = runCatching { java.net.URI(originalUrl).host.orEmpty() }.getOrDefault("")
  if (host != "hdslb.com" && !host.endsWith(".hdslb.com")) {
    return CommentImageThumbnailSpec(originalUrl, width, height)
  }
  val base = originalUrl.substringBefore('?')
  val query = originalUrl.substringAfter('?', "")
  val suffix = if (crop) "@${width}w_${height}h_1c.webp" else "@${width}w.webp"
  val url = base + suffix + if (query.isBlank()) "" else "?$query"
  return CommentImageThumbnailSpec(url, width, height)
}

internal fun fullResolutionCommentImageUrl(url: String): String {
  val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
  if (host != "hdslb.com" && !host.endsWith(".hdslb.com")) return url
  val base = url.substringBefore('?').substringBefore('@')
  val query = url.substringAfter('?', "")
  return base + if (query.isBlank()) "" else "?$query"
}

internal fun longCommentImageHtml(imageUrl: String): String {
  val escapedUrl =
    buildString(imageUrl.length) {
      imageUrl.forEach { character ->
        append(
          when (character) {
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&#39;"
            '<' -> "&lt;"
            '>' -> "&gt;"
            else -> character
          }
        )
      }
    }
  return """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
        <style>
          html, body {
            width: 100%;
            margin: 0;
            padding: 0;
            overflow-x: hidden;
            background: transparent;
          }
          img {
            display: block;
            width: 100%;
            height: auto;
            margin: 0;
            padding: 0;
          }
        </style>
      </head>
      <body><img src="$escapedUrl"></body>
    </html>
  """
    .trimIndent()
}

internal data class CommentImagePreviewLayout(
  val widthPx: Float,
  val heightPx: Float,
  val verticallyScrollable: Boolean,
)

internal fun commentImagePreviewLayout(
  viewportWidth: Float,
  viewportHeight: Float,
  imageWidth: Int,
  imageHeight: Int,
  wideViewport: Boolean,
): CommentImagePreviewLayout {
  val safeViewportWidth = viewportWidth.coerceAtLeast(1f)
  val safeViewportHeight = viewportHeight.coerceAtLeast(1f)
  val ratio = if (imageWidth > 0 && imageHeight > 0) imageWidth.toFloat() / imageHeight else 1.5f
  if (isLongCommentImage(imageWidth, imageHeight)) {
    return CommentImagePreviewLayout(
      widthPx = safeViewportWidth * if (wideViewport) (2f / 5f) else .88f,
      heightPx = safeViewportHeight * .86f,
      verticallyScrollable = true,
    )
  }
  val maxTargetWidth = safeViewportWidth * .9f
  val maxTargetHeight = safeViewportHeight * .86f
  val targetWidth = minOf(maxTargetWidth, maxTargetHeight * ratio).coerceAtLeast(1f)
  return CommentImagePreviewLayout(
    widthPx = targetWidth,
    heightPx = (targetWidth / ratio).coerceAtMost(maxTargetHeight).coerceAtLeast(1f),
    verticallyScrollable = false,
  )
}

@Composable
internal fun CommentImagePreviewOverlay(
  session: CommentImagePreviewSession,
  rootBounds: Rect,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val previewImageUrl = remember(session.image) { fullResolutionCommentImageUrl(session.image.url) }
  var zoomScale by remember(session) { mutableStateOf(1f) }
  var panOffset by remember(session) { mutableStateOf(Offset.Zero) }
  var confirmSave by remember(session) { mutableStateOf(false) }
  var saving by remember(session) { mutableStateOf(false) }
  fun saveImage() {
    saving = true
    scope.launch {
      val result = savePreviewImageToGallery(context, previewImageUrl)
      saving = false
      confirmSave = false
      val message =
        when (result) {
          ImageSaveResult.SAVED -> "已经保存到相册啦 (´▽`ʃ♡ƪ)"
          ImageSaveResult.PERMISSION_REQUIRED -> "想保存这张图，需要先给我相册权限哦 (´；ω；`)"
          ImageSaveResult.FAILED -> "这张图暂时没能保存下来，请稍后再试 (｡•́︿•̀｡)"
        }
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }
  val legacyStoragePermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        saveImage()
      } else {
        confirmSave = false
        Toast.makeText(
            context,
            "想保存这张图，需要先给我相册权限哦 (´；ω；`)",
            Toast.LENGTH_SHORT,
          )
          .show()
      }
    }
  BackHandler(onBack = onDismiss)
  BoxWithConstraints(
    modifier = modifier,
    contentAlignment = Alignment.Center,
  ) {
    val viewportWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val viewportHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val imageRatio =
      if (session.image.width > 0 && session.image.height > 0)
        session.image.width.toFloat() / session.image.height
      else 1.5f
    val previewLayout =
      commentImagePreviewLayout(
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        imageWidth = session.image.width,
        imageHeight = session.image.height,
        wideViewport = maxWidth >= 600.dp,
      )
    val targetWidth = previewLayout.widthPx
    val targetHeight = previewLayout.heightPx
    val rootLeft = if (rootBounds.left.isFinite()) rootBounds.left else 0f
    val rootTop = if (rootBounds.top.isFinite()) rootBounds.top else 0f
    val source = session.sourceBounds.translate(Offset(-rootLeft, -rootTop))
    val validSource = source.width > 1f && source.height > 1f
    val startScale = commentImageStartScale(source, targetWidth, targetHeight)
    val startTranslationX = if (validSource) source.center.x - viewportWidth / 2f else 0f
    val startTranslationY = if (validSource) source.center.y - viewportHeight / 2f else 0f
    val widthDp = with(density) { targetWidth.toDp() }
    val heightDp = with(density) { targetHeight.toDp() }
    LaunchedEffect(session, targetWidth, targetHeight) {
      session.preparation.markReady(TransitionReadySignal.TARGET_MOUNTED)
      repeat(4) {
        withFrameNanos {}
        if (session.targetBoundsTracker.observe(Rect(0f, 0f, targetWidth, targetHeight))) {
          session.preparation.markReady(TransitionReadySignal.TARGET_BOUNDS_STABLE)
          return@LaunchedEffect
        }
      }
    }
    Box(
      Modifier.matchParentSize()
        .graphicsLayer { alpha = session.progress.value.coerceIn(0f, 1f) * .9f }
        .background(Color.Black)
        .clickable(onClick = onDismiss)
    )

    if (validSource) {
      Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopStart) {
        AsyncImage(
          model = session.image.url,
          contentDescription = null,
          modifier =
            Modifier.requiredSize(
                with(density) { source.width.toDp() },
                with(density) { source.height.toDp() },
              )
              .clip(RoundedCornerShape(12.dp))
              .graphicsLayer {
                val progress = session.progress.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = source.left
                translationY = source.top
                alpha = (1f - progress / .35f).coerceIn(0f, 1f)
              },
          contentScale = ContentScale.Crop,
        )
      }
    }

    val transformGestureModifier =
      if (previewLayout.verticallyScrollable) {
        Modifier
      } else {
        Modifier.pointerInput(session, targetWidth, targetHeight) {
          detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
            if (session.progress.value < .995f) return@detectTransformGestures
            val previousScale = zoomScale
            val nextScale = (previousScale * zoom).coerceIn(1f, 5f)
            val scaleChange = nextScale / previousScale.coerceAtLeast(.001f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val centroidCorrection = (centroid - center) * (1f - scaleChange)
            val candidate = panOffset + pan + centroidCorrection
            val maxPanX = commentImagePanLimit(targetWidth, viewportWidth, nextScale)
            val maxPanY = commentImagePanLimit(targetHeight, viewportHeight, nextScale)
            zoomScale = nextScale
            panOffset =
              if (nextScale <= 1.001f) Offset.Zero
              else
                Offset(
                  candidate.x.coerceIn(-maxPanX, maxPanX),
                  candidate.y.coerceIn(-maxPanY, maxPanY),
                )
          }
        }
      }
    val saveGestureModifier =
      if (previewLayout.verticallyScrollable) {
        Modifier
      } else {
        Modifier.pointerInput(session) {
          detectTapGestures(
            onLongPress = {
              if (session.progress.value >= .995f && !saving) confirmSave = true
            }
          )
        }
      }
    Box(
      modifier =
        Modifier.size(widthDp, heightDp)
          .graphicsLayer {
            val progress = session.progress.value.coerceIn(0f, 1f)
            val effectiveZoom = 1f + (zoomScale - 1f) * progress
            val effectivePan = panOffset * progress
            val sharedScale = startScale + (1f - startScale) * progress
            transformOrigin = TransformOrigin.Center
            val imageScale = sharedScale * effectiveZoom
            scaleX = imageScale
            scaleY = imageScale
            translationX = startTranslationX * (1f - progress) + effectivePan.x
            translationY = startTranslationY * (1f - progress) + effectivePan.y
            alpha = if (validSource) ((progress - .04f) / .28f).coerceIn(0f, 1f) else progress
          }
          .then(saveGestureModifier)
          .then(transformGestureModifier)
    ) {
      if (previewLayout.verticallyScrollable) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { webContext ->
            WebView(webContext).apply {
              WebViewConfigurator.configure(this, BuildConfig.DEBUG)
              settings.javaScriptEnabled = false
              settings.domStorageEnabled = false
              settings.loadWithOverviewMode = false
              settings.useWideViewPort = false
              settings.builtInZoomControls = false
              settings.displayZoomControls = false
              isHorizontalScrollBarEnabled = false
              isVerticalScrollBarEnabled = true
              overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
              setBackgroundColor(android.graphics.Color.TRANSPARENT)
              isLongClickable = true
              setOnLongClickListener {
                if (session.progress.value >= .995f && !saving) confirmSave = true
                true
              }
              webViewClient =
                object : WebViewClient() {
                  override fun onPageFinished(view: WebView, url: String) {
                    session.preparation.markReady(TransitionReadySignal.IMAGE_READY)
                  }
                }
              loadDataWithBaseURL(
                "https://www.bilibili.com/",
                longCommentImageHtml(previewImageUrl),
                "text/html",
                "UTF-8",
                null,
              )
            }
          },
          onRelease = { webView ->
            webView.setOnLongClickListener(null)
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.destroy()
          },
        )
      } else {
        AsyncImage(
          model = previewImageUrl,
          contentDescription = "图片预览",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Fit,
          onSuccess = { session.preparation.markReady(TransitionReadySignal.IMAGE_READY) },
        )
      }
    }
    if (confirmSave) {
      AlertDialog(
        onDismissRequest = { if (!saving) confirmSave = false },
        title = { Text("保存图片？") },
        text = { Text("图片会保存到手机相册中的“哔哩ss”文件夹。") },
        dismissButton = {
          TextButton(onClick = { confirmSave = false }, enabled = !saving) { Text("取消") }
        },
        confirmButton = {
          TextButton(
            enabled = !saving,
            onClick = {
              if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                  ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                  ) != PackageManager.PERMISSION_GRANTED
              ) {
                legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
              } else {
                saveImage()
              }
            },
          ) {
            Text(if (saving) "保存中…" else "保存")
          }
        },
      )
    }
  }
}

@Composable
internal fun HdrVideoFocusOverlay(
  playerBounds: Rect,
  alpha: Float,
  modifier: Modifier = Modifier,
) {
  var overlayBounds by remember { mutableStateOf(Rect.Zero) }
  val cornerRadiusPx = with(LocalDensity.current) { VideoShapeTokens.CornerRadius.toPx() }
  Box(
    modifier
      .onGloballyPositioned { overlayBounds = it.boundsInRoot() }
      .drawBehind {
        if (overlayBounds.width <= 0f || overlayBounds.height <= 0f) return@drawBehind
        val left = (playerBounds.left - overlayBounds.left).coerceIn(0f, size.width)
        val top = (playerBounds.top - overlayBounds.top).coerceIn(0f, size.height)
        val right = (playerBounds.right - overlayBounds.left).coerceIn(0f, size.width)
        val bottom = (playerBounds.bottom - overlayBounds.top).coerceIn(0f, size.height)
        if (right <= left || bottom <= top) return@drawBehind
        val mask =
          Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(left, top, right, bottom, cornerRadiusPx, cornerRadiusPx))
          }
        drawPath(mask, Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)))
      }
  )
}

internal suspend fun animateWindowBrightness(
  window: Window,
  from: Float,
  to: Float,
  durationMs: Long,
) {
  val frameCount = (durationMs / HDR_BRIGHTNESS_STEP_MS).toInt().coerceAtLeast(1)
  val frameDelayMs = (durationMs / frameCount).coerceAtLeast(1L)
  for (frame in 1..frameCount) {
    val progress = FastOutSlowInEasing.transform(frame.toFloat() / frameCount)
    setWindowBrightness(window, from + (to - from) * progress)
    if (frame < frameCount) delay(frameDelayMs)
  }
}

internal fun fullscreenVideoBackgroundShadeAlpha(
  embeddedShadeAlpha: Float,
  fullscreenBrightness: Float,
  fullscreenProgress: Float,
): Float {
  val baseShade = embeddedShadeAlpha.coerceIn(0f, 1f)
  val brightness = fullscreenBrightness.coerceIn(0f, 1f)
  val progress = fullscreenProgress.coerceIn(0f, 1f)
  val fullscreenShade = 1f - brightness * (1f - baseShade)
  return baseShade + (fullscreenShade - baseShade) * progress
}

internal fun resolveWindowBrightness(context: Context, rawBrightness: Float): Float =
  rawBrightness.takeIf { it in 0f..1f }
    ?: (android.provider.Settings.System.getInt(
      context.contentResolver,
      android.provider.Settings.System.SCREEN_BRIGHTNESS,
      128,
    ) / 255f)

internal fun setWindowBrightness(window: Window, brightness: Float) {
  val attributes = window.attributes
  attributes.screenBrightness = brightness.coerceIn(.05f, 1f)
  window.attributes = attributes
}

internal fun releaseWindowBrightnessOverride(window: Window) {
  val attributes = window.attributes
  attributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
  window.attributes = attributes
}

/**
 * 让 HDR 视频保持 60 Hz 显示节奏。在 Tab S8 Ultra 上，应用强制 120 Hz 时把 HDR
 * SurfaceView 呈现在可见 Canvas 弹幕层之下会让 SurfaceFlinger 对整个 Activity 表面
 * 缓冲堆积；60 Hz 匹配 24/30/60 fps 视频而不会出现该队列。
 */
@Suppress("DEPRECATION")
internal fun hdrPreferredDisplayModeId(activity: Activity?): Int? {
  val modes = activity?.windowManager?.defaultDisplay?.supportedModes.orEmpty()
  return (modes
      .filter { it.refreshRate <= HDR_MAX_DISPLAY_REFRESH_RATE + .5f }
      .maxByOrNull {
        it.refreshRate
      } ?: modes.minByOrNull { abs(it.refreshRate - HDR_MAX_DISPLAY_REFRESH_RATE) })
    ?.modeId
}

internal fun restorePreferredDisplayMode(window: Window, preferredModeId: Int) {
  val attributes = window.attributes
  if (attributes.preferredDisplayModeId == preferredModeId) return
  attributes.preferredDisplayModeId = preferredModeId
  window.attributes = attributes
}

internal enum class ImageSaveResult {
  SAVED,
  PERMISSION_REQUIRED,
  FAILED,
}

internal const val HDR_BRIGHTNESS_RAMP_MS = 900L
internal const val HDR_BRIGHTNESS_RESTORE_MS = 480L
internal const val HDR_BRIGHTNESS_STEP_MS = 50L
internal const val HDR_MAX_DISPLAY_REFRESH_RATE = 60f

@Suppress("DEPRECATION")
internal suspend fun savePreviewImageToGallery(
  context: Context,
  imageUrl: String,
): ImageSaveResult =
  withContext(Dispatchers.IO) {
    var insertedUri: android.net.Uri? = null
    try {
      BiliHttpClient.getPublic(
          imageUrl,
          mapOf("Referer" to "https://www.bilibili.com/", "User-Agent" to "Mozilla/5.0"),
        )
        .use { response ->
          val body = response.body
          if (!response.isSuccessful || body == null) return@withContext ImageSaveResult.FAILED
          val mime =
            response.header("Content-Type")?.substringBefore(';')?.trim()?.takeIf {
              it.startsWith("image/")
            } ?: "image/jpeg"
          val extension =
            when (mime.lowercase()) {
              "image/png" -> "png"
              "image/gif" -> "gif"
              "image/webp" -> "webp"
              "image/avif" -> "avif"
              else -> "jpg"
            }
          val values =
            ContentValues().apply {
              put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "哔哩ss_${System.currentTimeMillis()}.$extension",
              )
              put(MediaStore.Images.Media.MIME_TYPE, mime)
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                  MediaStore.Images.Media.RELATIVE_PATH,
                  Environment.DIRECTORY_PICTURES + "/哔哩ss",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
              } else {
                val directory =
                  java.io.File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "哔哩ss",
                  )
                if (!directory.exists() && !directory.mkdirs()) {
                  return@withContext ImageSaveResult.FAILED
                }
                put(
                  MediaStore.Images.Media.DATA,
                  java.io
                    .File(
                      directory,
                      "哔哩ss_${System.currentTimeMillis()}.$extension",
                    )
                    .absolutePath,
                )
              }
            }
          val resolver = context.contentResolver
          val uri =
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
              ?: return@withContext ImageSaveResult.FAILED
          insertedUri = uri
          val outputStream = resolver.openOutputStream(uri)
          if (outputStream == null) {
            resolver.delete(uri, null, null)
            insertedUri = null
            return@withContext ImageSaveResult.FAILED
          }
          outputStream.use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) <= 0) {
              resolver.delete(uri, null, null)
              insertedUri = null
              return@withContext ImageSaveResult.FAILED
            }
          }
          ImageSaveResult.SAVED
        }
    } catch (_: SecurityException) {
      insertedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
      ImageSaveResult.PERMISSION_REQUIRED
    } catch (_: Throwable) {
      insertedUri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
      ImageSaveResult.FAILED
    }
  }

@Composable
internal fun ReplyThreadTransitionContainer(
  sourceBounds: Rect,
  targetBounds: Rect,
  progress: () -> Float,
  contentReady: Boolean = true,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val validBounds =
    sourceBounds.width > 1f &&
      sourceBounds.height > 1f &&
      targetBounds.width > 1f &&
      targetBounds.height > 1f
  val startScaleX = if (validBounds) sourceBounds.width / targetBounds.width else .96f
  val startScaleY = if (validBounds) sourceBounds.height / targetBounds.height else .96f
  val startX = if (validBounds) sourceBounds.left - targetBounds.left else 0f
  val startY = if (validBounds) sourceBounds.top - targetBounds.top else 0f
  Box(modifier = modifier.clipToBounds()) {
    // 进度只由 RenderNode 支持的图层读取：打开与关闭现在只更新四个变换属性，
    // 无需重组回复列表或在主线程显式录制大 GraphicsLayer。
    Box(
      Modifier.fillMaxSize().graphicsLayer {
        val value = progress().coerceIn(0f, 1f)
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = startScaleX + (1f - startScaleX) * value
        scaleY = startScaleY + (1f - startScaleY) * value
        translationX = startX * (1f - value)
        translationY = startY * (1f - value)
        alpha = if (validBounds) (value * 2.5f).coerceIn(0f, 1f) else value
      }
    ) {
      Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border =
          androidx.compose.foundation.BorderStroke(
            .75.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .72f),
          ),
      ) {}
      AnimatedVisibility(
        visible = contentReady,
        enter = fadeIn(tween(160, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(80, easing = FastOutSlowInEasing)),
      ) {
        Box(Modifier.fillMaxSize()) { content() }
      }
    }
  }
}

@Composable
internal fun FadeVisibility(
  visible: Boolean,
  enterMillis: Int,
  exitMillis: Int,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn(tween(enterMillis)),
    exit = fadeOut(tween(exitMillis)),
  ) {
    content()
  }
}

@Composable
internal fun RecommendationLoadingSkeleton(modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth()) {
    Spacer(Modifier.height(8.dp))
    Surface(
      modifier = Modifier.padding(horizontal = 12.dp).width(88.dp).height(18.dp),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceVariant,
    ) {}
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 52.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      repeat(3) {
        Surface(
          modifier = Modifier.width(220.dp).height(126.dp),
          shape = VideoShapeTokens.Card,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
        ) {}
      }
    }
  }
}

@Composable
internal fun CommentLoadingSkeleton(modifier: Modifier = Modifier) {
  Column(
    modifier.padding(start = 10.dp, end = 10.dp, top = 56.dp, bottom = 110.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    repeat(5) {
      Surface(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .68f),
      ) {}
    }
  }
}

internal data class VideoActionPanelGlassColors(
  val container: Color,
  val fallback: Color,
  val border: Color,
)

internal fun videoActionPanelGlassColors(foregroundColor: Color): VideoActionPanelGlassColors {
  val usesLightForeground = foregroundColor.luminance() >= .5f
  return if (usesLightForeground) {
    VideoActionPanelGlassColors(
      container = Color.Black.copy(alpha = .58f),
      fallback = Color.Black.copy(alpha = .76f),
      border = Color.White.copy(alpha = .30f),
    )
  } else {
    VideoActionPanelGlassColors(
      container = Color.White.copy(alpha = .72f),
      fallback = Color.White.copy(alpha = .92f),
      border = Color.Black.copy(alpha = .22f),
    )
  }
}

@Composable
internal fun VideoActionPanel(
  info: VideoInfo?,
  engagement: VideoEngagement,
  loggedIn: Boolean,
  favoriteFolders: List<FavoriteFolder>,
  favoriteFoldersLoading: Boolean,
  onLike: (Boolean) -> Unit,
  onCoin: (Int, Boolean) -> Unit,
  onFavorite: (List<Long>, List<Long>) -> Unit,
  onLoadFavoriteFolders: () -> Unit,
  onLogin: () -> Unit,
  foregroundColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  controlEnabled: Boolean = false,
  controlLikeFocusRequester: FocusRequester? = null,
  controlPlayerFocusRequester: FocusRequester? = null,
  controlSortFocusRequester: FocusRequester? = null,
  modifier: Modifier = Modifier,
) {
  val controlCoinFocusRequester = remember { FocusRequester() }
  val controlFavoriteFocusRequester = remember { FocusRequester() }
  val controlScope = rememberCoroutineScope()
  var showCoinDialog by remember(info?.aid) { mutableStateOf(false) }
  var showFavoriteDialog by remember(info?.aid) { mutableStateOf(false) }
  var favoriteDefaultPending by remember(info?.aid) { mutableStateOf(false) }
  var showCoinBurst by remember(info?.aid) { mutableStateOf(false) }
  val orderedFavoriteFolders =
    remember(favoriteFolders) { prioritizeVideoFavoriteFolders(favoriteFolders) }
  val glassColors = remember(foregroundColor) { videoActionPanelGlassColors(foregroundColor) }
  LaunchedEffect(favoriteDefaultPending, favoriteFoldersLoading, orderedFavoriteFolders) {
    if (!favoriteDefaultPending || favoriteFoldersLoading) return@LaunchedEffect
    val defaultFolder = orderedFavoriteFolders.firstOrNull() ?: return@LaunchedEffect
    favoriteDefaultPending = false
    if (!defaultFolder.favorited) onFavorite(listOf(defaultFolder.id), emptyList())
  }
  LaunchedEffect(showCoinBurst) {
    if (showCoinBurst) {
      delay(720)
      showCoinBurst = false
    }
  }
  BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
    val alignedMenuWidth = maxWidth
    val density = LocalDensity.current
    val compactActions = maxWidth < 380.dp || density.fontScale > 1.15f
    val menuPositionProvider =
      remember(density) {
        AlignedCardPopupPositionProvider(with(density) { 8.dp.roundToPx() })
      }
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = VideoShapeTokens.Card,
      color = glassColors.fallback,
      contentColor = foregroundColor,
      border =
        androidx.compose.foundation.BorderStroke(
          1.dp,
          glassColors.border,
        ),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        VideoActionButton(
          icon = {
            Icon(
              Icons.Default.ThumbUp,
              contentDescription = null,
              modifier = Modifier.size(25.dp),
            )
          },
          label = "点赞",
          count = info?.likeCount,
          active = engagement.loaded && engagement.liked,
          onClick = {
            if (loggedIn) onLike(!engagement.liked) else onLogin()
          },
          enabled = info?.aid?.let { it > 0 } == true && (!loggedIn || engagement.loaded),
          compact = compactActions,
          foregroundColor = foregroundColor,
          modifier =
            Modifier.weight(1f)
              .then(
                if (controlEnabled && controlLikeFocusRequester != null) {
                  Modifier.focusRequester(controlLikeFocusRequester)
                    .focusProperties {
                      left = controlPlayerFocusRequester ?: FocusRequester.Cancel
                      right = controlCoinFocusRequester
                      up = FocusRequester.Cancel
                      down = controlSortFocusRequester ?: FocusRequester.Cancel
                    }
                    .controlFocusOutline(
                      RoundedCornerShape(14.dp),
                      MaterialTheme.colorScheme.primary,
                      width = 3.dp,
                      enabled = true,
                    )
                } else Modifier
              ),
        )
        Box(Modifier.weight(1f)) {
          VideoActionButton(
            icon = {
              BiliCoinIcon(
                coinCount = engagement.coins,
                modifier = Modifier.size(28.dp),
              )
            },
            label = "投币",
            count = info?.coinCount,
            active = engagement.loaded && engagement.coins > 0,
            onClick = {
              if (loggedIn) showCoinDialog = true else onLogin()
            },
            enabled = info?.aid?.let { it > 0 } == true && (!loggedIn || engagement.loaded),
            compact = compactActions,
            foregroundColor = foregroundColor,
            modifier =
              Modifier.fillMaxWidth()
                .then(
                  if (controlEnabled && controlLikeFocusRequester != null) {
                    Modifier.focusRequester(controlCoinFocusRequester)
                      .focusProperties {
                        left = controlLikeFocusRequester
                        right = controlFavoriteFocusRequester
                        up = FocusRequester.Cancel
                        down = controlSortFocusRequester ?: FocusRequester.Cancel
                      }
                      .controlFocusOutline(
                        RoundedCornerShape(14.dp),
                        MaterialTheme.colorScheme.primary,
                        width = 3.dp,
                        enabled = true,
                      )
                  } else Modifier
                ),
          )
          if (showCoinBurst) CoinBurst(Modifier.align(Alignment.Center))
        }
        VideoActionButton(
          icon = {
            Icon(
              Icons.Default.Star,
              contentDescription = null,
              modifier = Modifier.size(27.dp),
            )
          },
          label = "收藏",
          count = info?.favoriteCount,
          active = engagement.loaded && engagement.favorited,
          onClick = {
            if (!loggedIn) {
              onLogin()
            } else {
              showFavoriteDialog = true
              val defaultFolder = orderedFavoriteFolders.firstOrNull()
              if (defaultFolder == null) {
                favoriteDefaultPending = true
                onLoadFavoriteFolders()
              } else {
                favoriteDefaultPending = false
                if (!defaultFolder.favorited) {
                  onFavorite(listOf(defaultFolder.id), emptyList())
                }
              }
            }
          },
          enabled = info?.aid?.let { it > 0 } == true && (!loggedIn || engagement.loaded),
          compact = compactActions,
          foregroundColor = foregroundColor,
          modifier =
            Modifier.weight(1f)
              .then(
                if (controlEnabled && controlLikeFocusRequester != null) {
                  Modifier.focusRequester(controlFavoriteFocusRequester)
                    .focusProperties {
                      left = controlCoinFocusRequester
                      right = FocusRequester.Cancel
                      up = FocusRequester.Cancel
                      down = controlSortFocusRequester ?: FocusRequester.Cancel
                    }
                    .controlFocusOutline(
                      RoundedCornerShape(14.dp),
                      MaterialTheme.colorScheme.primary,
                      width = 3.dp,
                      enabled = true,
                    )
                } else Modifier
              ),
        )
      }
    }
    if (showCoinDialog) {
      CoinDialog(
        alreadyCoined = engagement.coins,
        alreadyLiked = engagement.liked,
        copyright = info?.copyright ?: 0,
        width = alignedMenuWidth,
        positionProvider = menuPositionProvider,
        onDismiss = {
          showCoinDialog = false
          if (controlEnabled) {
            controlScope.launch {
              withFrameNanos {}
              runCatching { controlCoinFocusRequester.requestFocus() }
            }
          }
        },
        onConfirm = { count, alsoLike ->
          showCoinBurst = true
          onCoin(count, alsoLike)
        },
      )
    }
    if (showFavoriteDialog) {
      FavoriteFolderDialog(
        folders = orderedFavoriteFolders,
        loading = favoriteFoldersLoading,
        width = alignedMenuWidth,
        positionProvider = menuPositionProvider,
        onDismiss = {
          showFavoriteDialog = false
          if (controlEnabled) {
            controlScope.launch {
              withFrameNanos {}
              runCatching { controlFavoriteFocusRequester.requestFocus() }
            }
          }
        },
        onConfirm = { addIds, removeIds ->
          onFavorite(addIds, removeIds)
        },
      )
    }
  }
}

internal fun prioritizeVideoFavoriteFolders(folders: List<FavoriteFolder>): List<FavoriteFolder> {
  if (folders.size <= 1) return folders
  val defaultFolder = folders.firstOrNull { it.title.trim() == "默认收藏夹" } ?: folders.first()
  val musicFolder = folders.firstOrNull { it.id != defaultFolder.id && it.title.trim() == "音乐" }
  return buildList(folders.size) {
    add(defaultFolder)
    musicFolder?.let(::add)
    folders.forEach { folder ->
      if (folder.id != defaultFolder.id && folder.id != musicFolder?.id) add(folder)
    }
  }
}

internal class AlignedCardPopupPositionProvider(private val gapPx: Int) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    val x = anchorBounds.left.coerceIn(0, maxX)
    val below = anchorBounds.bottom + gapPx
    val above = anchorBounds.top - popupContentSize.height - gapPx
    val y =
      when {
        below + popupContentSize.height <= windowSize.height -> below
        above >= 0 -> above
        else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
      }
    return IntOffset(x, y)
  }
}

@Composable
internal fun VideoActionButton(
  icon: @Composable () -> Unit,
  label: String,
  count: Long?,
  active: Boolean,
  onClick: () -> Unit,
  enabled: Boolean,
  compact: Boolean,
  foregroundColor: Color,
  modifier: Modifier = Modifier,
) {
  val actionPink = Color(0xFFFF5C8A)
  val iconColor by
    animateColorAsState(
      targetValue = if (active) actionPink else foregroundColor.copy(alpha = .86f),
      label = "videoActionIconColor",
    )
  Surface(
    modifier =
      modifier
        .heightIn(min = if (compact) 72.dp else 68.dp)
        .clickable(enabled = enabled, onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    color = if (active) actionPink.copy(alpha = .13f) else foregroundColor.copy(alpha = .08f),
    contentColor = foregroundColor,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides iconColor) {
        icon()
      }
      Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      count?.let {
        Text(
          formatCompactCount(it),
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
