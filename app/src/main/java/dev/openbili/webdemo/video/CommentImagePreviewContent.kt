package dev.openbili.webdemo.video

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import java.net.URI
import kotlin.math.roundToInt

private const val COMMENT_IMAGE_PREVIEW_TAG = "CommentImagePreview"
private const val COMMENT_IMAGE_PREVIEW_HIGH_QUALITY_SCALE = 2f
private const val COMMENT_IMAGE_PREVIEW_HIGH_QUALITY_MAX_EDGE_PX = 4096
private const val COMMENT_IMAGE_PREVIEW_FALLBACK_MAX_EDGE_PX = 2048

internal data class CommentImagePreviewRequestSpec(
  val url: String,
  val widthPx: Int,
  val heightPx: Int,
)

/**
 * 普通评论图片只请求与屏幕、缩放需求相称的派生图。原图保留为最后一级兼容回退，
 * 不参与超长图的 WebView 加载路径。
 */
internal fun commentImagePreviewRequestSpecs(
  rawUrl: String,
  targetWidthPx: Float,
  targetHeightPx: Float,
): List<CommentImagePreviewRequestSpec> {
  val originalUrl = fullResolutionCommentImageUrl(rawUrl)
  val highQualitySize =
    boundedCommentImagePreviewSize(
      targetWidthPx * COMMENT_IMAGE_PREVIEW_HIGH_QUALITY_SCALE,
      targetHeightPx * COMMENT_IMAGE_PREVIEW_HIGH_QUALITY_SCALE,
      COMMENT_IMAGE_PREVIEW_HIGH_QUALITY_MAX_EDGE_PX,
    )
  val fallbackSize =
    boundedCommentImagePreviewSize(
      targetWidthPx,
      targetHeightPx,
      COMMENT_IMAGE_PREVIEW_FALLBACK_MAX_EDGE_PX,
    )
  val host = runCatching { URI(originalUrl).host.orEmpty() }.getOrDefault("")
  val bilibiliImageHost = host == "hdslb.com" || host.endsWith(".hdslb.com")

  fun variant(size: Pair<Int, Int>): CommentImagePreviewRequestSpec {
    val url =
      if (bilibiliImageHost) {
        val base = originalUrl.substringBefore('?')
        val query = originalUrl.substringAfter('?', "")
        "$base@${size.first}w.webp" + if (query.isBlank()) "" else "?$query"
      } else {
        originalUrl
      }
    return CommentImagePreviewRequestSpec(url, size.first, size.second)
  }

  return buildList {
      add(variant(highQualitySize))
      add(variant(fallbackSize))
      add(CommentImagePreviewRequestSpec(originalUrl, fallbackSize.first, fallbackSize.second))
    }
    .distinct()
}

private fun boundedCommentImagePreviewSize(
  widthPx: Float,
  heightPx: Float,
  maxEdgePx: Int,
): Pair<Int, Int> {
  val safeWidth = widthPx.coerceAtLeast(1f)
  val safeHeight = heightPx.coerceAtLeast(1f)
  val scale = minOf(1f, maxEdgePx / maxOf(safeWidth, safeHeight))
  return (safeWidth * scale).roundToInt().coerceAtLeast(1) to
    (safeHeight * scale).roundToInt().coerceAtLeast(1)
}

internal fun commentImageCanPan(
  panChange: Offset,
  panOffset: Offset,
  maxPanX: Float,
  maxPanY: Float,
): Boolean {
  val nextX = (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX)
  val nextY = (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
  return nextX != panOffset.x || nextY != panOffset.y
}

@Composable
internal fun CommentRegularImagePreview(
  rawUrl: String,
  targetWidthPx: Float,
  targetHeightPx: Float,
  onReady: () -> Unit,
  onIntrinsicSizeKnown: (width: Int, height: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val requests =
    remember(rawUrl, targetWidthPx, targetHeightPx) {
      commentImagePreviewRequestSpecs(rawUrl, targetWidthPx, targetHeightPx)
    }
  var requestIndex by remember(requests) { mutableIntStateOf(0) }
  var retryEpoch by remember(requests) { mutableIntStateOf(0) }
  var finalRequestFailed by remember(requests) { mutableStateOf(false) }
  val requestSpec = requests[requestIndex.coerceIn(requests.indices)]
  val request =
    remember(context, requestSpec, retryEpoch) {
      ImageRequest.Builder(context)
        .data(requestSpec.url)
        .size(requestSpec.widthPx, requestSpec.heightPx)
        .precision(Precision.INEXACT)
        .build()
    }

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    AsyncImage(
      model = request,
      contentDescription = "图片预览",
      modifier = Modifier.fillMaxSize(),
      contentScale = ContentScale.Fit,
      onSuccess = { state ->
        finalRequestFailed = false
        onIntrinsicSizeKnown(state.result.image.width, state.result.image.height)
        onReady()
      },
      onError = { state ->
        val host = runCatching { URI(requestSpec.url).host.orEmpty() }.getOrDefault("")
        Log.w(
          COMMENT_IMAGE_PREVIEW_TAG,
          "评论图片加载失败：host=$host size=${requestSpec.widthPx}x${requestSpec.heightPx}",
          state.result.throwable,
        )
        if (requestIndex < requests.lastIndex) {
          finalRequestFailed = false
          requestIndex++
        } else {
          finalRequestFailed = true
          onReady()
        }
      },
    )
    if (finalRequestFailed) {
      Text(
        text = "图片加载失败，点击重试",
        modifier =
          Modifier.clickable {
            finalRequestFailed = false
            requestIndex = 0
            retryEpoch++
          },
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}
