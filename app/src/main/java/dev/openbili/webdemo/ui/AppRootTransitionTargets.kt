package dev.openbili.webdemo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.openbili.webdemo.R
import dev.openbili.webdemo.video.bangumiPageLayoutForPane
import dev.openbili.webdemo.video.videoPaneSpec
import kotlinx.coroutines.launch

@Composable
internal fun BiliOneStartupAnimation(
  reduceMotion: Boolean,
  customImageUri: String,
  modifier: Modifier = Modifier,
) {
  if (customImageUri.isNotBlank()) {
    // 用户选择的蒙版拥有完整的启动区间。Coil 打开持久化 URI 时不要在其后挂载默认
    // logo，也不要继承 logo 的缩放/淡入。根启动层仍会在最后执行那一次整屏淡出。
    AsyncImage(
      model = customImageUri,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier.fillMaxSize(),
    )
    return
  }
  // 没有自定义图片时，复用完全相同的 Android 自适应图标前景，让第一视觉印象
  // 与启动器身份不会漂移。
  val logoAlpha = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
  val logoScale = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else .9f) }
  LaunchedEffect(reduceMotion) {
    if (reduceMotion) return@LaunchedEffect
    logoAlpha.snapTo(0f)
    logoScale.snapTo(.9f)
    launch { logoAlpha.animateTo(1f, animationSpec = tween(150, easing = FastOutSlowInEasing)) }
    logoScale.animateTo(1f, animationSpec = tween(280, easing = FastOutSlowInEasing))
  }
  val defaultPainter = painterResource(R.drawable.bilione_3d_foreground)
  val defaultImageModifier =
    modifier.size(172.dp).graphicsLayer {
      alpha = logoAlpha.value
      scaleX = logoScale.value
      scaleY = logoScale.value
    }
  Image(
    painter = defaultPainter,
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier = defaultImageModifier,
  )
}

/**
 * 只测量竖屏转番剧共享转场所使用的海报目的地。让该布局独立于 VideoScreen，
 * 避免海报飞行期间播放器、评论和页面效果被组合。
 */
@Composable
internal fun BangumiPosterTransitionTarget(onPosterBoundsChanged: (Rect) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val contentWidth = maxOf(1.dp, maxWidth - 28.dp)
    val contentHeight = maxOf(1.dp, maxHeight - 88.dp)
    val paneSpec =
      videoPaneSpec(
        widthPx = with(density) { contentWidth.roundToPx() },
        heightPx = with(density) { contentHeight.roundToPx() },
        density = density.density,
        fontScale = density.fontScale,
      )
    val primaryWidth =
      if (paneSpec.split) {
        maxOf(1.dp, contentWidth - with(density) { paneSpec.secondarySizePx.toDp() } - 12.dp)
      } else contentWidth
    val primaryHeight = if (paneSpec.split) contentHeight else maxOf(1.dp, contentHeight * .68f)
    val pageLayout =
      bangumiPageLayoutForPane(
        primaryWidthDp = primaryWidth.value,
        primaryHeightDp = primaryHeight.value,
        fontScale = density.fontScale,
      )
    val posterHeight = maxOf(1.dp, primaryHeight - pageLayout.playerHeight - 8.dp)
    Box(
      Modifier.offset(x = 16.dp, y = 84.dp + pageLayout.playerHeight)
        .size(width = posterHeight * .75f, height = posterHeight)
        .onGloballyPositioned { onPosterBoundsChanged(it.boundsInRoot()) }
    )
  }
}

/** 番剧首页播放器/卡片飞行占据画面时使用的轻量 16:9 目的地。 */
@Composable
internal fun BangumiPlayerTransitionTarget(onPlayerBoundsChanged: (Rect) -> Unit) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val contentWidth = maxOf(1.dp, maxWidth - 28.dp)
    val contentHeight = maxOf(1.dp, maxHeight - 88.dp)
    val paneSpec =
      videoPaneSpec(
        widthPx = with(density) { contentWidth.roundToPx() },
        heightPx = with(density) { contentHeight.roundToPx() },
        density = density.density,
        fontScale = density.fontScale,
      )
    val primaryWidth =
      if (paneSpec.split) {
        maxOf(1.dp, contentWidth - with(density) { paneSpec.secondarySizePx.toDp() } - 12.dp)
      } else contentWidth
    val primaryHeight = if (paneSpec.split) contentHeight else maxOf(1.dp, contentHeight * .68f)
    val pageLayout =
      bangumiPageLayoutForPane(
        primaryWidthDp = primaryWidth.value,
        primaryHeightDp = primaryHeight.value,
        fontScale = density.fontScale,
      )
    Box(
      // 精确镜像 VideoContent：76 dp 的 BangumiHeader，然后是内容 16 dp 的起始内边距。
      // 真实 Surface 使用 playerWidth，而不是完整的主窗格宽度。
      Modifier.offset(x = 16.dp, y = 76.dp)
        .size(width = pageLayout.playerWidth, height = pageLayout.playerHeight)
        .onGloballyPositioned { onPlayerBoundsChanged(it.boundsInRoot()) }
    )
  }
}
