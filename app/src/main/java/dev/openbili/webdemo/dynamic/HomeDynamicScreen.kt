package dev.openbili.webdemo.dynamic

/**
 * 首页「动态」Tab 的组合界面。
 *
 * 负责渲染左侧 UP 主选择栏、顶部「动态 / 仅视频」头部条，以及复用 [ProfileDynamicGrid]
 * 呈现的动态内容网格。页面自身不持有业务状态：数据、加载与错误状态全部来自
 * [HomeDynamicUiState]，用户交互（选择 UP 主、切换仅视频、加载更多、点赞、打开
 * 视频/文章/评论头像等）一律以回调形式上抛给宿主，由其转发到 [HomeDynamicViewModel]。
 *
 * 本文件还包含遥控器按键解析 [resolveDynamicUploaderControlAction]，以及两个私有
 * 组件 [DynamicUploaderRail]（左侧栏）与 [UploaderRailItem]（栏内条目）。
 */

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.HomeDynamicUploader
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.live.LiveSearchRoom
import dev.openbili.webdemo.profile.ProfileDynamicGrid
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.BackdropGlassSurface
import dev.openbili.webdemo.ui.HomeGlassTokens
import dev.openbili.webdemo.ui.LocalNavigationTopClearance
import dev.openbili.webdemo.ui.LocalControlFocusVisible
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.ui.isControlConfirmKey
import dev.openbili.webdemo.video.CommentProfileAnchor

/**
 * 遥控器按键作用于 UP 主栏条目时的处理结果。
 *
 * [NONE] 表示该按键与列表无关（不消费事件）；[CONSUME] 表示列表消费该按键但不触发
 * 动作（如方向键移动焦点、确认键按下未抬起）；[SELECT_AND_ENTER_CONTENT] 表示确认键
 * 抬起，应选中该 UP 主并进入其内容区。
 */
internal enum class DynamicUploaderControlAction {
  NONE,
  CONSUME,
  SELECT_AND_ENTER_CONTENT,
}

/**
 * 将一次按键解析为 UP 主栏可识别的控制动作。
 *
 * 为什么需要单独解析：焦点处于 UP 主栏时，方向键应被拦截以防焦点逃离栏位；而确认键
 * 需区分「按下」（仅消费，避免一次按压触发两次）与「抬起」（真正进入内容）。当控件
 * 被禁用时一律返回 [DynamicUploaderControlAction.NONE]，让事件回落到默认处理链路。
 */
internal fun resolveDynamicUploaderControlAction(
  keyCode: Int,
  keyUp: Boolean,
  controlEnabled: Boolean,
): DynamicUploaderControlAction {
  if (!controlEnabled) return DynamicUploaderControlAction.NONE
  if (isControlConfirmKey(keyCode)) {
    return if (keyUp) DynamicUploaderControlAction.SELECT_AND_ENTER_CONTENT
    else DynamicUploaderControlAction.CONSUME
  }
  return if (
    keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
  ) {
    DynamicUploaderControlAction.CONSUME
  } else {
    DynamicUploaderControlAction.NONE
  }
}

/**
 * 首页「动态」页面的主组合体。
 *
 * 页面结构分三块：中央的动态内容网格（复用 [ProfileDynamicGrid]）、左侧 UP 主选择栏
 * [DynamicUploaderRail]、顶部「动态 / 仅视频」头部条。三者基于 [BackdropGlassSurface]
 * 与玻璃令牌实现统一的磨砂视觉，并共享 [backdropLayer] 快照与边界以支撑后续转场。
 *
 * 该组合体不直接发网络请求：所有数据、加载/错误状态都来自 [state]，交互通过 on*
 * 回调交给宿主（通常转发到 [HomeDynamicViewModel]）。焦点与遥控器相关逻辑由
 * [pageControlsEnabled]/[contentControlsEnabled] 两套开关隔离控制。
 */
@Composable
fun HomeDynamicScreen(
  state: HomeDynamicUiState,
  accountMid: Long,
  settings: AppSettings,
  onSelectUploader: (Long?) -> Unit,
  onVideoOnlyChange: (Boolean) -> Unit,
  onSelectDynamic: (String?) -> Unit,
  onRefresh: () -> Unit,
  onLoadMore: () -> Unit,
  onLoadMoreUploaders: () -> Unit,
  onLike: (SpaceDynamicItem, Long) -> Unit,
  onVideoClick: (SpaceDynamicItem, FeedItem, Rect) -> Unit,
  onVideoLongClick: (FeedItem) -> Unit,
  onLiveClick: (LiveSearchRoom, Rect) -> Unit = { _, _ -> },
  hiddenDynamicId: String?,
  hiddenLiveCoverItemId: String? = null,
  onVideoBoundsChanged: (String, Rect) -> Unit,
  onLiveBoundsChanged: (LiveSearchRoom, Rect) -> Unit = { _, _ -> },
  onArticleClick: (ArticleItem, Rect) -> Unit,
  hiddenArticleItemId: String?,
  onArticleBoundsChanged: (ArticleItem, Rect) -> Unit,
  onCommentProfileClick: (Long, CommentItem, CommentProfileAnchor) -> Unit,
  onAvatarProfileClick: (Long, String?, String?, Rect) -> Unit,
  hiddenCommentAvatarRpid: Long?,
  hiddenAvatarSourceBounds: Rect?,
  backHandlingEnabled: Boolean,
  backdropCaptureEnabled: Boolean,
  backdropLayer: GraphicsLayer,
  onBackdropBoundsChanged: (Rect) -> Unit,
  underlayLayer: GraphicsLayer,
  underlayBounds: Rect,
  topContentPadding: Dp,
  onDetailOverlayActiveChanged: (Boolean) -> Unit,
  pageControlsEnabled: Boolean = false,
  contentControlsEnabled: Boolean = false,
  onControlEnterContent: () -> Unit = {},
  onControlFocusFeed: () -> Unit = {},
  initialFocusRequester: FocusRequester? = null,
) {
  // ── 布局常量与玻璃配色令牌：栏宽、页头高度及按主题切换的透明度 ──────────
  val railWidth = 232.dp
  val pageHeaderHeight = 58.dp
  val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
  val glassContainerColor =
    MaterialTheme.colorScheme.surface.copy(
      alpha =
        if (darkTheme) HomeGlassTokens.DarkContainerAlpha else HomeGlassTokens.LightContainerAlpha
    )
  val glassBorder =
    BorderStroke(
      .75.dp,
      MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (darkTheme) HomeGlassTokens.DarkBorderAlpha else HomeGlassTokens.LightBorderAlpha
      ),
    )
  // ── 本地状态：内容区边界（供玻璃快照）、详情浮层激活位、焦点对象 ──────────
  var contentBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var detailOverlayActive by remember { mutableStateOf(false) }
  val videoOnlyFocusRequester = remember { FocusRequester() }
  val uploaderEntryFocusRequester = remember { FocusRequester() }
  val selectedUploaderFocusRequester = remember { FocusRequester() }
  var videoOnlyFocused by remember { mutableStateOf(false) }
  val controlFocusVisible = LocalControlFocusVisible.current
  // ── 控件启用后延迟一帧再抢占焦点，避开组合期的焦点竞争 ────────────────────
  LaunchedEffect(pageControlsEnabled, state.selectedMid) {
    if (pageControlsEnabled) {
      androidx.compose.runtime.withFrameNanos {}
      runCatching {
        if (state.selectedMid == null) uploaderEntryFocusRequester.requestFocus()
        else if (!selectedUploaderFocusRequester.requestFocus()) {
          uploaderEntryFocusRequester.requestFocus()
        }
      }
    }
  }
  val chromeAnimationDuration = if (settings.reduceMotion) 90 else 220
  // ── 根容器：先铺内容网格，再叠左侧栏与顶部头部条（chrome）──────────────────
  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize()
        .onGloballyPositioned {
          contentBackdropBounds = it.boundsInRoot()
          onBackdropBoundsChanged(contentBackdropBounds)
        }
        .drawWithContent {
          if (backdropCaptureEnabled) {
            backdropLayer.record { this@drawWithContent.drawContent() }
            drawLayer(backdropLayer)
          } else {
            drawContent()
          }
        }
    ) {
      // ── 中央内容网格：下拉刷新包裹 ProfileDynamicGrid，详情浮层激活时禁用 ──
      PullRefreshContainer(
        refreshing = state.loading && state.selectedDynamicId == null,
        onRefresh = { if (state.selectedDynamicId == null) onRefresh() },
        enabled = state.selectedDynamicId == null && !detailOverlayActive,
        indicatorTopPadding = topContentPadding + pageHeaderHeight + 8.dp,
        modifier = Modifier.fillMaxSize(),
      ) {
        CompositionLocalProvider(
          LocalNavigationTopClearance provides (topContentPadding + pageHeaderHeight + 10.dp)
        ) {
          // 复用个人页的动态网格；scrollToTopKey 随所选 UP 主切换触发回到顶部，
          // revealBatchKey 按「UP 主 + 仅视频」分区，避免跨区复用入场动画批次。
          ProfileDynamicGrid(
            items = state.items,
            searchQuery = "",
            loading = state.loading || state.loadingMore,
            hasMore = state.hasMore,
            error = state.error,
            selectedDynamicId = state.selectedDynamicId,
            profile = null,
            currentAccountMid = accountMid,
            settings = settings,
            showFilterRow = false,
            allowManagement = false,
            gridHorizontalPadding = 12.dp,
            gridStartPadding = railWidth + 12.dp,
            gridTopPadding = topContentPadding + pageHeaderHeight + 10.dp,
            detailTopPadding = topContentPadding,
            scrollToTopKey = state.selectedMid,
            revealBatchKey = "home-dynamic:${state.selectedMid}:${state.videoOnly}",
            onDetailOverlayActiveChanged = { active ->
              detailOverlayActive = active
              onDetailOverlayActiveChanged(active)
            },
            onVideoClick = { video, bounds ->
              state.items
                .firstOrNull { it.id == state.selectedDynamicId }
                ?.let { dynamic -> onVideoClick(dynamic, video, bounds) }
            },
            onVideoLongClick = onVideoLongClick,
            onLiveClick = onLiveClick,
            hiddenCoverItemId =
              state.selectedDynamicId
                .takeIf { it != null && it == hiddenDynamicId }
                ?.let { state.items.firstOrNull { item -> item.id == it }?.video?.bvid },
            hiddenLiveCoverItemId = hiddenLiveCoverItemId,
            onVideoBoundsChanged = { _, bounds ->
              state.selectedDynamicId?.let { onVideoBoundsChanged(it, bounds) }
            },
            onLiveBoundsChanged = onLiveBoundsChanged,
            onArticleClick = onArticleClick,
            hiddenArticleItemId = hiddenArticleItemId,
            onArticleBoundsChanged = onArticleBoundsChanged,
            onCommentProfileClick = onCommentProfileClick,
            onAvatarProfileClick = onAvatarProfileClick,
            hiddenCommentAvatarRpid = hiddenCommentAvatarRpid,
            hiddenAvatarSourceBounds = hiddenAvatarSourceBounds,
            backHandlingEnabled = backHandlingEnabled,
            onSelectedDynamicIdChange = onSelectDynamic,
            onDynamicLike = { onLike(it, accountMid) },
            onDynamicDelete = {},
            onDynamicPin = {},
            onLoadMore = onLoadMore,
            onScrollStarted = {},
            onControlExitUp = {
              if (contentControlsEnabled) {
                runCatching { videoOnlyFocusRequester.requestFocus() }
              }
            },
            initialFocusRequester = initialFocusRequester,
          )
        }
      }
    }
    // ── 左侧 UP 主栏：详情浮层激活时淡出并左滑隐藏 ──────────────────────────
    AnimatedVisibility(
      visible = !detailOverlayActive,
      modifier =
        Modifier.align(Alignment.TopStart)
          .padding(top = topContentPadding + 12.dp, bottom = 14.dp)
          .widthIn(min = railWidth, max = railWidth)
          .fillMaxHeight(),
      enter =
        fadeIn(tween(chromeAnimationDuration)) +
          slideInHorizontally(tween(chromeAnimationDuration)) { -it / 5 },
      exit =
        fadeOut(tween(chromeAnimationDuration)) +
          slideOutHorizontally(tween(chromeAnimationDuration)) { -it / 5 },
    ) {
      DynamicUploaderRail(
        uploaders = state.uploaders,
        selectedMid = state.selectedMid,
        onSelected = onSelectUploader,
        hasMore = state.uploadersHaveMore,
        loadingMore = state.uploadersLoadingMore,
        onLoadMore = onLoadMoreUploaders,
        controlEnabled = pageControlsEnabled,
        entryFocusRequester = uploaderEntryFocusRequester,
        selectedFocusRequester = selectedUploaderFocusRequester,
        onControlEnterContent = onControlEnterContent,
        backdropLayer = backdropLayer,
        backdropBounds = contentBackdropBounds,
        underlayLayer = underlayLayer,
        underlayBounds = underlayBounds,
        modifier = Modifier.fillMaxSize(),
      )
    }
    // ── 顶部头部条：标题、错误提示与「仅视频」过滤芯片 ──────────────────────
    AnimatedVisibility(
      visible = !detailOverlayActive,
      modifier =
        Modifier.align(Alignment.TopStart)
          .padding(start = railWidth, top = topContentPadding)
          .fillMaxWidth()
          .height(pageHeaderHeight),
      enter =
        fadeIn(tween(chromeAnimationDuration)) +
          slideInVertically(tween(chromeAnimationDuration)) { -it / 4 },
      exit =
        fadeOut(tween(chromeAnimationDuration)) +
          slideOutVertically(tween(chromeAnimationDuration)) { -it / 4 },
    ) {
      BackdropGlassSurface(
        backdropLayer = backdropLayer,
        backdropBounds = contentBackdropBounds,
        underlayLayer = underlayLayer,
        underlayBounds = underlayBounds,
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        blurRadius = HomeGlassTokens.BlurRadius,
        containerColor = glassContainerColor,
        border = glassBorder,
        shadowElevation = 0.dp,
      ) {
        Row(
          Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("动态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Spacer(Modifier.weight(1f))
          state.error
            ?.takeIf { state.items.isNotEmpty() }
            ?.let {
              Text(
                it,
                modifier = Modifier.padding(end = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          // 「仅视频」芯片：内容区焦点入口。四向焦点被 Cancel 隔离以充当栏位边界，
          // 仅向下键把焦点移交回内容网格（onControlFocusFeed）。
          FilterChip(
            selected = state.videoOnly,
            onClick = { onVideoOnlyChange(!state.videoOnly) },
            label = { Text("仅视频") },
            modifier =
              Modifier.focusRequester(videoOnlyFocusRequester)
                .focusProperties {
                  canFocus = contentControlsEnabled
                  left = FocusRequester.Cancel
                  right = FocusRequester.Cancel
                  up = FocusRequester.Cancel
                  down = FocusRequester.Cancel
                }
                .onFocusChanged { videoOnlyFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                  val keyCode = event.nativeKeyEvent.keyCode
                  if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT &&
                    keyCode != KeyEvent.KEYCODE_DPAD_RIGHT &&
                    keyCode != KeyEvent.KEYCODE_DPAD_UP &&
                    keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                  ) {
                    return@onPreviewKeyEvent false
                  }
                  if (
                    event.type == KeyEventType.KeyDown &&
                      event.nativeKeyEvent.repeatCount == 0
                  ) {
                    when (keyCode) {
                      KeyEvent.KEYCODE_DPAD_DOWN -> onControlFocusFeed()
                    }
                  }
                  true
                }
                .border(
                  width = if (videoOnlyFocused && controlFocusVisible) 3.dp else 0.dp,
                  color =
                    if (videoOnlyFocused && controlFocusVisible) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                  shape = RoundedCornerShape(12.dp),
                ),
          )
        }
      }
    }
  }
}

/**
 * 左侧 UP 主选择栏：毛玻璃底 + 纵向列表。
 *
 * 首项固定为「所有」（[selectedMid] 为 null 时选中），其后是关注 UP 主列表。列表滚到
 * 末尾且还有更多数据时，通过 [onLoadMore] 触发下一页（惰性触底加载）。控件模式开启
 * 时整栏声明为焦点组并拦截退出焦点，避免遥控器焦点外逸到内容网格。
 */
@Composable
private fun DynamicUploaderRail(
  uploaders: List<HomeDynamicUploader>,
  selectedMid: Long?,
  onSelected: (Long?) -> Unit,
  hasMore: Boolean,
  loadingMore: Boolean,
  onLoadMore: () -> Unit,
  controlEnabled: Boolean,
  entryFocusRequester: FocusRequester,
  selectedFocusRequester: FocusRequester,
  onControlEnterContent: () -> Unit,
  backdropLayer: GraphicsLayer,
  backdropBounds: Rect,
  underlayLayer: GraphicsLayer,
  underlayBounds: Rect,
  modifier: Modifier = Modifier,
) {
  val darkTheme = MaterialTheme.colorScheme.background.luminance() < .5f
  BackdropGlassSurface(
    backdropLayer = backdropLayer,
    backdropBounds = backdropBounds,
    underlayLayer = underlayLayer,
    underlayBounds = underlayBounds,
    modifier = modifier,
    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
    blurRadius = HomeGlassTokens.BlurRadius,
    containerColor =
      MaterialTheme.colorScheme.surface.copy(
        alpha =
          if (darkTheme) HomeGlassTokens.DarkContainerAlpha else HomeGlassTokens.LightContainerAlpha
      ),
    border =
      BorderStroke(
        .75.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(
          alpha =
            if (darkTheme) HomeGlassTokens.DarkBorderAlpha else HomeGlassTokens.LightBorderAlpha
        ),
      ),
    shadowElevation = 0.dp,
  ) {
    // ── UP 主列表：「所有」首项 + 关注列表 + 触底加载分页 ──────────────────
    LazyColumn(
      modifier =
        Modifier.fillMaxSize()
          .then(
            if (controlEnabled) {
              Modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
            } else Modifier
          ),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // 「所有」：固定首项，选中即回到全部关注动态
      item(key = "all") {
        UploaderRailItem(
          mid = null,
          name = "所有",
          face = "",
          selected = selectedMid == null,
          live = false,
          hasUpdate = false,
          controlEnabled = controlEnabled,
          focusRequester = entryFocusRequester,
          onControlEnterContent = onControlEnterContent,
          onClick = { onSelected(null) },
        )
      }
      items(uploaders, key = HomeDynamicUploader::mid) { uploader ->
        UploaderRailItem(
          mid = uploader.mid,
          name = uploader.name,
          face = uploader.face,
          selected = selectedMid == uploader.mid,
          live = uploader.live,
          hasUpdate = uploader.hasUpdate,
          controlEnabled = controlEnabled,
          focusRequester = selectedFocusRequester.takeIf { selectedMid == uploader.mid },
          onControlEnterContent = onControlEnterContent,
          onClick = { onSelected(uploader.mid) },
        )
        // 触底加载：当最后一项进入组合且尚未在加载时，请求下一页
        if (hasMore && uploader == uploaders.lastOrNull()) {
          androidx.compose.runtime.LaunchedEffect(uploader.mid, loadingMore) {
            if (!loadingMore) onLoadMore()
          }
        }
      }
      if (loadingMore) {
        item(key = "loading_more_uploaders") {
          Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
          }
        }
      }
    }
  }
}

/**
 * UP 主栏的单个条目。
 *
 * 以 [VideoCardGradient] 用头像取色作为卡片渐变底，左侧头像、中间名字与「直播中」
 * 状态、右侧更新小圆点。选中态与焦点态用不同描边宽度区分，点击即选中该 UP 主；控件
 * 模式下确认键抬起会选中并进入内容区（见 [resolveDynamicUploaderControlAction]）。
 */
@Composable
private fun UploaderRailItem(
  mid: Long?,
  name: String,
  face: String,
  selected: Boolean,
  live: Boolean,
  hasUpdate: Boolean,
  controlEnabled: Boolean,
  focusRequester: FocusRequester? = null,
  onControlEnterContent: () -> Unit,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(16.dp)
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  VideoCardGradient(
    coverUrl = face,
    loadKey = "home-dynamic-uploader:${mid ?: "all"}",
    modifier = Modifier.fillMaxWidth().clip(shape),
    prioritizePaletteLoad = true,
    paletteRequestWidth = 84,
    paletteRequestHeight = 84,
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
          .focusProperties { canFocus = controlEnabled }
          .onPreviewKeyEvent { event ->
            when (
              resolveDynamicUploaderControlAction(
                keyCode = event.nativeKeyEvent.keyCode,
                keyUp = event.type == KeyEventType.KeyUp,
                controlEnabled = controlEnabled,
              )
            ) {
              DynamicUploaderControlAction.SELECT_AND_ENTER_CONTENT -> {
                onClick()
                onControlEnterContent()
                true
              }
              DynamicUploaderControlAction.CONSUME -> true
              DynamicUploaderControlAction.NONE -> false
            }
          }
          .then(
            if (focused)
              Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
            else if (selected)
              Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .7f), shape)
            else Modifier
          )
          .clip(shape)
          .background(
            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .22f)
            else androidx.compose.ui.graphics.Color.Transparent
          )
          .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick,
          )
          .padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // 无头像时（「所有」入口）用主色圆形 + 「全」字占位
      if (face.isBlank()) {
        Box(
          Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Text("全", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
      } else {
        AvatarImage(
          face = face,
          contentDescription = name,
          requestSize = 84,
          modifier = Modifier.size(42.dp).clip(CircleShape),
        )
      }
      Column(Modifier.padding(start = 10.dp).weight(1f)) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (live) {
          Text(
            "直播中",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }
      // 更新提示点：该 UP 主有未读动态
      if (hasUpdate) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
      }
    }
  }
}
