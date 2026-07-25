package dev.openbili.webdemo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.openbili.webdemo.R
import dev.openbili.webdemo.bangumi.BangumiExploreViewModel
import dev.openbili.webdemo.bangumi.BangumiPreviewPlayerState
import dev.openbili.webdemo.bangumi.BangumiPreviewPlayerViewModel
import dev.openbili.webdemo.bangumi.BangumiRecommendationUiState
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiRecommendation
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.BangumiSection
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal data class BangumiPreviewTarget(
  val season: BangumiSeason,
  val episode: BangumiEpisode,
  val item: FeedItem,
  val isPromotional: Boolean,
)

internal data class BangumiPreviewCoverBlend(
  val fromCoverUrl: String,
  val toCoverUrl: String,
  val progress: Float,
)

internal fun bangumiPreviewCoverCacheKey(coverUrl: String): String =
  "bangumi-preview-cover-1600:$coverUrl"

internal fun bangumiRecommendationIndexForSettledPage(
  settledPage: Int,
  itemCount: Int,
): Int = if (itemCount > 0) Math.floorMod(settledPage, itemCount) else 0

internal fun shouldCommitBangumiPreview(
  targetAvailable: Boolean,
  coverReady: Boolean,
): Boolean = targetAvailable && coverReady

internal fun bangumiPreloadDetailsSettled(
  items: List<BangumiRecommendation>,
  seasons: Map<String, BangumiSeason>,
  errors: Map<String, String>,
): Boolean = items.filterNot(BangumiRecommendation::isLive).all { it.stableId in seasons || it.stableId in errors }

internal fun shouldHideBangumiRecommendationCard(cardId: String?, hiddenCardId: String?): Boolean =
  cardId != null && hiddenCardId != null && cardId == hiddenCardId

/**
 * A detail page is drawn above the retained Bangumi root during a card return. Its Hero is still a
 * stable source layer, even though that root is intentionally inactive for input and preview
 * playback.
 */
internal fun shouldRetainBangumiHeroVisuals(
  active: Boolean,
  preloadEnabled: Boolean,
  retainedForDetailReturn: Boolean,
): Boolean = active || preloadEnabled || retainedForDetailReturn

/** Returns null before touch slop, then locks the card gesture to its dominant direction. */
internal fun bangumiCardGestureIsHorizontal(displacement: Offset, touchSlop: Float): Boolean? {
  if (displacement.getDistance() < touchSlop) return null
  return displacement.x.absoluteValue > displacement.y.absoluteValue
}

private const val PreviewPriorityPromo = 0
private const val PreviewPriorityMusicVideo = 1
private const val PreviewPriorityHighlight = 2
private const val PreviewPriorityNextEpisode = 3

internal fun selectBangumiPreviewEpisode(sections: List<BangumiSection>): BangumiEpisode? {
  for (priority in PreviewPriorityPromo..PreviewPriorityNextEpisode) {
    sections.forEach { section ->
      section.episodes.firstOrNull { episode ->
        bangumiPreviewPriority(section, episode) == priority
      }?.let { return it }
    }
  }
  return null
}

private fun bangumiPreviewPriority(
  section: BangumiSection,
  episode: BangumiEpisode,
): Int? {
  if (!episode.isPlayableBangumiPreview()) return null
  val sectionLabel = section.title.normalizedBangumiPreviewLabel()
  val episodeLabel = "${episode.title} ${episode.longTitle}".normalizedBangumiPreviewLabel()
  val sectionHasBehindScenes = sectionLabel.contains("花絮")
  val episodeHasBehindScenes = episodeLabel.contains("花絮")
  if (episodeHasBehindScenes) return null

  val sectionPromo = sectionLabel.isBangumiPromoLabel()
  val sectionMusicVideo = sectionLabel.isBangumiMusicVideoLabel()
  val sectionHighlight = sectionLabel.isBangumiHighlightLabel()
  val sectionNextEpisode = sectionLabel.isBangumiNextEpisodePreviewLabel()
  // A purely behind-the-scenes group is never a preview source. Mixed groups such as
  // “PV＆花絮” still allow an item whose own title positively identifies it as a PV or MV.
  val sectionIsOnlyBehindScenes =
    sectionHasBehindScenes &&
      !sectionPromo &&
      !sectionMusicVideo &&
      !sectionHighlight &&
      !sectionNextEpisode
  if (sectionIsOnlyBehindScenes) return null

  return when {
    episodeLabel.isBangumiNextEpisodePreviewLabel() ||
      (!sectionHasBehindScenes && sectionNextEpisode) -> PreviewPriorityNextEpisode
    episodeLabel.isBangumiPromoLabel() || (!sectionHasBehindScenes && sectionPromo) ->
      PreviewPriorityPromo
    episodeLabel.isBangumiMusicVideoLabel() || (!sectionHasBehindScenes && sectionMusicVideo) ->
      PreviewPriorityMusicVideo
    episodeLabel.isBangumiHighlightLabel() || (!sectionHasBehindScenes && sectionHighlight) ->
      PreviewPriorityHighlight
    else -> null
  }
}

private fun String.normalizedBangumiPreviewLabel(): String =
  lowercase().replace(Regex("[\\s　]"), "")

private fun String.isBangumiPromoLabel(): Boolean =
  contains("pv") ||
    contains("预告") ||
    contains("宣传") ||
    contains("先导") ||
    contains("先行") ||
    contains("定档") ||
    contains("终极") ||
    contains("teaser") ||
    contains("trailer")

private fun String.isBangumiMusicVideoLabel(): Boolean = contains("mv") || contains("音乐视频")

private fun String.isBangumiHighlightLabel(): Boolean =
  contains("精彩") ||
    contains("高光") ||
    contains("看点") ||
    contains("片段") ||
    contains("名场面") ||
    contains("爽燃") ||
    contains("速看") ||
    contains("高能") ||
    contains("片花")

private fun String.isBangumiNextEpisodePreviewLabel(): Boolean =
  contains("预告") &&
    (
      contains("下集") ||
        contains("下期") ||
        contains("下一集") ||
        contains("下回") ||
        Regex("第[0-9一二三四五六七八九十百零〇]+集预告").containsMatchIn(this)
    )

private fun BangumiEpisode.isPlayableBangumiPreview(): Boolean =
  id > 0L && bvid.isNotBlank() && cid > 0L

internal fun selectBangumiMainEpisode(episodes: List<BangumiEpisode>): BangumiEpisode? =
  episodes.firstOrNull(BangumiEpisode::isPlayableBangumiPreview)

internal fun selectBangumiAutoplayEpisode(
  sections: List<BangumiSection>,
  episodes: List<BangumiEpisode>,
): BangumiEpisode? = selectBangumiPreviewEpisode(sections) ?: selectBangumiMainEpisode(episodes)

internal fun recommendationPlaybackCover(
  item: BangumiRecommendation,
  season: BangumiSeason?,
): String =
  season
    ?.let { selectBangumiAutoplayEpisode(it.sections, it.episodes) }
    ?.coverUrl
    ?.takeIf(String::isNotBlank)
    ?: season?.coverUrl?.takeIf(String::isNotBlank)
    ?: item.cardUrl

internal fun recommendationMainEpisodeCover(
  item: BangumiRecommendation,
  season: BangumiSeason?,
): String =
  season
    ?.let { selectBangumiMainEpisode(it.episodes) }
    ?.coverUrl
    ?.takeIf(String::isNotBlank)
    ?: season?.coverUrl?.takeIf(String::isNotBlank)
    ?: item.cardUrl

@Composable
internal fun BangumiRecommendationScreen(
  exploreViewModel: BangumiExploreViewModel,
  active: Boolean,
  preloadEnabled: Boolean,
  retainedForDetailReturn: Boolean = false,
  previewMuted: Boolean,
  hiddenCardId: String?,
  currentAccountMid: Long,
  state: BangumiRecommendationUiState,
  onRefresh: () -> Unit,
  onSelect: (String) -> Unit,
  onRequireDetails: (List<String>) -> Unit,
  onRetryDetail: (String) -> Unit,
  onPreviewChanged: (BangumiPreviewTarget?) -> Unit,
  onPreloadReady: () -> Unit,
  onTogglePreviewMute: () -> Unit,
  onOpenMainEpisode: (SpaceContentCard, FeedItem, Rect) -> Unit,
  onOpenExploreLandscape: (dev.openbili.webdemo.api.BangumiExploreItem, Rect) -> Unit,
  onOpenExplorePoster: (dev.openbili.webdemo.api.BangumiExploreItem, Rect) -> Unit,
  onOpenIndex: (Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  val items = state.items
  val seasons = state.seasons
  val errors = state.detailErrors
  val pageCount = items.size
  val previewPlayerViewModel: BangumiPreviewPlayerViewModel = viewModel()
  val previewPlayerState by previewPlayerViewModel.state.collectAsState()
  var interactionActive by remember { mutableStateOf(false) }
  var previewCardGestureActive by remember { mutableStateOf(false) }
  var previewCoverBlend by remember { mutableStateOf<BangumiPreviewCoverBlend?>(null) }
  var readyPreviewCoverKeys by remember { mutableStateOf(emptySet<String>()) }
  var readyBackgroundKeys by remember { mutableStateOf(emptySet<String>()) }
  var readyPosterKeys by remember { mutableStateOf(emptySet<String>()) }
  var livePlaceholderItem by remember { mutableStateOf<BangumiRecommendation?>(null) }
  var heroCardTransitionActive by remember { mutableStateOf(false) }

  BackHandler(enabled = active && livePlaceholderItem != null) { livePlaceholderItem = null }

  if (pageCount <= 0) {
    // A transient carousel failure must not remove the entire Bangumi root. The explore page has
    // its own endpoint and remains a valid destination while the first screen retries.
    BangumiRecommendationFallbackWithExplore(
      active = active,
      currentAccountMid = currentAccountMid,
      retainedForDetailReturn = retainedForDetailReturn,
      exploreViewModel = exploreViewModel,
      state = state,
      onRefresh = onRefresh,
      onPreviewChanged = onPreviewChanged,
      onOpenExploreLandscape = onOpenExploreLandscape,
      onOpenExplorePoster = onOpenExplorePoster,
      onOpenIndex = onOpenIndex,
      modifier = modifier,
    )
    return
  }

  val midpoint = Int.MAX_VALUE / 2
  val initialPage = remember { midpoint - Math.floorMod(midpoint, pageCount) }
  val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })
  var mainPageVisible by remember { mutableStateOf(false) }
  var verticalPageMoving by remember { mutableStateOf(false) }
  val exploreState by exploreViewModel.state.collectAsState()
  LaunchedEffect(currentAccountMid) { exploreViewModel.setAccount(currentAccountMid) }
  var suppressNextFollowingRefresh by remember { mutableStateOf(false) }
  LaunchedEffect(retainedForDetailReturn) {
    if (retainedForDetailReturn) suppressNextFollowingRefresh = true
  }
  LaunchedEffect(active, mainPageVisible, exploreState.accountMid) {
    if (active && mainPageVisible && exploreState.accountMid > 0L) {
      if (suppressNextFollowingRefresh) {
        suppressNextFollowingRefresh = false
      } else {
        exploreViewModel.refreshFollowing(force = true)
      }
    }
  }
  val selectedIndex by remember {
    derivedStateOf { bangumiRecommendationIndexForSettledPage(pagerState.settledPage, pageCount) }
  }
  val selectedItem = items[selectedIndex]
  val selectedSeason = seasons[selectedItem.stableId]
  val selectedPreviewEpisode =
    remember(selectedSeason) { selectedSeason?.let { selectBangumiPreviewEpisode(it.sections) } }
  val selectedAutoplayEpisode =
    selectedSeason?.let { selectBangumiAutoplayEpisode(it.sections, it.episodes) }
  val selectedPreviewTarget =
    remember(selectedSeason, selectedAutoplayEpisode, selectedPreviewEpisode, selectedItem) {
      val season = selectedSeason ?: return@remember null
      val episode = selectedAutoplayEpisode ?: return@remember null
      BangumiPreviewTarget(
        season = season,
        episode = episode,
        item = previewFeedItem(selectedItem, season, episode),
        isPromotional = selectedPreviewEpisode != null,
      )
    }
  val selectedPreviewCoverKey =
    selectedPreviewTarget?.item?.coverUrl?.let(::bangumiPreviewCoverCacheKey)
  val selectedPreviewCoverReady =
    selectedPreviewCoverKey != null &&
      (selectedPreviewCoverKey in readyPreviewCoverKeys ||
        LoadedFeedImageRegistry.bitmap(selectedPreviewCoverKey) != null)

  val imageLoadingEnabled = (active || preloadEnabled) && !interactionActive
  // `active` governs input and the preview player. A detail page keeps this root composed beneath
  // it, so Hero artwork needs a separate visual-retention signal and must not turn into its black
  // placeholder while the right card alone performs the shared transition.
  val heroBackgroundLoadingEnabled =
    shouldRetainBangumiHeroVisuals(active, preloadEnabled, retainedForDetailReturn) &&
      !interactionActive &&
      !heroCardTransitionActive
  LaunchedEffect(pagerState.settledPage) {
    if (pageCount <= 0) return@LaunchedEffect
    val idx = bangumiRecommendationIndexForSettledPage(pagerState.settledPage, pageCount)
    val id = items.getOrNull(idx)?.stableId ?: return@LaunchedEffect
    onSelect(id)
  }

  LaunchedEffect(state.selectedId) {
    val id = state.selectedId ?: return@LaunchedEffect
    val idx = items.indexOfFirst { it.stableId == id }
    if (idx < 0) return@LaunchedEffect
    val needCurrent = id !in seasons && id !in errors
    val needPrev = (idx - 1 >= 0) && items[idx - 1].stableId.let { it !in seasons && it !in errors }
    val needNext =
      (idx + 1 < items.size) && items[idx + 1].stableId.let { it !in seasons && it !in errors }
    if (needCurrent || needPrev || needNext) {
      val idsToLoad = mutableListOf<String>()
      if (needCurrent) idsToLoad.add(id)
      if (needPrev) idsToLoad.add(items[idx - 1].stableId)
      if (needNext) idsToLoad.add(items[idx + 1].stableId)
      onRequireDetails(idsToLoad)
    }
  }

  // The recommendation carousel is a visual destination, so cold-start readiness covers every
  // item rather than only the currently composed pager neighbourhood.
  val preloadItems = items.distinctBy(BangumiRecommendation::stableId)
  val preloadCoverKeys =
    preloadItems
      .flatMap { item ->
        val season = seasons[item.stableId]
        listOf(
          recommendationPlaybackCover(item, season),
          recommendationMainEpisodeCover(item, season),
        )
      }
      .filter(String::isNotBlank)
      .distinct()
      .map(::bangumiPreviewCoverCacheKey)
  val preloadBackgroundKeys =
    preloadItems.map { item -> "bangumi-background-bitmap-${item.stableId}" }
  val preloadPosterKeys =
    preloadItems
      .mapNotNull { item -> seasons[item.stableId]?.coverUrl?.takeIf(String::isNotBlank) }
      .distinct()
  val seasonRequestsSettled =
    bangumiPreloadDetailsSettled(preloadItems, seasons, errors)
  val preloadCoversReady =
    preloadCoverKeys.all { key ->
      key in readyPreviewCoverKeys || LoadedFeedImageRegistry.bitmap(key) != null
    }
  val preloadBackgroundsReady =
    preloadBackgroundKeys.all { key ->
      key in readyBackgroundKeys || LoadedFeedImageRegistry.bitmap(key) != null
    }
  val preloadPostersReady =
    preloadPosterKeys.all { key ->
      key in readyPosterKeys || LoadedFeedImageRegistry.contains(key)
    }
  LaunchedEffect(
    preloadEnabled,
    seasonRequestsSettled,
    preloadCoversReady,
    preloadBackgroundsReady,
    preloadPostersReady,
  ) {
    if (
      preloadEnabled &&
        seasonRequestsSettled &&
        preloadCoversReady &&
        preloadBackgroundsReady &&
        preloadPostersReady
    ) {
      onPreloadReady()
    }
  }

  val previewActive = active && !mainPageVisible && !verticalPageMoving
  val previewPlaybackActive = active && !mainPageVisible
  LaunchedEffect(
    previewActive,
    selectedPreviewTarget?.item?.id,
    selectedPreviewCoverReady,
  ) {
    val target = selectedPreviewTarget
    if (previewActive && selectedPreviewTarget == null) {
      onPreviewChanged(null)
      return@LaunchedEffect
    }
    if (
      !previewActive ||
        !shouldCommitBangumiPreview(
          targetAvailable = target != null,
          coverReady = selectedPreviewCoverReady,
        )
    ) return@LaunchedEffect

    onPreviewChanged(target)
  }

  LaunchedEffect(previewActive, pagerState) {
    if (!previewActive) {
      previewCoverBlend = null
      return@LaunchedEffect
    }
    snapshotFlow {
        val offset = pagerState.currentPageOffsetFraction
        val fromPage = if (offset >= 0f) pagerState.currentPage else pagerState.currentPage - 1
        val toPage = fromPage + 1
        val progress = if (offset >= 0f) offset else 1f + offset
        val fromIdx = Math.floorMod(fromPage, pageCount)
        val toIdx = Math.floorMod(toPage, pageCount)
        val fromItem = items.getOrNull(fromIdx) ?: return@snapshotFlow null
        val toItem = items.getOrNull(toIdx) ?: return@snapshotFlow null
        BangumiPreviewCoverBlend(
          fromCoverUrl = recommendationPlaybackCover(fromItem, seasons[fromItem.stableId]),
          toCoverUrl = recommendationPlaybackCover(toItem, seasons[toItem.stableId]),
          progress = progress.coerceIn(0f, 1f),
        )
      }
      .distinctUntilChanged()
      .collect { blend -> previewCoverBlend = blend }
  }
  LaunchedEffect(previewPlaybackActive, selectedPreviewTarget?.item?.id, previewMuted) {
    val target = selectedPreviewTarget
    if (previewPlaybackActive && target != null && !selectedItem.isLive) {
      previewPlayerViewModel.play(target.item, previewMuted)
    } else {
      previewPlayerViewModel.pauseForInactivePage()
    }
  }
  LaunchedEffect(previewPlayerState) {
    if (previewPlayerState is BangumiPreviewPlayerState.Ready) previewCardGestureActive = false
  }

  val pagerOffset = pagerState.currentPageOffsetFraction
  val blendFromPage = if (pagerOffset >= 0f) pagerState.currentPage else pagerState.currentPage - 1
  val blendToPage = blendFromPage + 1
  val blendProgress = if (pagerOffset >= 0f) pagerOffset else 1f + pagerOffset
  val blendFromItem = items.getOrNull(Math.floorMod(blendFromPage, pageCount))
  val blendToItem = items.getOrNull(Math.floorMod(blendToPage, pageCount))
  BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    if (imageLoadingEnabled) {
      preloadItems.forEach { item ->
        val season = seasons[item.stableId]
        val visualCovers =
          listOf(
              "preview" to recommendationPlaybackCover(item, season),
              "card" to recommendationMainEpisodeCover(item, season),
            )
            .filter { (_, url) -> url.isNotBlank() }
            .distinctBy { (_, url) -> url }
        visualCovers.forEach { (role, coverUrl) ->
          CoverImage(
            coverUrl = coverUrl,
            contentDescription = null,
            modifier = Modifier.size(1.dp).graphicsLayer { alpha = 0f },
            shape = RectangleShape,
            enforceAspectRatio = false,
            requestWidth = 1600,
            requestHeight = 900,
            loadKey = "bangumi-$role-preload-${item.stableId}",
            bitmapCacheKey = bangumiPreviewCoverCacheKey(coverUrl),
            alwaysLoad = true,
            loadingEnabled = true,
            retainBitmap = true,
            onBitmapLoaded = {
              readyPreviewCoverKeys =
                readyPreviewCoverKeys + bangumiPreviewCoverCacheKey(coverUrl)
            },
            fadeIn = false,
          )
        }
        CoverImage(
          coverUrl = item.bannerUrl,
          contentDescription = null,
          modifier = Modifier.size(1.dp).graphicsLayer { alpha = 0f },
          shape = RectangleShape,
          enforceAspectRatio = false,
          requestWidth = 1920,
          requestHeight = 720,
          loadKey = "bangumi-background-preload-${item.stableId}",
          bitmapCacheKey = "bangumi-background-bitmap-${item.stableId}",
          useOriginalSource = true,
          alwaysLoad = true,
          loadingEnabled = true,
          retainBitmap = true,
          onBitmapLoaded = {
            readyBackgroundKeys =
              readyBackgroundKeys + "bangumi-background-bitmap-${item.stableId}"
          },
          fadeIn = false,
          contentScale = ContentScale.Crop,
        )
        season?.coverUrl?.takeIf(String::isNotBlank)?.let { posterUrl ->
          CoverImage(
            coverUrl = posterUrl,
            contentDescription = null,
            modifier = Modifier.size(1.dp).graphicsLayer { alpha = 0f },
            shape = RectangleShape,
            enforceAspectRatio = false,
            requestWidth = 360,
            requestHeight = 480,
            loadKey = "bangumi-poster-preload-${item.stableId}",
            bitmapCacheKey = posterUrl,
            alwaysLoad = true,
            loadingEnabled = true,
            onBitmapLoaded = { readyPosterKeys = readyPosterKeys + posterUrl },
            fadeIn = false,
            contentScale = ContentScale.Fit,
          )
        }
      }
    }
    val screenHeight = maxHeight
    val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
    val transitionThresholdPx = with(LocalDensity.current) { 112.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var transitionOffsetPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var verticalSettling by remember { mutableStateOf(false) }

    fun settleVerticalPage(showMainPage: Boolean) {
      settleJob?.cancel()
      verticalSettling = true
      settleJob =
        animationScope.launch {
          try {
            val animation = androidx.compose.animation.core.Animatable(transitionOffsetPx)
            animation.updateBounds(lowerBound = 0f, upperBound = screenHeightPx)
            animation.animateTo(
              targetValue = if (showMainPage) screenHeightPx else 0f,
              animationSpec = spring(dampingRatio = .86f, stiffness = 360f),
            ) {
              transitionOffsetPx = value
            }
            mainPageVisible = showMainPage
          } finally {
            verticalSettling = false
            verticalPageMoving = false
            interactionActive = false
            settleJob = null
          }
        }
    }

    // Detail/player routes sit above this root but keep it composed for the return transition.
    // Never let their system Back fall through and also collapse this retained page.
    BackHandler(enabled = active && mainPageVisible && !verticalSettling) { settleVerticalPage(false) }

    fun dragRecommendationBack(deltaPx: Float) {
      if (!mainPageVisible || verticalSettling) return
      verticalPageMoving = true
      interactionActive = true
      transitionOffsetPx = (transitionOffsetPx - deltaPx * .82f).coerceIn(0f, screenHeightPx)
    }

    fun releaseRecommendationBack(velocityY: Float) {
      if (!mainPageVisible || verticalSettling) return
      val revealedHeight = screenHeightPx - transitionOffsetPx
      val returnToRecommendation =
        revealedHeight > screenHeightPx * .18f || velocityY > 900f
      settleVerticalPage(showMainPage = !returnToRecommendation)
    }

    // Once the explore page is settled, its LazyColumn owns vertical drags. Keeping this root
    // detector active there was the direct cause of the permanent non-scrollable blank page.
    val verticalTransitionModifier =
      if (active && !mainPageVisible) {
        Modifier.pointerInput(screenHeightPx) {
        var dragDistance = 0f
        detectVerticalDragGestures(
          onDragStart = {
            dragDistance = 0f
            verticalPageMoving = true
            interactionActive = true
          },
          onVerticalDrag = { change, dragAmount ->
            change.consume()
            dragDistance -= dragAmount
            val pageBase = if (mainPageVisible) screenHeightPx else 0f
            val requested = pageBase + dragDistance
            transitionOffsetPx =
              when {
                !mainPageVisible && requested > 0f -> {
                  val resisted = requested.coerceAtMost(transitionThresholdPx) * .30f
                  val beyondThreshold = (requested - transitionThresholdPx).coerceAtLeast(0f)
                  (resisted + beyondThreshold).coerceIn(0f, screenHeightPx)
                }
                mainPageVisible && requested < screenHeightPx ->
                  requested.coerceIn(0f, screenHeightPx)
                else -> pageBase
              }
          },
          onDragEnd = {
            val showMain =
              if (mainPageVisible) dragDistance >= -transitionThresholdPx
              else dragDistance > transitionThresholdPx
            settleVerticalPage(showMain)
          },
          onDragCancel = { settleVerticalPage(mainPageVisible) },
        )
        }
      } else {
        Modifier
      }

    Box(Modifier.fillMaxSize()) {
      // The explore page is the stable bottom layer. It must be measured in the viewport rather
      // than placed as a second child below a one-screen Column; otherwise its semantics and draw
      // content are clipped when the recommendation layer moves away.
      BangumiExploreScreen(
        active = active,
        interactionEnabled = active && mainPageVisible && !verticalSettling,
        state = exploreState,
        hiddenItemId = hiddenCardId,
        onSelectCategory = exploreViewModel::select,
        onRefresh = exploreViewModel::refresh,
        onLoadMoreFollowing = exploreViewModel::loadMoreFollowing,
        onExplorePull = ::dragRecommendationBack,
        onExplorePullRelease = ::releaseRecommendationBack,
        onOpenLandscape = onOpenExploreLandscape,
        onOpenPoster = onOpenExplorePoster,
        onOpenIndex = onOpenIndex,
        modifier = Modifier.fillMaxSize(),
      )
      val pageProgress = (transitionOffsetPx / screenHeightPx).coerceIn(0f, 1f)
      Box(
        Modifier.fillMaxSize()
          .zIndex(.5f)
          .background(Color.Black.copy(alpha = .18f * (1f - pageProgress)))
      )
      // This full viewport input layer belongs to the recommendation cover, not only its visible
      // artwork. It closes the transparent-area hit-test hole into the explore cards underneath.
      Box(Modifier.fillMaxSize().then(verticalTransitionModifier).zIndex(1f)) {
        BangumiRecommendationHero(
          // Keep the local TextureView mounted during a vertical drag so the decoded video moves
          // with the recommendation cover instead of being replaced by a delayed surface portal.
          active = previewPlaybackActive,
          modifier =
            Modifier.fillMaxWidth()
              .height(screenHeight)
              .offset { IntOffset(0, -transitionOffsetPx.roundToInt()) },
          selectedItem = selectedItem,
          backgroundFromItem = blendFromItem,
          backgroundToItem = blendToItem,
          backgroundProgress = blendProgress,
          imageLoadingEnabled = imageLoadingEnabled,
          heroBackgroundLoadingEnabled = heroBackgroundLoadingEnabled,
          onHeroCardTransitionFinished = { heroCardTransitionActive = false },
          selectedSeason = selectedSeason,
          selectedPreviewTarget = selectedPreviewTarget,
          selectedError = errors[selectedItem.stableId],
          pagerState = pagerState,
          items = items,
          seasons = seasons,
          hiddenCardId = hiddenCardId,
          previewMuted = previewMuted,
          previewPlayerViewModel = previewPlayerViewModel,
          previewPlayerState = previewPlayerState,
          previewCoverBlend = previewCoverBlend,
          previewCoverVisible = previewCardGestureActive,
          onRetry = { onRetryDetail(selectedItem.stableId) },
          onOpenMainEpisode = { card, item, bounds ->
            heroCardTransitionActive = true
            previewPlayerViewModel.stopForNavigation()
            onOpenMainEpisode(card, item, bounds)
          },
          onOpenLive = { livePlaceholderItem = it },
          onTogglePreviewMute = onTogglePreviewMute,
          onPreviewCardGestureStart = {
            previewCardGestureActive = true
            previewPlayerViewModel.pauseForGesture()
            interactionActive = true
          },
          onPreviewCardGestureEnd = { pageChanged ->
            if (!pageChanged) {
              previewCardGestureActive = false
              previewPlayerViewModel.resumeAfterGesture()
            }
            interactionActive = false
          },
        )
      }
      BangumiUpwardHint(
        visibility =
          if (active) {
            1f - pageProgress
          } else {
            0f
          },
        transitionProgress = pageProgress,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .zIndex(2f)
            .navigationBarsPadding()
            .padding(bottom = 96.dp),
      )
      if (verticalSettling) {
        Box(
          Modifier.fillMaxSize()
            .zIndex(20f)
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) {
                  awaitPointerEvent().changes.forEach { it.consume() }
                }
              }
            }
        )
      }
      livePlaceholderItem?.let { item ->
        LiveUnavailableScreen(item = item, onBack = { livePlaceholderItem = null })
      }
    }
  }
}

@Composable
private fun BangumiRecommendationFallbackWithExplore(
  exploreViewModel: BangumiExploreViewModel,
  active: Boolean,
  retainedForDetailReturn: Boolean,
  currentAccountMid: Long,
  state: BangumiRecommendationUiState,
  onRefresh: () -> Unit,
  onPreviewChanged: (BangumiPreviewTarget?) -> Unit,
  onOpenExploreLandscape: (dev.openbili.webdemo.api.BangumiExploreItem, Rect) -> Unit,
  onOpenExplorePoster: (dev.openbili.webdemo.api.BangumiExploreItem, Rect) -> Unit,
  onOpenIndex: (Rect) -> Unit,
  modifier: Modifier,
) {
  val exploreState by exploreViewModel.state.collectAsState()
  LaunchedEffect(currentAccountMid) { exploreViewModel.setAccount(currentAccountMid) }
  var exploreVisible by remember { mutableStateOf(false) }
  var settling by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  var suppressNextFollowingRefresh by remember { mutableStateOf(false) }
  LaunchedEffect(retainedForDetailReturn) {
    if (retainedForDetailReturn) suppressNextFollowingRefresh = true
  }
  LaunchedEffect(active, exploreVisible, exploreState.accountMid) {
    if (active && exploreVisible && exploreState.accountMid > 0L) {
      if (suppressNextFollowingRefresh) {
        suppressNextFollowingRefresh = false
      } else {
        exploreViewModel.refreshFollowing(force = true)
      }
    }
  }

  // No root PV exists in this fallback, so clear the retained target explicitly.
  LaunchedEffect(active) {
    onPreviewChanged(null)
  }

  BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    val screenHeight = maxHeight
    val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
    val thresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun settle(showExplore: Boolean) {
      settleJob?.cancel()
      settling = true
      settleJob =
        scope.launch {
          try {
            val animation = androidx.compose.animation.core.Animatable(offsetPx)
            animation.updateBounds(0f, screenHeightPx)
            animation.animateTo(
              targetValue = if (showExplore) screenHeightPx else 0f,
              animationSpec = spring(dampingRatio = .86f, stiffness = 360f),
            ) { offsetPx = value }
            exploreVisible = showExplore
          } finally {
            settling = false
            settleJob = null
          }
        }
    }

    BackHandler(enabled = active && exploreVisible && !settling) { settle(false) }

    fun dragFallbackRecommendationBack(deltaPx: Float) {
      if (!exploreVisible || settling) return
      offsetPx = (offsetPx - deltaPx * .82f).coerceIn(0f, screenHeightPx)
    }

    fun releaseFallbackRecommendationBack(velocityY: Float) {
      if (!exploreVisible || settling) return
      val revealedHeight = screenHeightPx - offsetPx
      val returnToRecommendation =
        revealedHeight > screenHeightPx * .18f || velocityY > 900f
      settle(showExplore = !returnToRecommendation)
    }
    val verticalGestureModifier =
      if (active && !exploreVisible) {
        Modifier.pointerInput(screenHeightPx) {
          var dragDistance = 0f
          detectVerticalDragGestures(
            onDragStart = {
              dragDistance = 0f
            },
            onVerticalDrag = { change, dragAmount ->
              change.consume()
              dragDistance -= dragAmount
              offsetPx = dragDistance.coerceIn(0f, screenHeightPx)
            },
            onDragEnd = { settle(dragDistance > thresholdPx) },
            onDragCancel = { settle(false) },
          )
        }
      } else {
        Modifier
      }

    Box(Modifier.fillMaxSize()) {
      BangumiExploreScreen(
        active = active,
        interactionEnabled = active && exploreVisible && !settling,
        state = exploreState,
        hiddenItemId = null,
        onSelectCategory = exploreViewModel::select,
        onRefresh = exploreViewModel::refresh,
        onLoadMoreFollowing = exploreViewModel::loadMoreFollowing,
        onExplorePull = ::dragFallbackRecommendationBack,
        onExplorePullRelease = ::releaseFallbackRecommendationBack,
        onOpenLandscape = onOpenExploreLandscape,
        onOpenPoster = onOpenExplorePoster,
        onOpenIndex = onOpenIndex,
        modifier = Modifier.fillMaxSize(),
      )
      val pageProgress = (offsetPx / screenHeightPx).coerceIn(0f, 1f)
      Box(
        Modifier.fillMaxSize()
          .zIndex(.5f)
          .background(Color.Black.copy(alpha = .18f * (1f - pageProgress)))
      )
      Box(Modifier.fillMaxSize().then(verticalGestureModifier).zIndex(1f)) {
        BangumiRecommendationFallbackHero(
          state = state,
          onRefresh = onRefresh,
          modifier =
            Modifier.fillMaxWidth()
              .height(screenHeight)
              .offset { IntOffset(0, -offsetPx.roundToInt()) },
        )
      }
      BangumiUpwardHint(
        visibility =
          if (active) {
            1f - pageProgress
          } else {
            0f
          },
        transitionProgress = pageProgress,
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .zIndex(2f)
            .navigationBarsPadding()
            .padding(bottom = 96.dp),
      )
      if (settling) {
        Box(
          Modifier.fillMaxSize()
            .zIndex(20f)
            .pointerInput(Unit) {
              awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
              }
            }
        )
      }
    }
  }
}

@Composable
private fun BangumiRecommendationFallbackHero(
  state: BangumiRecommendationUiState,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier.padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text("本期推荐", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    when {
      state.initialLoading ->
        CircularProgressIndicator(
          modifier = Modifier.size(34.dp),
          color = MaterialTheme.colorScheme.primary,
          strokeWidth = 3.dp,
        )
      state.initialError != null -> {
        Text(
          state.initialError,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onRefresh) { Text("重新加载") }
      }
      else -> {
        Text("正在准备本期推荐", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRefresh) { Text("重新加载") }
      }
    }
  }
}

@Composable
private fun BangumiUpwardHint(
  visibility: Float,
  transitionProgress: Float,
  modifier: Modifier = Modifier,
) {
  val breathing = rememberInfiniteTransition(label = "bangumiUpHint")
  val alpha by
    breathing.animateFloat(
      initialValue = .38f,
      targetValue = .92f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1_050),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "bangumiUpHintAlpha",
    )
  val lift by
    breathing.animateFloat(
      initialValue = 6f,
      targetValue = -4f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1_050),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "bangumiUpHintLift",
    )
  val dismissTravelPx = with(LocalDensity.current) { 36.dp.toPx() }
  Box(
    modifier =
      modifier.graphicsLayer {
          this.alpha = alpha * visibility
          // The hint belongs to the recommendation cover: it rises with that cover while fading
          // out, and naturally performs the exact inverse when the cover is pulled back down.
          translationY = lift * visibility - dismissTravelPx * transitionProgress.coerceIn(0f, 1f)
        }.size(width = 42.dp, height = 38.dp),
  ) {
    Icon(
      imageVector = Icons.Rounded.KeyboardArrowUp,
      contentDescription = "向上滑动",
      modifier = Modifier.align(Alignment.TopCenter).size(30.dp),
      tint = Color.White.copy(alpha = .55f),
    )
    Icon(
      imageVector = Icons.Rounded.KeyboardArrowUp,
      contentDescription = null,
      modifier = Modifier.align(Alignment.BottomCenter).size(30.dp),
      tint = Color.White,
    )
  }
}

@Composable
private fun LiveUnavailableScreen(
  item: BangumiRecommendation,
  onBack: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxSize().zIndex(40f),
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        "直播",
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(12.dp))
      Text(
        item.title,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        "直播功能暂未完成",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
      )
      Spacer(Modifier.height(24.dp))
      Text(
        "返回",
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack).padding(14.dp),
      )
    }
  }
}

@Composable
private fun BangumiRecommendationHero(
  active: Boolean,
  modifier: Modifier,
  selectedItem: BangumiRecommendation,
  backgroundFromItem: BangumiRecommendation?,
  backgroundToItem: BangumiRecommendation?,
  backgroundProgress: Float,
  imageLoadingEnabled: Boolean,
  heroBackgroundLoadingEnabled: Boolean,
  onHeroCardTransitionFinished: () -> Unit,
  selectedSeason: BangumiSeason?,
  selectedPreviewTarget: BangumiPreviewTarget?,
  selectedError: String?,
  pagerState: androidx.compose.foundation.pager.PagerState,
  items: List<BangumiRecommendation>,
  seasons: Map<String, BangumiSeason>,
  hiddenCardId: String?,
  previewMuted: Boolean,
  previewPlayerViewModel: BangumiPreviewPlayerViewModel,
  previewPlayerState: BangumiPreviewPlayerState,
  previewCoverBlend: BangumiPreviewCoverBlend?,
  previewCoverVisible: Boolean,
  onRetry: () -> Unit,
  onOpenMainEpisode: (SpaceContentCard, FeedItem, Rect) -> Unit,
  onOpenLive: (BangumiRecommendation) -> Unit,
  onTogglePreviewMute: () -> Unit,
  onPreviewCardGestureStart: () -> Unit,
  onPreviewCardGestureEnd: (pageChanged: Boolean) -> Unit,
) {
  BoxWithConstraints(modifier) {
    if (backgroundFromItem != null) {
      BangumiHeroBackground(
        item = backgroundFromItem,
        Modifier.fillMaxSize(),
        loadingEnabled = heroBackgroundLoadingEnabled,
      )
    }
    if (backgroundToItem != null) {
      BangumiHeroBackground(
        item = backgroundToItem,
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = backgroundProgress },
        loadingEnabled = heroBackgroundLoadingEnabled,
      )
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)))

    val wideLayout = maxWidth >= 840.dp
    var currentTime by remember { mutableStateOf(formatCurrentTime()) }
    LaunchedEffect(active) {
      while (active) {
        currentTime = formatCurrentTime()
        delay(1_000)
      }
    }
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = if (wideLayout) 32.dp else 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "本期推荐",
          color = Color.White,
          fontSize = if (wideLayout) 62.sp else 46.sp,
          fontWeight = FontWeight.Black,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            modifier =
              Modifier.size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onTogglePreviewMute),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = .34f),
            contentColor = Color.White,
          ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              PreviewVolumeIcon(muted = previewMuted)
            }
          }
          Text(
            currentTime,
            color = Color.White.copy(alpha = .92f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
          )
        }
      }
      Spacer(Modifier.height(4.dp))

      if (wideLayout) {
        Row(
          Modifier.fillMaxWidth().weight(1f).padding(bottom = 76.dp).offset(y = (-12).dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          BangumiPreviewPlayer(
            modifier = Modifier.weight(1.12f).padding(start = 32.dp),
            active = active,
            item = selectedItem,
            season = selectedSeason,
            previewTarget = selectedPreviewTarget,
            selectedError = selectedError,
            imageLoadingEnabled = imageLoadingEnabled,
            previewPlayerViewModel = previewPlayerViewModel,
            previewPlayerState = previewPlayerState,
            previewCoverBlend = previewCoverBlend,
            previewCoverVisible = previewCoverVisible,
            onRetry = onRetry,
          )
          BangumiCardStack(
            modifier = Modifier.weight(.88f).fillMaxHeight(),
            active = active,
            pagerState = pagerState,
            items = items,
            seasons = seasons,
            hiddenCardId = hiddenCardId,
            imageLoadingEnabled = imageLoadingEnabled,
            onHeroCardTransitionFinished = onHeroCardTransitionFinished,
            onOpen = onOpenMainEpisode,
            onOpenLive = onOpenLive,
            onGestureStart = onPreviewCardGestureStart,
            onGestureEnd = onPreviewCardGestureEnd,
          )
        }
      } else {
        Column(
          Modifier.fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 20.dp)
            .offset(y = (-8).dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          BangumiPreviewPlayer(
            modifier = Modifier.fillMaxWidth(),
            active = active,
            item = selectedItem,
            season = selectedSeason,
            previewTarget = selectedPreviewTarget,
            selectedError = selectedError,
            imageLoadingEnabled = imageLoadingEnabled,
            previewPlayerViewModel = previewPlayerViewModel,
            previewPlayerState = previewPlayerState,
            previewCoverBlend = previewCoverBlend,
            previewCoverVisible = previewCoverVisible,
            onRetry = onRetry,
          )
          BangumiCardStack(
            modifier = Modifier.fillMaxWidth().weight(1f),
            active = active,
            pagerState = pagerState,
            items = items,
            seasons = seasons,
            hiddenCardId = hiddenCardId,
            imageLoadingEnabled = imageLoadingEnabled,
            onHeroCardTransitionFinished = onHeroCardTransitionFinished,
            onOpen = onOpenMainEpisode,
            onOpenLive = onOpenLive,
            onGestureStart = onPreviewCardGestureStart,
            onGestureEnd = onPreviewCardGestureEnd,
          )
        }
      }
    }
  }
}

@Composable
private fun PreviewVolumeIcon(muted: Boolean) {
  val iconColor = if (muted) Color(0xFFFF4D5E) else Color.White
  Box(Modifier.size(23.dp)) {
    Icon(
      painter = painterResource(R.drawable.ic_gesture_volume),
      contentDescription = if (muted) "当前静音，点击开启声音" else "当前有声，点击静音",
      modifier = Modifier.fillMaxSize(),
      tint = iconColor,
    )
    if (muted) {
      Canvas(Modifier.fillMaxSize()) {
        drawLine(
          color = iconColor,
          start = Offset(size.width * .16f, size.height * .14f),
          end = Offset(size.width * .84f, size.height * .86f),
          strokeWidth = 2.2.dp.toPx(),
        )
      }
    }
  }
}

@Composable
private fun BangumiHeroBackground(
  item: BangumiRecommendation,
  modifier: Modifier = Modifier,
  loadingEnabled: Boolean = true,
) {
  val bitmapCacheKey = "bangumi-background-bitmap-${item.stableId}"
  val keepCachedBackgroundMounted = LoadedFeedImageRegistry.bitmap(bitmapCacheKey) != null
  Box(modifier.background(Color.Black)) {
    CoverImage(
      coverUrl = item.bannerUrl,
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
      shape = RectangleShape,
      enforceAspectRatio = false,
      requestWidth = 1920,
      requestHeight = 720,
      loadKey = "bangumi-background-${item.stableId}",
      bitmapCacheKey = bitmapCacheKey,
      useOriginalSource = true,
      alwaysLoad = true,
      loadingEnabled = loadingEnabled || keepCachedBackgroundMounted,
      retainBitmap = true,
      placeholderColor = Color.Black,
      fadeIn = false,
      contentScale = ContentScale.Crop,
    )
  }
}

@Composable
private fun CachedBangumiPreviewTransitionCover(
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
private fun BangumiPreviewPlayer(
  modifier: Modifier,
  active: Boolean,
  item: BangumiRecommendation,
  season: BangumiSeason?,
  previewTarget: BangumiPreviewTarget?,
  selectedError: String?,
  imageLoadingEnabled: Boolean,
  previewPlayerViewModel: BangumiPreviewPlayerViewModel,
  previewPlayerState: BangumiPreviewPlayerState,
  previewCoverBlend: BangumiPreviewCoverBlend?,
  previewCoverVisible: Boolean,
  onRetry: () -> Unit,
) {
  val fallbackUrl = season?.coverUrl?.takeIf(String::isNotBlank) ?: item.cardUrl
  Surface(
    modifier =
      modifier.fillMaxWidth().aspectRatio(16f / 9f),
    shape = VideoShapeTokens.Player,
    color = Color.Black,
    shadowElevation = 12.dp,
  ) {
    Box(Modifier.fillMaxSize()) {
      CoverImage(
        coverUrl = previewTarget?.item?.coverUrl?.ifBlank { fallbackUrl } ?: fallbackUrl,
        contentDescription = season?.title ?: item.title,
        modifier = Modifier.fillMaxSize(),
        shape = VideoShapeTokens.Player,
        enforceAspectRatio = false,
        requestWidth = 1600,
        requestHeight = 900,
        loadKey = "bangumi-preview-cover-${item.stableId}",
        bitmapCacheKey =
          bangumiPreviewCoverCacheKey(
            previewTarget?.item?.coverUrl?.ifBlank { fallbackUrl } ?: fallbackUrl
          ),
        alwaysLoad = true,
        loadingEnabled = imageLoadingEnabled,
      )
      if (active && previewTarget != null && !item.isLive) {
        val player = previewPlayerViewModel.preparePlayer()
        AndroidView(
          factory = { context -> createTexturePlayerView(context, player) },
          update = { view -> if (view.player !== player) view.player = player },
          modifier = Modifier.fillMaxSize(),
        )
      }
      val previewReady =
        (previewPlayerState as? BangumiPreviewPlayerState.Ready)?.itemId == previewTarget?.item?.id
      val showCover = previewCoverVisible || !previewReady
      val coverAlpha by
        animateFloatAsState(
          targetValue = if (showCover) 1f else 0f,
          animationSpec = tween(if (showCover) 70 else 200),
          label = "bangumiPreviewCover",
        )
      if (coverAlpha > .01f) {
        Box(
          Modifier.fillMaxSize()
            .graphicsLayer { alpha = coverAlpha }
            .background(Color.Black.copy(alpha = .18f))
        ) {
          if (previewCoverVisible && previewCoverBlend != null) {
            CachedBangumiPreviewTransitionCover(
              coverUrl = previewCoverBlend.fromCoverUrl,
              modifier = Modifier.fillMaxSize(),
            )
            CachedBangumiPreviewTransitionCover(
              coverUrl = previewCoverBlend.toCoverUrl,
              modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previewCoverBlend.progress },
            )
          } else {
            CoverImage(
              coverUrl = previewTarget?.item?.coverUrl?.ifBlank { fallbackUrl } ?: fallbackUrl,
              contentDescription = null,
              modifier = Modifier.fillMaxSize(),
              shape = VideoShapeTokens.Player,
              enforceAspectRatio = false,
              requestWidth = 1600,
              requestHeight = 900,
              loadKey = "bangumi-preview-transition-cover-${item.stableId}",
              bitmapCacheKey =
                bangumiPreviewCoverCacheKey(
                  previewTarget?.item?.coverUrl?.ifBlank { fallbackUrl } ?: fallbackUrl
                ),
              alwaysLoad = true,
              loadingEnabled = imageLoadingEnabled,
              retainBitmap = true,
              fadeIn = false,
            )
          }
        }
      }
      val loading = !item.isLive && season == null && selectedError == null
      if (item.isLive) {
        Text(
          "直播功能暂未完成",
          modifier = Modifier.align(Alignment.Center),
          color = Color.White.copy(alpha = .9f),
          style = MaterialTheme.typography.titleMedium,
        )
      } else if (loading) {
        CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center).size(32.dp),
          color = Color.White,
          strokeWidth = 3.dp,
        )
      } else if (selectedError != null) {
        Column(
          Modifier.align(Alignment.Center),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("推荐加载失败", color = Color.White, style = MaterialTheme.typography.titleMedium)
          Text(
            "点这里重试",
            color = Color.White.copy(alpha = .78f),
            style = MaterialTheme.typography.bodyMedium,
            modifier =
              Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onRetry).padding(8.dp),
          )
        }
      } else if (previewTarget == null) {
        Text(
          "该番剧暂未提供独立 PV",
          modifier = Modifier.align(Alignment.Center),
          color = Color.White.copy(alpha = .88f),
          style = MaterialTheme.typography.titleMedium,
        )
      } else if (previewPlayerState is BangumiPreviewPlayerState.Error) {
        Text(
          "预览暂时无法播放",
          modifier = Modifier.align(Alignment.Center),
          color = Color.White.copy(alpha = .88f),
          style = MaterialTheme.typography.bodyLarge,
        )
      }

    }
  }
}

@Composable
private fun BangumiCardStack(
  modifier: Modifier,
  active: Boolean,
  pagerState: androidx.compose.foundation.pager.PagerState,
  items: List<BangumiRecommendation>,
  seasons: Map<String, BangumiSeason>,
  hiddenCardId: String?,
  imageLoadingEnabled: Boolean,
  onHeroCardTransitionFinished: () -> Unit,
  onOpen: (SpaceContentCard, FeedItem, Rect) -> Unit,
  onOpenLive: (BangumiRecommendation) -> Unit,
  onGestureStart: () -> Unit,
  onGestureEnd: (pageChanged: Boolean) -> Unit,
) {
  val gestureScope = rememberCoroutineScope()
  var gestureSettleJob by remember { mutableStateOf<Job?>(null) }
  HorizontalPager(
    state = pagerState,
    modifier =
      modifier.pointerInput(active, pagerState) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
          val startPage = pagerState.settledPage
          val down = awaitFirstDown(requireUnconsumed = false)
          var horizontalGesture: Boolean? = null
          while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (horizontalGesture == null) {
              horizontalGesture =
                bangumiCardGestureIsHorizontal(change.position - down.position, touchSlop)
              if (horizontalGesture == true && active) {
                gestureSettleJob?.cancel()
                onGestureStart()
              }
            }
            if (!change.pressed) break
          }
          if (horizontalGesture == true && active) {
            gestureSettleJob =
              gestureScope.launch {
                withFrameNanos {}
                while (pagerState.isScrollInProgress) withFrameNanos {}
                onGestureEnd(pagerState.settledPage != startPage)
              }
          }
        }
      },
    contentPadding = PaddingValues(horizontal = 54.dp),
    pageSpacing = 12.dp,
    beyondViewportPageCount = 1,
    verticalAlignment = Alignment.CenterVertically,
  ) { page ->
    val index = Math.floorMod(page, items.size)
    val item = items[index]
    val season = seasons[item.stableId]
    val navigation =
      remember(item, season) { mainEpisodeNavigationTarget(item, season) }
    var bounds by remember(page) { mutableStateOf(Rect.Zero) }
    var opening by remember(page) { mutableStateOf(false) }
    val interactionSource = remember(page) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by
      animateFloatAsState(
        targetValue = if (pressed) MotionTokens.PressedScale else 1f,
        animationSpec = tween(MotionTokens.Quick),
        label = "bangumiCardPress",
      )
    val chromeAlpha = remember(page) { Animatable(1f) }
    var chromeAnimationJob by remember(page) { mutableStateOf<Job?>(null) }
    val signedPageOffset =
      (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val pageOffset = signedPageOffset.absoluteValue
    val stackScale = (1f - pageOffset.coerceIn(0f, 1f) * .075f) * pressScale
    val selected = page == pagerState.currentPage
    val hidden = shouldHideBangumiRecommendationCard(navigation?.second?.id, hiddenCardId)
    LaunchedEffect(active, hiddenCardId) {
      if (opening && active && hiddenCardId == null) {
        opening = false
        chromeAnimationJob?.cancel()
        chromeAnimationJob =
          launch {
            // The returning cover has already landed on this real card. Restore its information
            // only after that handoff, keeping the shared home-style flight cover-only.
            chromeAlpha.animateTo(1f, tween(220))
            onHeroCardTransitionFinished()
          }
      }
    }
    val clickModifier =
      if (selected && (navigation != null || item.isLive)) {
        Modifier.clickable(
          interactionSource = interactionSource,
          indication = null,
        ) {
          if (item.isLive) {
            onOpenLive(item)
          } else if (!opening) {
            opening = true
            navigation?.let { target ->
              chromeAnimationJob?.cancel()
              chromeAnimationJob =
                gestureScope.launch {
                  // Match the mature home-card contract: the shared overlay flies only the cover.
                  // This Hero-specific prelude completes while the card is still stationary, then
                  // hands its real 16:9 layout bounds to the existing home transition pipeline.
                  chromeAlpha.animateTo(0f, tween(220))
                  withFrameNanos {}
                  onOpen(target.first, target.second, bounds)
                }
            }
          }
        }
      } else {
        Modifier
      }

    Box(
      Modifier.fillMaxSize().zIndex(10f - pageOffset),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
              scaleX = stackScale
              scaleY = stackScale
              translationX =
                when {
                  signedPageOffset > 0f -> 24.dp.toPx() * pageOffset.coerceIn(0f, 1f)
                  signedPageOffset < 0f -> -24.dp.toPx() * pageOffset.coerceIn(0f, 1f)
                  else -> 0f
                }
              translationY = pageOffset.coerceIn(0f, 1f) * 18.dp.toPx()
              alpha = if (hidden) 0f else 1f - pageOffset.coerceIn(0f, 1f) * .14f
            }
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .then(clickModifier),
        shape = VideoShapeTokens.Card,
        color = Color.Black,
        shadowElevation = if (selected) 14.dp else 5.dp,
      ) {
        Box(Modifier.fillMaxSize()) {
          CoverImage(
            coverUrl = recommendationMainEpisodeCover(item, season),
            contentDescription = season?.title ?: item.title,
            modifier = Modifier.fillMaxSize(),
            shape = VideoShapeTokens.Card,
            enforceAspectRatio = false,
            requestWidth = 1600,
            requestHeight = 900,
            loadKey = "bangumi-card-${item.stableId}",
            bitmapCacheKey =
              bangumiPreviewCoverCacheKey(recommendationMainEpisodeCover(item, season)),
            alwaysLoad = true,
            loadingEnabled = imageLoadingEnabled,
            contentScale = ContentScale.Crop,
          )
          Box(
            Modifier.fillMaxSize()
              .graphicsLayer { alpha = chromeAlpha.value }
              .background(
                Brush.verticalGradient(
                  listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .86f))
                )
              )
          )
          Column(
            Modifier.align(Alignment.BottomStart)
              .padding(18.dp)
              .fillMaxWidth(.82f)
              .graphicsLayer { alpha = chromeAlpha.value }
          ) {
            Text(
              season?.title ?: item.title,
              color = Color.White,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val metadata =
              season
                ?.let {
                  buildList {
                      it.rating?.let { score -> add(String.format("%.1f 分", score)) }
                      if (it.styles.isNotEmpty()) add(it.styles.take(2).joinToString(" / "))
                    }
                    .joinToString(" · ")
                }
                .orEmpty()
            if (metadata.isNotBlank()) {
              Text(
                metadata,
                color = Color.White.copy(alpha = .74f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

private fun previewFeedItem(
  item: BangumiRecommendation,
  season: BangumiSeason,
  episode: BangumiEpisode,
  coverUrl: String = episode.coverUrl.ifBlank { season.coverUrl.ifBlank { item.cardUrl } },
): FeedItem =
  FeedItem(
    id = "bangumi-home-${item.stableId}-ep-${episode.id}",
    title = season.title,
    videoUrl = "https://www.bilibili.com/bangumi/play/ep${episode.id}",
    coverUrl = coverUrl,
    uploader = null,
    playCount = null,
    duration = formatPreviewDuration(episode.durationSeconds),
    description = season.evaluate,
  )

private fun mainEpisodeNavigationTarget(
  item: BangumiRecommendation,
  season: BangumiSeason?,
): Pair<SpaceContentCard, FeedItem>? {
  season ?: return null
  val episode = selectBangumiMainEpisode(season.episodes) ?: return null
  val feedItem = previewFeedItem(item, season, episode)
  return recommendationTarget(season, episode, feedItem, feedItem.coverUrl)
}

private fun recommendationTarget(
  season: BangumiSeason,
  episode: BangumiEpisode,
  item: FeedItem,
  coverUrl: String,
): Pair<SpaceContentCard, FeedItem> {
  val card =
    SpaceContentCard(
      id = item.id,
      title = season.title,
      subtitle = season.evaluate,
      coverUrl = coverUrl,
      aid = episode.aid,
      bvid = episode.bvid,
      videoUrl = item.videoUrl,
      seasonId = season.seasonId,
      episodeId = episode.id,
      kind = SpaceContentKind.BANGUMI,
    )
  return card to item
}

private val BangumiTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatCurrentTime(): String = LocalTime.now().format(BangumiTimeFormatter)

private fun formatPreviewDuration(seconds: Long): String {
  val safe = seconds.coerceAtLeast(0L)
  return "%d:%02d".format(safe / 60L, safe % 60L)
}
