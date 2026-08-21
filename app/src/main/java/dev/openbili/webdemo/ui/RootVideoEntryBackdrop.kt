package dev.openbili.webdemo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import dev.openbili.webdemo.settings.AppSettings

/**
 * 在根卡片飞向其目的地时只绘制播放页背景。
 *
 * 让完整的 [dev.openbili.webdemo.video.VideoScreen] 在该飞行期间保持透明，
 * 避免交叉淡入其整个设备尺寸的渲染树。alpha 直接应用到三个背景原语上，
 * 因此该图层无需另一个全屏离屏缓冲。
 */
@Composable
internal fun RootVideoEntryBackdrop(
  settings: AppSettings,
  playerBounds: Rect,
  revealAlpha: () -> Float,
  punchPlayerPortal: Boolean = true,
  showCustomImageScrim: Boolean = true,
  modifier: Modifier = Modifier,
) {
  val pageBackground = androidx.compose.material3.MaterialTheme.colorScheme.background
  val darkPage = pageBackground.luminance() < .5f
  val portalRadiusPx = with(LocalDensity.current) { VideoShapeTokens.CornerRadius.toPx() }
  val customImageAlpha =
    if (settings.videoBackgroundBlur) 1f
    else 1f - settings.videoBackgroundTransparency.coerceIn(0f, 1f)
  val backgroundModel =
    rememberStaticBackgroundModel(
      source = settings.videoBackgroundUri,
      blurred = settings.videoBackgroundBlur,
    )
  val backgroundProfile = rememberBackgroundLuminanceProfile(backgroundModel)
  val backgroundScrim = videoBackgroundScrim(backgroundProfile, darkPage)

  Box(
    modifier =
      modifier.fillMaxSize().drawWithCache {
        val outsidePlayer =
          outsidePlayerPath(
            size.width,
            size.height,
            playerBounds,
            portalRadiusPx,
            punchPlayerPortal,
          )
        onDrawBehind {
          val alpha = revealAlpha().coerceIn(0f, 1f)
          if (alpha <= 0f) return@onDrawBehind
          if (outsidePlayer != null) {
            drawPath(outsidePlayer, pageBackground, alpha = alpha)
          } else {
            drawRect(pageBackground, alpha = alpha)
          }
        }
      }
  ) {
    if (settings.videoBackgroundUri.isNotBlank()) {
      CrossfadeBackgroundImage(
        model = backgroundModel,
        modifier =
          Modifier.fillMaxSize()
            .drawWithCache {
              val outsidePlayer =
                outsidePlayerPath(
                  size.width,
                  size.height,
                  playerBounds,
                  portalRadiusPx,
                  punchPlayerPortal,
                )
              onDrawWithContent {
                if (outsidePlayer != null) {
                  clipPath(outsidePlayer) { this@onDrawWithContent.drawContent() }
                } else {
                  drawContent()
                }
              }
            }
            .graphicsLayer {
              alpha = customImageAlpha * revealAlpha().coerceIn(0f, 1f)
              compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        contentScale = ContentScale.Crop,
        transitionMillis = if (settings.reduceMotion) 90 else 300,
      )
      if (showCustomImageScrim && darkPage) {
        Box(
          Modifier.fillMaxSize().drawWithCache {
            val outsidePlayer =
              outsidePlayerPath(
                size.width,
                size.height,
                playerBounds,
                portalRadiusPx,
                punchPlayerPortal,
              )
            onDrawBehind {
              val alpha = revealAlpha().coerceIn(0f, 1f)
              if (alpha <= 0f) return@onDrawBehind
              if (outsidePlayer != null) {
                drawPath(outsidePlayer, backgroundScrim, alpha = alpha)
              } else {
                drawRect(backgroundScrim, alpha = alpha)
              }
            }
          }
        )
      }
    }
  }
}

private fun outsidePlayerPath(
  width: Float,
  height: Float,
  playerBounds: Rect,
  radiusPx: Float,
  punchPlayerPortal: Boolean,
): Path? {
  if (!punchPlayerPortal || playerBounds.width <= 0f || playerBounds.height <= 0f) return null
  return Path().apply {
    fillType = PathFillType.EvenOdd
    addRect(Rect(0f, 0f, width, height))
    addRoundRect(
      RoundRect(
        playerBounds.left,
        playerBounds.top,
        playerBounds.right,
        playerBounds.bottom,
        radiusPx,
        radiusPx,
      )
    )
  }
}
