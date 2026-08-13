package dev.openbili.webdemo.dynamic

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openbili.webdemo.api.ArticleItem
import dev.openbili.webdemo.api.CommentItem
import dev.openbili.webdemo.api.HomeDynamicUploader
import dev.openbili.webdemo.api.SpaceDynamicItem
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.profile.ProfileDynamicGrid
import dev.openbili.webdemo.settings.AppSettings
import dev.openbili.webdemo.ui.AvatarImage
import dev.openbili.webdemo.ui.BackdropGlassSurface
import dev.openbili.webdemo.ui.HomeGlassTokens
import dev.openbili.webdemo.ui.LocalNavigationTopClearance
import dev.openbili.webdemo.ui.PullRefreshContainer
import dev.openbili.webdemo.ui.VideoCardGradient
import dev.openbili.webdemo.video.CommentProfileAnchor

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
  hiddenDynamicId: String?,
  onVideoBoundsChanged: (String, Rect) -> Unit,
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
) {
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
  var contentBackdropBounds by remember { mutableStateOf(Rect.Zero) }
  var detailOverlayActive by remember { mutableStateOf(false) }
  val chromeAnimationDuration = if (settings.reduceMotion) 90 else 220
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
      PullRefreshContainer(
        refreshing = state.loading && state.selectedDynamicId == null,
        onRefresh = { if (state.selectedDynamicId == null) onRefresh() },
        enabled = state.selectedDynamicId == null && !detailOverlayActive,
        indicatorTopPadding = topContentPadding + pageHeaderHeight + 8.dp,
        modifier = Modifier.fillMaxSize(),
      ) {
        CompositionLocalProvider(
          LocalNavigationTopClearance provides
            (topContentPadding + pageHeaderHeight + 10.dp)
        ) {
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
          hiddenCoverItemId =
            state.selectedDynamicId
              .takeIf { it != null && it == hiddenDynamicId }
              ?.let { state.items.firstOrNull { item -> item.id == it }?.video?.bvid },
          onVideoBoundsChanged = { _, bounds ->
            state.selectedDynamicId?.let { onVideoBoundsChanged(it, bounds) }
          },
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
          )
        }
      }
    }
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
        backdropLayer = backdropLayer,
        backdropBounds = contentBackdropBounds,
        underlayLayer = underlayLayer,
        underlayBounds = underlayBounds,
        modifier = Modifier.fillMaxSize(),
      )
    }
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
          FilterChip(
            selected = state.videoOnly,
            onClick = { onVideoOnlyChange(!state.videoOnly) },
            label = { Text("仅视频") },
          )
        }
      }
    }
  }
}

@Composable
private fun DynamicUploaderRail(
  uploaders: List<HomeDynamicUploader>,
  selectedMid: Long?,
  onSelected: (Long?) -> Unit,
  hasMore: Boolean,
  loadingMore: Boolean,
  onLoadMore: () -> Unit,
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
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      item(key = "all") {
        UploaderRailItem(
          mid = null,
          name = "所有",
          face = "",
          selected = selectedMid == null,
          live = false,
          hasUpdate = false,
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
          onClick = { onSelected(uploader.mid) },
        )
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

@Composable
private fun UploaderRailItem(
  mid: Long?,
  name: String,
  face: String,
  selected: Boolean,
  live: Boolean,
  hasUpdate: Boolean,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(16.dp)
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
          .then(
            if (selected)
              Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .7f), shape)
            else Modifier
          )
          .clip(shape)
          .background(
            if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .22f)
            else androidx.compose.ui.graphics.Color.Transparent
          )
          .clickable(onClick = onClick)
          .padding(horizontal = 10.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
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
      if (hasUpdate) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
      }
    }
  }
}
