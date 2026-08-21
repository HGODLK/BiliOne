package dev.openbili.webdemo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.media3.ui.PlayerView
import dev.openbili.webdemo.api.DanmakuItem
import dev.openbili.webdemo.api.DanmakuMaskTimeline
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import kotlin.math.roundToInt

internal enum class RootPlayerSurfaceRole {
  IDLE,
  PREVIEW_PENDING,
  PREVIEW,
  DETAIL_PENDING,
  DETAIL,
  EXIT_COVERED,
}

internal data class RootPlayerOwnership(
  val role: RootPlayerSurfaceRole,
  val mediaId: String? = null,
)

internal data class SharedPlayerHostConfig(
  val modifier: Modifier,
  val fullscreenProgress: Float,
  val fullscreen: Boolean,
  val danmakuAllowed: Boolean,
)

internal enum class SharedPlayerViewRole {
  PREVIEW,
  DETAIL,
}

internal data class HeldPlayerView(
  val role: SharedPlayerViewRole,
  val view: PlayerView,
)

internal fun shouldPositionBangumiPreviewPortal(
  previewOwned: Boolean,
  boundsUsable: Boolean,
  previewPortalVisible: Boolean,
): Boolean = previewOwned && boundsUsable && previewPortalVisible

internal fun shouldUseRootPlayerHost(
  startupWarmupVisible: Boolean,
  bangumiRootPageActive: Boolean,
  hasBangumiHomeTransition: Boolean,
): Boolean = !startupWarmupVisible && (bangumiRootPageActive || hasBangumiHomeTransition)

internal fun shouldActivateBangumiRootPage(
  selectedTab: RootTab,
  settledPage: Int,
  pageSwitchInProgress: Boolean,
  videoScreenVisible: Boolean,
): Boolean =
  selectedTab == RootTab.BANGUMI &&
    settledPage == RootTab.BANGUMI.ordinal &&
    !pageSwitchInProgress &&
    !videoScreenVisible

internal fun shouldSuppressDetailPlayerForBangumiCardTransition(
  kind: TransitionKind,
  phase: SessionPhase,
): Boolean =
  when (kind) {
    TransitionKind.ENTER_ROOT ->
      phase == SessionPhase.PREPARING || phase == SessionPhase.READY || phase == SessionPhase.FLYING
    TransitionKind.EXIT_ROOT ->
      phase == SessionPhase.FLYING || phase == SessionPhase.REVEALING_BACKGROUND
    else -> false
  }

@Composable
private fun CachedBangumiTransitionCover(
  coverUrl: String,
  modifier: Modifier = Modifier,
) {
  val bitmap =
    LoadedFeedImageRegistry.bitmap(bangumiPreviewCoverCacheKey(coverUrl))
      ?: LoadedFeedImageRegistry.bitmap(coverUrl)
  if (bitmap != null) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      modifier = modifier,
      contentScale = ContentScale.Crop,
    )
  } else {
    Box(modifier.background(Color.Black))
  }
}

@Composable
internal fun RootPlayerLayer(
  hostEnabled: Boolean,
  ownership: RootPlayerOwnership,
  previewBounds: Rect,
  previewCoverAlpha: () -> Float,
  previewCoverBlend: BangumiPreviewCoverBlend?,
  previewGestureVisualActive: Boolean,
  previewPortalVisible: Boolean,
  previewImageLoadingEnabled: Boolean,
  previewTarget: BangumiPreviewTarget?,
  layerItem: FeedItem?,
  playerContent: @Composable (SharedPlayerHostConfig) -> Unit,
) {
  if (!hostEnabled) return
  val density = LocalDensity.current
  // 预览门户在其媒体 id 被切换期间拥有物理宿主。要求旧拥有权 id 已与新目标一致，
  // 会在拥有权效应赶上之前把 SurfaceView 短暂停在 1 x 1，表现为位置闪烁。
  val previewOwned =
    ownership.role in setOf(RootPlayerSurfaceRole.PREVIEW_PENDING, RootPlayerSurfaceRole.PREVIEW)
  // PREVIEW_PENDING 必须已经收到真实的预览边界。等首帧再给 SurfaceView 定尺寸，
  // 会在不渲染 1 px 停放表面的设备上造成死锁。在其首帧上报之前，其上方一直保留
  // 一张封面。
  val previewPositioned =
    shouldPositionBangumiPreviewPortal(
      previewOwned = previewOwned,
      boundsUsable = previewBounds.hasUsableSize(),
      previewPortalVisible = previewPortalVisible,
    )
  val bounds = if (previewPositioned) previewBounds else Rect.Zero
  val contentVisible = bounds.hasUsableSize()
  // SurfaceView 不能停放在窗口之外：在三星的 SurfaceControl 实现上，Compose 视图返回后，
  // 屏幕外的父级可能仍留在 SurfaceFlinger 的 Offscreen Hierarchy 中。一个 1 像素的
  // 屏上宿主能让那个表面保持挂接而不暴露内容。
  val hostBounds = if (contentVisible) bounds else Rect(0f, 0f, 1f, 1f)

  Box(
    Modifier.offset { IntOffset(hostBounds.left.roundToInt(), hostBounds.top.roundToInt()) }
      .size(
        width = with(density) { hostBounds.width.toDp() },
        height = with(density) { hostBounds.height.toDp() },
      )
      .clip(VideoShapeTokens.Player)
  ) {
    if (contentVisible) {
      VideoCardGradient(
        coverUrl = layerItem?.coverUrl.orEmpty(),
        modifier = Modifier.fillMaxSize(),
        loadKey = "root-player-background:${layerItem?.id.orEmpty()}",
        useColorfulCardsPreference = false,
      ) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .36f)))
      }
    }
    // 预热后保持唯一的 AndroidView 挂载，使其无需重新充气即可复用。空闲的根宿主
    // 有意让 Media3 保持未绑定；否则旧解码器可能继续向这个 1 像素 SurfaceView 产出，
    // 耗尽三星的缓冲队列。
    playerContent(
      SharedPlayerHostConfig(
        modifier = Modifier.fillMaxSize(),
        fullscreenProgress = 0f,
        fullscreen = false,
        danmakuAllowed = false,
      )
    )
    if (contentVisible && previewPositioned) {
      Box(
        Modifier.fillMaxSize().graphicsLayer {
          alpha = previewCoverAlpha().coerceIn(0f, 1f)
        }
      ) {
        if (previewGestureVisualActive && previewCoverBlend != null) {
          CachedBangumiTransitionCover(
            coverUrl = previewCoverBlend.fromCoverUrl,
            modifier = Modifier.fillMaxSize(),
          )
          CachedBangumiTransitionCover(
            coverUrl = previewCoverBlend.toCoverUrl,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previewCoverBlend.progress },
          )
        } else {
          CoverImage(
            coverUrl =
              previewTarget?.item?.coverUrl?.ifBlank { layerItem?.coverUrl.orEmpty() }
                ?: layerItem?.coverUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            shape = VideoShapeTokens.Player,
            enforceAspectRatio = false,
            requestWidth = 1600,
            requestHeight = 900,
            loadKey = "root-player-preview-pending:${previewTarget?.item?.id.orEmpty()}",
            bitmapCacheKey =
              bangumiPreviewCoverCacheKey(
                previewTarget?.item?.coverUrl?.ifBlank { layerItem?.coverUrl.orEmpty() }
                  ?: layerItem?.coverUrl.orEmpty()
              ),
            alwaysLoad = true,
            loadingEnabled = previewImageLoadingEnabled,
            retainBitmap = true,
            fadeIn = false,
          )
        }
      }
    }
  }
}

/**
 * 跟踪上一次的弹幕参数，让托管的覆盖层在重组之间没有任何变化时不更新。
 */
internal class DanmakuUpdateState {
  private var itemsRef: List<DanmakuItem>? = null
  private var maskRef: DanmakuMaskTimeline? = null
  private var enabled = false
  private var smartBlocking = false
  private var paused = false
  private var fullscreen = false
  private var highDynamicRange = false
  private var opacity = .72f
  private var displayArea = .75f
  private var densityLevel = 3
  private var blockLevel = 1
  private var fontScale = 1f
  private var speed = 1f
  private var positionEpoch = Long.MIN_VALUE

  fun changed(
    items: List<DanmakuItem>,
    mask: DanmakuMaskTimeline?,
    enabled: Boolean,
    smartBlocking: Boolean,
    paused: Boolean,
    fullscreen: Boolean,
    highDynamicRange: Boolean,
    opacity: Float,
    displayArea: Float,
    densityLevel: Int,
    blockLevel: Int,
    fontScale: Float,
    speed: Float,
    positionEpoch: Long,
  ): Boolean {
    if (
      items !== itemsRef ||
        mask !== maskRef ||
        enabled != this.enabled ||
        smartBlocking != this.smartBlocking ||
        paused != this.paused ||
        fullscreen != this.fullscreen ||
        highDynamicRange != this.highDynamicRange ||
        opacity != this.opacity ||
        displayArea != this.displayArea ||
        densityLevel != this.densityLevel ||
        blockLevel != this.blockLevel ||
        fontScale != this.fontScale ||
        speed != this.speed ||
        positionEpoch != this.positionEpoch
    ) {
      itemsRef = items
      maskRef = mask
      this.enabled = enabled
      this.smartBlocking = smartBlocking
      this.paused = paused
      this.fullscreen = fullscreen
      this.highDynamicRange = highDynamicRange
      this.opacity = opacity
      this.displayArea = displayArea
      this.densityLevel = densityLevel
      this.blockLevel = blockLevel
      this.fontScale = fontScale
      this.speed = speed
      this.positionEpoch = positionEpoch
      return true
    }
    return false
  }
}
