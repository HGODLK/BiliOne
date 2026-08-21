package dev.openbili.webdemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class RootTab {
  HOME,
  BANGUMI,
  MY,
}

private val BangumiTvIcon: ImageVector by lazy {
  ImageVector.Builder(
      name = "BangumiTv",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    )
    .apply {
      path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
        moveTo(7.1f, 2.7f)
        lineTo(12f, 6.4f)
        lineTo(16.9f, 2.7f)
        lineTo(18.1f, 4.3f)
        lineTo(15.9f, 6f)
        horizontalLineTo(19f)
        curveTo(20.7f, 6f, 22f, 7.3f, 22f, 9f)
        verticalLineTo(19f)
        curveTo(22f, 20.7f, 20.7f, 22f, 19f, 22f)
        horizontalLineTo(5f)
        curveTo(3.3f, 22f, 2f, 20.7f, 2f, 19f)
        verticalLineTo(9f)
        curveTo(2f, 7.3f, 3.3f, 6f, 5f, 6f)
        horizontalLineTo(8.1f)
        lineTo(5.9f, 4.3f)
        close()
        moveTo(5f, 8f)
        curveTo(4.4f, 8f, 4f, 8.4f, 4f, 9f)
        verticalLineTo(19f)
        curveTo(4f, 19.6f, 4.4f, 20f, 5f, 20f)
        horizontalLineTo(19f)
        curveTo(19.6f, 20f, 20f, 19.6f, 20f, 19f)
        verticalLineTo(9f)
        curveTo(20f, 8.4f, 19.6f, 8f, 19f, 8f)
        close()
        moveTo(9.2f, 10.4f)
        lineTo(15.7f, 14f)
        lineTo(9.2f, 17.6f)
        close()
      }
    }
    .build()
}

internal data class RootPagerAnchor(val page: Int, val offsetFraction: Float)

internal fun rootTabForCapsulePosition(position: Float): RootTab =
  RootTab.entries[position.coerceIn(0f, (RootTab.entries.size - 1).toFloat()).roundToInt()]

internal fun rootPagerAnchorForCapsulePosition(position: Float): RootPagerAnchor {
  val lastPage = RootTab.entries.lastIndex
  val clamped = position.coerceIn(0f, lastPage.toFloat())
  val lower = kotlin.math.floor(clamped).toInt()
  if (lower >= lastPage) return RootPagerAnchor(lastPage, 0f)
  val fraction = clamped - lower
  return if (fraction <= .5f) RootPagerAnchor(lower, fraction)
  else RootPagerAnchor(lower + 1, fraction - 1f)
}

@Composable
fun BottomCapsule(
  selected: RootTab,
  onSelected: (RootTab) -> Unit,
  onControlSelected: (RootTab) -> Unit = onSelected,
  backdropLayer: GraphicsLayer? = null,
  modifier: Modifier = Modifier,
  selectionPosition: () -> Float = { selected.ordinal.toFloat() },
  onSelectionDrag: (Float) -> Unit = {},
  onInteractionStart: () -> Unit = {},
  onInteractionEnd: () -> Unit = {},
  dragEnabled: Boolean = true,
  initialFocusRequester: FocusRequester? = null,
  focusEnabled: Boolean = true,
) {
  val glassEffectsEnabled = LocalGlassEffectsEnabled.current
  var contentBounds by remember { mutableStateOf(Rect.Zero) }
  var dragPosition by remember { mutableStateOf<Float?>(null) }
  var selectionTravelPx by remember { mutableFloatStateOf(1f) }
  fun settleDrag() {
    val position =
      (dragPosition ?: selectionPosition()).coerceIn(0f, RootTab.entries.lastIndex.toFloat())
    dragPosition = null
    onSelected(rootTabForCapsulePosition(position))
    onInteractionEnd()
  }
  GlassSurface(
    // 保持原有的尺寸链完整。此前基于 Box 的尝试没有固有高度，
    // 导致胶囊在测量期间塌陷。
    modifier = modifier.navigationBarsPadding().width(438.dp),
    shape = CircleShape,
    containerColor = Color.Transparent,
    borderColor = Color.White.copy(alpha = .20f),
  ) {
    Box(
      Modifier.onGloballyPositioned { contentBounds = it.boundsInRoot() }
        .pointerInput(dragEnabled, selectionTravelPx) {
          if (!dragEnabled) return@pointerInput
          detectHorizontalDragGestures(
            onDragStart = {
              onInteractionStart()
              dragPosition = selectionPosition().coerceIn(0f, RootTab.entries.lastIndex.toFloat())
            },
            onHorizontalDrag = { change, dragAmount ->
              change.consume()
              val updated =
                ((dragPosition ?: selectionPosition()) + dragAmount / selectionTravelPx).coerceIn(
                  0f,
                  RootTab.entries.lastIndex.toFloat(),
                )
              dragPosition = updated
              onSelectionDrag(updated)
            },
            onDragEnd = ::settleDrag,
            onDragCancel = ::settleDrag,
          )
        }
    ) {
      val layer = backdropLayer.takeIf { glassEffectsEnabled }
      if (layer != null && contentBounds.width > 0f && contentBounds.height > 0f) {
        Canvas(Modifier.matchParentSize().clip(CircleShape).blur(14.dp)) {
          translate(left = -contentBounds.left, top = -contentBounds.top) {
            drawLayer(layer)
          }
        }
      }
      Box(
        Modifier.matchParentSize()
          .clip(CircleShape)
          .background(
            MaterialTheme.colorScheme.surface.copy(alpha = if (glassEffectsEnabled) .48f else .97f)
          )
      )
      Box(
        Modifier.padding(6.dp).size(width = 426.dp, height = 68.dp).onSizeChanged {
          selectionTravelPx = (it.width / 3f).coerceAtLeast(1f)
        }
      ) {
        Box(
          Modifier.width(142.dp)
            .fillMaxHeight()
            .graphicsLayer {
              translationX =
                size.width *
                  (dragPosition ?: selectionPosition()).coerceIn(
                    0f,
                    RootTab.entries.lastIndex.toFloat(),
                  )
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .14f))
        )
        Row(Modifier.fillMaxSize()) {
          CapsuleItem(
            RootTab.HOME,
            selected,
            onSelected,
            onControlSelected,
            onInteractionStart,
            onInteractionEnd,
            Modifier.weight(1f)
              .then(
                if (initialFocusRequester != null && selected == RootTab.HOME)
                  Modifier.focusRequester(initialFocusRequester)
                else Modifier
              ),
            focusEnabled,
          )
          CapsuleItem(
            RootTab.BANGUMI,
            selected,
            onSelected,
            onControlSelected,
            onInteractionStart,
            onInteractionEnd,
            Modifier.weight(1f)
              .then(
                if (initialFocusRequester != null && selected == RootTab.BANGUMI)
                  Modifier.focusRequester(initialFocusRequester)
                else Modifier
              ),
            focusEnabled,
          )
          CapsuleItem(
            RootTab.MY,
            selected,
            onSelected,
            onControlSelected,
            onInteractionStart,
            onInteractionEnd,
            Modifier.weight(1f)
              .then(
                if (initialFocusRequester != null && selected == RootTab.MY)
                  Modifier.focusRequester(initialFocusRequester)
                else Modifier
              ),
            focusEnabled,
          )
        }
      }
    }
  }
}

@Composable
private fun CapsuleItem(
  tab: RootTab,
  selected: RootTab,
  onSelected: (RootTab) -> Unit,
  onControlSelected: (RootTab) -> Unit,
  onInteractionStart: () -> Unit,
  onInteractionEnd: () -> Unit,
  modifier: Modifier,
  focusEnabled: Boolean = true,
) {
  val controlMode = LocalControlMode.current
  val controlFocusVisible = LocalControlFocusVisible.current
  val active = tab == selected
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val focused by interactionSource.collectIsFocusedAsState()
  var pressWasActive by remember { mutableStateOf(false) }
  LaunchedEffect(pressed) {
    if (pressed) {
      pressWasActive = true
      onInteractionStart()
    } else if (pressWasActive) {
      pressWasActive = false
      // 取消的按压不会调用 onClick，但它仍必须释放播放封面。
      onInteractionEnd()
    }
  }
  Column(
    modifier =
      modifier
        .fillMaxHeight()
        .focusProperties {
          canFocus = focusEnabled
          if (controlMode) {
            up = FocusRequester.Cancel
            down = FocusRequester.Cancel
          }
        }
        .then(
          if (controlMode) {
            Modifier.onPreviewKeyEvent { event ->
              if (!focusEnabled || !isControlConfirmKey(event.nativeKeyEvent.keyCode)) {
                return@onPreviewKeyEvent false
              }
              if (event.type == KeyEventType.KeyUp) {
                onControlSelected(tab)
                onInteractionEnd()
              }
              true
            }
          } else Modifier
        )
        .graphicsLayer {
          val focusScale = if (focused && controlFocusVisible) 1.045f else 1f
          scaleX = focusScale
          scaleY = focusScale
        }
        .clip(CircleShape)
        .background(
          if (focused && controlFocusVisible)
            MaterialTheme.colorScheme.primary.copy(alpha = .16f)
          else Color.Transparent
        )
        .border(
          width = if (focused && controlFocusVisible) 2.dp else 0.dp,
          color =
            if (focused && controlFocusVisible) MaterialTheme.colorScheme.primary
            else Color.Transparent,
          shape = CircleShape,
        )
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = {
            onSelected(tab)
            onInteractionEnd()
          },
        ),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      when (tab) {
        RootTab.HOME -> Icons.Default.Home
        RootTab.BANGUMI -> BangumiTvIcon
        RootTab.MY -> Icons.Default.Person
      },
      null,
      modifier = Modifier.size(27.dp),
      tint =
        if (active) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      when (tab) {
        RootTab.HOME -> "首页"
        RootTab.BANGUMI -> "番剧"
        RootTab.MY -> "我的"
      },
      style = MaterialTheme.typography.labelLarge,
      color =
        if (active) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
