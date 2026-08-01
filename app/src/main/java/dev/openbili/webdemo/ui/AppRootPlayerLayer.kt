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
): Boolean =
  !startupWarmupVisible && (bangumiRootPageActive || hasBangumiHomeTransition)

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
      phase == SessionPhase.PREPARING ||
        phase == SessionPhase.READY ||
        phase == SessionPhase.FLYING
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
  // The preview portal owns the physical host while its media id is being switched. Requiring the
  // old ownership id to already match the new target briefly parks the SurfaceView at 1 x 1 before
  // the ownership effect can catch up, which is visible as a positional flash.
  val previewOwned =
    ownership.role in setOf(RootPlayerSurfaceRole.PREVIEW_PENDING, RootPlayerSurfaceRole.PREVIEW)
  // PREVIEW_PENDING must already receive the real preview bounds. Waiting for the first frame
  // before sizing the SurfaceView creates a deadlock on devices that do not render a 1 px parked
  // surface. A cover remains above it until that first frame is reported.
  val previewPositioned =
    shouldPositionBangumiPreviewPortal(
      previewOwned = previewOwned,
      boundsUsable = previewBounds.hasUsableSize(),
      previewPortalVisible = previewPortalVisible,
    )
  val bounds = if (previewPositioned) previewBounds else Rect.Zero
  val contentVisible = bounds.hasUsableSize()
  // SurfaceView cannot be parked outside the window: on Samsung's SurfaceControl implementation
  // an off-screen parent may stay in SurfaceFlinger's Offscreen Hierarchy after the Compose view
  // returns. A one-pixel on-screen host keeps the one surface attached without exposing content.
  val hostBounds =
    if (contentVisible) bounds
    else Rect(0f, 0f, 1f, 1f)

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
      ) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .36f)))
      }
    }
    // Keep the sole AndroidView mounted after warmup. Parking it off-screen avoids the stale
    // SurfaceView buffer size seen when a PV-sized host was detached and later reused by a larger
    // homepage player.
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
            modifier =
              Modifier.fillMaxSize().graphicsLayer { alpha = previewCoverBlend.progress },
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
 * Tracks the last danmaku parameters so we can skip [PlayerView.updateDanmakuOverlay] when nothing
 * has changed across recompositions.
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
