package dev.openbili.webdemo.video

/**
 * 番剧播放页的内容组合件与数据辅助函数。
 *
 * 该文件承载番剧（含影视剧/电影）详情页的整套界面：顶部标题栏 [BangumiHeader]、下方
 * 详情卡与选集卡 [BangumiLowerPanel]、选集/季度菜单弹窗、番剧信息弹窗与短评弹窗。
 * 同时提供若干纯函数，负责解析"可播放剧集列表"、当前集标题/封面、下一集，以及番剧
 * 标题、评分、播放量等格式化逻辑。所有 UI 组件均为 @Composable，仅依赖 [BangumiPageUi]
 * 这一状态聚合对象与少量回调，保持无副作用、便于复用。
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.BangumiSeason
import dev.openbili.webdemo.api.SpaceContentCard
import dev.openbili.webdemo.api.SpaceContentKind
import dev.openbili.webdemo.feed.CoverImage
import dev.openbili.webdemo.feed.FeedViewModel
import dev.openbili.webdemo.ui.DeviceStatusCluster
import dev.openbili.webdemo.ui.LocalControlFocusVisible
import dev.openbili.webdemo.ui.VideoShapeTokens
import dev.openbili.webdemo.ui.controlFocusOutline
import kotlinx.coroutines.launch

/**
 * 番剧播放页的 UI 状态聚合。
 *
 * 集中存放渲染番剧页所需的全部只读数据：来源卡片、番剧季详情、加载/错误标记、
 * 当前集 ID、海报可见性以及追番按钮忙碌标记。由上层构建后传入各内容组合件，
 * 组件内部不再直接访问网络或 ViewModel。
 */
data class BangumiPageUi(
  val sourceCard: SpaceContentCard,
  val season: BangumiSeason?,
  val loading: Boolean,
  val error: String?,
  val currentEpisodeId: Long,
  val posterVisible: Boolean,
  val followBusy: Boolean = false,
)

/** 为番剧播放页可操作项复用与普通视频页一致的 3 dp 控制器焦点框。 */
@Composable
private fun Modifier.bangumiPlaybackControlFocus(
  focusRequester: FocusRequester?,
  leftFocus: FocusRequester = FocusRequester.Cancel,
  rightFocus: FocusRequester = FocusRequester.Cancel,
  upFocus: FocusRequester = FocusRequester.Cancel,
  downFocus: FocusRequester = FocusRequester.Cancel,
  shape: Shape,
): Modifier =
  if (focusRequester == null) {
    this
  } else {
    this.focusRequester(focusRequester)
      .focusProperties {
        left = leftFocus
        right = rightFocus
        up = upFocus
        down = downFocus
      }
      .controlFocusOutline(
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        width = 3.dp,
        enabled = true,
      )
  }

/**
 * 生成单集在列表/标题栏中的展示标题。
 *
 * 依据内容类型选用单位（番剧用"话"、影视剧用"集"）；当标题为纯数字时补全为
 * "第N话/集"，已含"第"则原样保留，其余情况直接使用原标题。最终把短标题与
 * [BangumiEpisode.longTitle] 用 " · " 拼接，作为完整的展示文本。
 */
internal fun BangumiEpisode.displayTitle(kind: SpaceContentKind): String {
  val unit = if (kind == SpaceContentKind.BANGUMI) "话" else "集"
  val prefix =
    title.trim().let { value ->
      when {
        value.isBlank() -> ""
        value.all(Char::isDigit) -> "第${value}$unit"
        value.startsWith("第") -> value
        else -> value
      }
    }
  return listOf(prefix, longTitle.trim()).filter(String::isNotBlank).joinToString(" · ")
}

/**
 * 计算"可播放剧集"列表。
 *
 * 优先使用番剧季的主选集列表；只有当某个分节包含当前选中的集，或该季根本没有主选集时，
 * 才回退到分节（花絮/其他内容）里的剧集作为可播放来源。
 */
internal fun BangumiPageUi.playableEpisodes(): List<BangumiEpisode> {
  val season = season ?: return emptyList()
  val mainEpisodes = season.episodes
  val selectedSectionEpisodes =
    season.sections
      .firstOrNull { section ->
        section.episodes.any { it.id == currentEpisodeId }
      }
      ?.episodes
  return selectedSectionEpisodes
    ?: mainEpisodes.ifEmpty {
      season.sections.firstOrNull { it.episodes.isNotEmpty() }?.episodes.orEmpty()
    }
}

/**
 * 解析当前应显示的标题文本。
 *
 * 先在可播放剧集中查找当前集并取展示标题；找不到或为空时，依次回退到来源卡片的
 * 副标题与主标题，保证标题栏始终有内容可显示。
 */
internal fun BangumiPageUi.currentEpisodeTitle(): String {
  val episode = playableEpisodes().firstOrNull { it.id == currentEpisodeId }
  return episode?.displayTitle(sourceCard.kind)?.takeIf(String::isNotBlank)
    ?: sourceCard.subtitle.takeIf(String::isNotBlank)
    ?: sourceCard.title
}

/**
 * 解析当前集封面 URL。
 *
 * 优先取当前集的封面，为空则回退到番剧季封面，最后再回退到来源卡片封面。
 */
internal fun BangumiPageUi.currentEpisodeCoverUrl(): String =
  playableEpisodes()
    .firstOrNull { it.id == currentEpisodeId }
    ?.coverUrl
    .orEmpty()
    .ifBlank { season?.coverUrl.orEmpty() }
    .ifBlank { sourceCard.coverUrl }

/**
 * 返回当前集的下一集。
 *
 * 若当前集在可播放列表中不存在（indexOfFirst 返回 -1），则同样返回 null，
 * 避免在"未找到当前集"的情况下错误地给出第一集作为下一集。
 */
internal fun BangumiPageUi.nextPlayableEpisode(): BangumiEpisode? {
  val episodes = playableEpisodes()
  val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
  return episodes.getOrNull(currentIndex + 1).takeIf { currentIndex >= 0 }
}

/**
 * 判断该番剧季是否为"电影/剧场版"页面。
 *
 * 通过类型名或风格标签中是否包含"电影"或"剧场版"来判定，用于切换选集区的
 * 单列"正片"布局与多列"选集"布局。
 */
internal fun BangumiSeason.isMoviePage(): Boolean =
  typeName.contains("电影") || styles.any { it.contains("电影") || it.contains("剧场版") }

@Composable
internal fun BangumiHeader(
  page: BangumiPageUi,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onFollow: () -> Unit,
  onRate: (Int, String) -> Unit,
  panelSlideProgress: () -> Float,
  showDeviceStatus: Boolean,
  foregroundColor: Color? = null,
  glassBackdrop: PlaybackPageGlassBackdrop = PlaybackPageGlassBackdrop(),
  controlFocus: PlaybackHeaderControlFocus? = null,
) {
  var showShortReview by remember(page.season?.mediaId) { mutableStateOf(false) }
  val title = page.currentEpisodeTitle()
  val resolvedForeground = foregroundColor ?: MaterialTheme.colorScheme.onBackground
  Surface(
    modifier =
      Modifier.fillMaxWidth().height(76.dp).graphicsLayer {
        alpha = panelSlideProgress().coerceIn(0f, 1f)
      },
    color = Color.Transparent,
    contentColor = resolvedForeground,
    tonalElevation = 0.dp,
  ) {
    Row(
      Modifier.fillMaxSize().padding(end = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val ratingEnabled = page.season?.mediaId?.let { it > 0L } == true
      val followEnabled = !page.followBusy && page.season?.seasonId?.let { it > 0L } == true
      val rateFocus = controlFocus?.details?.takeIf { ratingEnabled }
      val followFocus = controlFocus?.follow?.takeIf { followEnabled }
      IconButton(
        onClick = onBack,
        modifier =
          Modifier.testTag("bangumi_back_button").bangumiPlaybackControlFocus(
            focusRequester = controlFocus?.back,
            rightFocus = controlFocus?.home ?: FocusRequester.Cancel,
            downFocus = controlFocus?.player ?: FocusRequester.Cancel,
            shape = CircleShape,
          ),
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
      }
      IconButton(
        onClick = onHome,
        modifier =
          Modifier.bangumiPlaybackControlFocus(
            focusRequester = controlFocus?.home,
            leftFocus = controlFocus?.back ?: FocusRequester.Cancel,
            rightFocus = rateFocus ?: followFocus ?: FocusRequester.Cancel,
            downFocus = controlFocus?.player ?: FocusRequester.Cancel,
            shape = CircleShape,
          ),
      ) {
        Icon(Icons.Default.Home, contentDescription = "返回首页")
      }
      Text(
        text = title,
        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      PlaybackPageGlassSurface(
        backdrop = glassBackdrop,
        modifier =
          Modifier.clip(RoundedCornerShape(18.dp))
            .bangumiPlaybackControlFocus(
              focusRequester = rateFocus,
              leftFocus = controlFocus?.home ?: FocusRequester.Cancel,
              rightFocus = followFocus ?: FocusRequester.Cancel,
              downFocus = controlFocus?.player ?: FocusRequester.Cancel,
              shape = RoundedCornerShape(18.dp),
            )
            .clickable(enabled = ratingEnabled) { showShortReview = true }
            .graphicsLayer { alpha = if (ratingEnabled) 1f else .42f },
        shape = RoundedCornerShape(18.dp),
      ) {
        Row(
          Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = resolvedForeground,
          )
          Spacer(Modifier.width(6.dp))
          Text(
            page.season?.userRatingScore?.let { "${it / 2} 星" }
              ?: page.season?.rating?.let { "$it 分" }
              ?: "短评",
            color = resolvedForeground,
          )
        }
      }
      Spacer(Modifier.width(8.dp))
      PlaybackPageGlassSurface(
        backdrop = glassBackdrop,
        modifier =
          Modifier.clip(RoundedCornerShape(18.dp))
            .bangumiPlaybackControlFocus(
              focusRequester = followFocus,
              leftFocus = rateFocus ?: controlFocus?.home ?: FocusRequester.Cancel,
              downFocus = controlFocus?.player ?: FocusRequester.Cancel,
              shape = RoundedCornerShape(18.dp),
            )
            .clickable(enabled = followEnabled, onClick = onFollow)
            .graphicsLayer { alpha = if (followEnabled) 1f else .42f },
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .28f),
        fallbackColor = MaterialTheme.colorScheme.primary.copy(alpha = .72f),
      ) {
        Text(
          if (page.followBusy) "处理中…"
          else if (page.season?.followed == true) "已追"
          else if (page.sourceCard.kind == SpaceContentKind.DRAMA) "追剧" else "追番",
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
          color = resolvedForeground,
        )
      }
      Spacer(Modifier.width(14.dp))
      if (showDeviceStatus) {
        PlaybackPageGlassSurface(
          backdrop = glassBackdrop,
          shape = CircleShape,
        ) {
          DeviceStatusCluster(
            containerColor = Color.Transparent,
            contentColor = resolvedForeground,
          )
        }
      }
    }
  }
  if (showShortReview) {
    BangumiShortReviewDialog(
      title = page.season?.title ?: page.sourceCard.title,
      onDismiss = { showShortReview = false },
      onSubmit = { score, content ->
        showShortReview = false
        onRate(score, content)
      },
    )
  }
}

@Composable
internal fun BangumiLowerPanel(
  page: BangumiPageUi,
  onPosterBoundsChanged: (Rect) -> Unit,
  onOpenDetails: () -> Unit,
  onOpenEpisodeSelection: () -> Unit,
  onEpisodeSelected: (BangumiEpisode) -> Unit,
  onSeasonSelected: (Long) -> Unit,
  panelSlideProgress: () -> Float,
  glassBackdrop: PlaybackPageGlassBackdrop = PlaybackPageGlassBackdrop(),
  foregroundColor: Color = MaterialTheme.colorScheme.onBackground,
  controlFocus: BangumiLowerPanelControlFocus? = null,
  modifier: Modifier = Modifier,
) {
  val movie = page.season?.isMoviePage() == true
  Row(
    modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    BangumiDetailCard(
      page = page,
      onPosterBoundsChanged = onPosterBoundsChanged,
      onClick = onOpenDetails,
      panelSlideProgress = panelSlideProgress,
      glassBackdrop = glassBackdrop,
      foregroundColor = foregroundColor,
      controlFocus = controlFocus,
      modifier = Modifier.weight(if (movie) 7f else 5f).fillMaxHeight(),
    )
    BangumiEpisodeCard(
      page = page,
      movie = movie,
      onOpenEpisodeSelection = onOpenEpisodeSelection,
      onEpisodeSelected = onEpisodeSelected,
      onSeasonSelected = onSeasonSelected,
      panelSlideProgress = panelSlideProgress,
      glassBackdrop = glassBackdrop,
      foregroundColor = foregroundColor,
      controlFocus = controlFocus,
      modifier = Modifier.weight(if (movie) 2f else 3f).fillMaxHeight(),
    )
  }
}

@Composable
private fun BangumiDetailCard(
  page: BangumiPageUi,
  onPosterBoundsChanged: (Rect) -> Unit,
  onClick: () -> Unit,
  panelSlideProgress: () -> Float,
  glassBackdrop: PlaybackPageGlassBackdrop,
  foregroundColor: Color,
  controlFocus: BangumiLowerPanelControlFocus?,
  modifier: Modifier,
) {
  val season = page.season
  Surface(
    modifier =
      modifier
        .bangumiPlaybackControlFocus(
          focusRequester = controlFocus?.detail,
          rightFocus = controlFocus?.episodes ?: FocusRequester.Cancel,
          upFocus = controlFocus?.player ?: FocusRequester.Cancel,
          shape = VideoShapeTokens.Card,
        )
        .clickable(onClick = onClick),
    shape = VideoShapeTokens.Card,
    color = Color.Transparent,
    contentColor = foregroundColor,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box(Modifier.fillMaxSize()) {
      PlaybackPageGlassSurface(
        backdrop = glassBackdrop,
        modifier =
          Modifier.fillMaxSize().graphicsLayer {
            alpha = panelSlideProgress().coerceIn(0f, 1f)
          },
        shape = VideoShapeTokens.Card,
        border = BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {}
      Row(Modifier.fillMaxSize()) {
        CoverImage(
          coverUrl = season?.coverUrl?.takeIf(String::isNotBlank) ?: page.sourceCard.coverUrl,
          contentDescription = page.sourceCard.title,
          modifier =
            Modifier.fillMaxHeight()
              .aspectRatio(3f / 4f)
              .onGloballyPositioned { onPosterBoundsChanged(it.boundsInRoot()) }
              .graphicsLayer { alpha = if (page.posterVisible) 1f else 0f },
          shape = RoundedCornerShape(16.dp),
          enforceAspectRatio = false,
          contentScale = ContentScale.Fit,
          requestWidth = 360,
          requestHeight = 480,
          loadKey = "bangumi-poster-${page.sourceCard.id}-${season?.seasonId}",
        )
        Column(
          Modifier.weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .graphicsLayer { alpha = panelSlideProgress().coerceIn(0f, 1f) },
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            season?.title ?: page.sourceCard.title,
            style = MaterialTheme.typography.headlineSmall,
            color = foregroundColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          BangumiStatLine(season, foregroundColor.copy(alpha = .76f))
          val metadata =
            buildList {
                season?.areas?.takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let(::add)
                season?.styles?.take(3)?.takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let(::add)
                season
                  ?.publishText
                  ?.takeIf(String::isNotBlank)
                  ?.let(::bangumiPublishDate)
                  ?.let(::add)
                season?.typeName?.takeIf(String::isNotBlank)?.let(::add)
              }
              .joinToString("  ·  ")
          if (metadata.isNotBlank()) {
            Text(
              metadata,
              style = MaterialTheme.typography.bodyMedium,
              color = foregroundColor.copy(alpha = .76f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Text(
            "简介：${
            season?.evaluate?.takeIf(String::isNotBlank)
              ?: page.sourceCard.subtitle.takeIf(String::isNotBlank)
              ?: if (page.loading) "正在读取番剧资料…" else "暂无简介"
          }",
            style = MaterialTheme.typography.bodyMedium,
            color = foregroundColor.copy(alpha = .76f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            season?.rating?.let {
              Text(
                "$it 分",
                style = MaterialTheme.typography.titleLarge,
                color = foregroundColor,
                fontWeight = FontWeight.Bold,
              )
            }
            season
              ?.ratingCount
              ?.takeIf { it > 0 }
              ?.let {
                Text(
                  "${formatBangumiCount(it)} 人评分",
                  style = MaterialTheme.typography.labelMedium,
                  color = foregroundColor.copy(alpha = .72f),
                )
              }
            Spacer(Modifier.weight(1f))
            Text(
              "点击查看完整信息",
              style = MaterialTheme.typography.labelSmall,
              color = foregroundColor.copy(alpha = .82f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BangumiStatLine(
  season: BangumiSeason?,
  foregroundColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  val stats =
    listOfNotNull(
        season?.viewCount?.takeIf { it > 0 }?.let { "${formatBangumiCount(it)} 播放" },
        season?.danmakuCount?.takeIf { it > 0 }?.let { "${formatBangumiCount(it)} 弹幕" },
        season?.followCount?.takeIf { it > 0 }?.let { "${formatBangumiCount(it)} 人追番" },
      )
      .joinToString("  ·  ")
  if (stats.isNotBlank()) {
    Text(
      stats,
      style = MaterialTheme.typography.bodyMedium,
      color = foregroundColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun formatBangumiCount(value: Long): String = FeedViewModel.formatCount(value)

internal fun bangumiPublishDate(value: String): String =
  value.trim().substringBefore(' ').substringBefore('T')

internal fun bangumiScoreForStars(stars: Int): Int = stars.coerceIn(1, 5) * 2

@Composable
private fun BangumiEpisodeCard(
  page: BangumiPageUi,
  movie: Boolean,
  onOpenEpisodeSelection: () -> Unit,
  onEpisodeSelected: (BangumiEpisode) -> Unit,
  onSeasonSelected: (Long) -> Unit,
  panelSlideProgress: () -> Float,
  glassBackdrop: PlaybackPageGlassBackdrop,
  foregroundColor: Color,
  controlFocus: BangumiLowerPanelControlFocus?,
  modifier: Modifier,
) {
  var showMenu by remember(page.season?.seasonId) { mutableStateOf(false) }
  val episodes =
    page.season?.episodes.orEmpty().ifEmpty {
      page.season?.sections?.firstOrNull { it.episodes.isNotEmpty() }?.episodes.orEmpty()
    }
  val showingRelatedInsteadOfMain = page.season != null && page.season.episodes.isEmpty()
  PlaybackPageGlassSurface(
    backdrop = glassBackdrop,
    modifier =
      modifier
        .graphicsLayer { alpha = panelSlideProgress().coerceIn(0f, 1f) }
        .bangumiPlaybackControlFocus(
          focusRequester = controlFocus?.episodes,
          leftFocus = controlFocus?.detail ?: FocusRequester.Cancel,
          upFocus = controlFocus?.player ?: FocusRequester.Cancel,
          shape = VideoShapeTokens.Card,
        )
        .then(
          if (controlFocus != null) Modifier.clickable(onClick = onOpenEpisodeSelection) else Modifier
        ),
    shape = VideoShapeTokens.Card,
    border = BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(Modifier.fillMaxSize()) {
      Column(
        Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
      ) {
        Text(
          if (showingRelatedInsteadOfMain) "相关内容" else if (movie) "正片" else "选集",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = foregroundColor,
        )
        Spacer(Modifier.height(8.dp))
        when {
          page.loading && episodes.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("正在读取选集…", color = foregroundColor.copy(alpha = .72f))
            }
          page.error != null && episodes.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                page.error,
                color = foregroundColor.copy(alpha = .82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
          else ->
            LazyVerticalGrid(
              columns = GridCells.Fixed(if (movie) 1 else 4),
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(end = 8.dp, bottom = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(7.dp),
              verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
              items(episodes, key = { it.id }) { episode ->
                BangumiEpisodeNumber(
                  episode = episode,
                  selected = episode.id == page.currentEpisodeId,
                  foregroundColor = foregroundColor,
                  controllerFocusDisabled = controlFocus != null,
                  onClick = { onEpisodeSelected(episode) },
                )
              }
            }
        }
      }
      Surface(
        modifier = Modifier.width(48.dp).fillMaxHeight(),
        color = foregroundColor.copy(alpha = .08f),
        contentColor = foregroundColor,
      ) {
        Column(
          Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          IconButton(
            onClick = { showMenu = true },
            modifier =
              if (controlFocus != null) Modifier.focusProperties { canFocus = false } else Modifier,
          ) {
            Icon(
              Icons.Default.MoreVert,
              contentDescription = "季度和花絮",
              tint = foregroundColor,
            )
          }
          Text(
            "更多",
            style = MaterialTheme.typography.labelSmall,
            color = foregroundColor.copy(alpha = .76f),
          )
        }
      }
    }
  }
  if (showMenu) {
    BangumiEpisodeMenuDialog(
      page = page,
      controlEnabled = controlFocus != null,
      onDismiss = { showMenu = false },
      onSeasonSelected = {
        showMenu = false
        onSeasonSelected(it)
      },
      onEpisodeSelected = {
        showMenu = false
        onEpisodeSelected(it)
      },
    )
  }
}

@Composable
private fun BangumiEpisodeNumber(
  episode: BangumiEpisode,
  selected: Boolean,
  foregroundColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  controlEnabled: Boolean = false,
  controllerFocusDisabled: Boolean = false,
  focusRequester: FocusRequester? = null,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(10.dp)
  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .aspectRatio(1.35f)
        .then(
          if (controllerFocusDisabled) Modifier.focusProperties { canFocus = false } else Modifier
        )
        .bangumiPlaybackControlFocus(
          focusRequester = focusRequester.takeIf { controlEnabled },
          leftFocus = FocusRequester.Default,
          rightFocus = FocusRequester.Default,
          upFocus = FocusRequester.Default,
          downFocus = FocusRequester.Default,
          shape = shape,
        )
        .clickable(enabled = controlEnabled || !selected) {
          if (!selected) onClick()
        },
    shape = shape,
    color =
      if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .22f)
      else foregroundColor.copy(alpha = .08f),
    contentColor = if (selected) foregroundColor else foregroundColor.copy(alpha = .82f),
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(
        episode.title,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * 全屏沿用与页面卡片相同的剧集来源与选中项处理，不改变既有页面卡片或其
 * "更多内容"菜单。
 */
@Composable
internal fun BangumiEpisodeSelectionDialog(
  page: BangumiPageUi,
  controlEnabled: Boolean = false,
  onDismiss: () -> Unit,
  onEpisodeSelected: (BangumiEpisode) -> Unit,
  onSeasonSelected: (Long) -> Unit,
) {
  val episodes = page.playableEpisodes()
  val movie = page.season?.isMoviePage() == true
  val episodeIds = remember(episodes) { episodes.map(BangumiEpisode::id) }
  val episodeFocusRequesters =
    remember(episodeIds) { episodeIds.associateWith { FocusRequester() } }
  val gridState = rememberLazyGridState()
  val scope = rememberCoroutineScope()
  val menuFocusRequester = remember { FocusRequester() }
  val controlFocusVisible = LocalControlFocusVisible.current
  var dialogReady by remember(page.season?.seasonId) { mutableStateOf(false) }
  var showMenu by remember(page.season?.seasonId) { mutableStateOf(false) }
  LaunchedEffect(
    controlEnabled,
    controlFocusVisible,
    dialogReady,
    page.currentEpisodeId,
    episodeIds,
  ) {
    if (!controlEnabled || !controlFocusVisible || !dialogReady || episodes.isEmpty()) {
      return@LaunchedEffect
    }
    val focusIndex = episodes.indexOfFirst { it.id == page.currentEpisodeId }.coerceAtLeast(0)
    gridState.scrollToItem(focusIndex)
    withFrameNanos {}
    val requester = episodeFocusRequesters[episodes[focusIndex].id] ?: return@LaunchedEffect
    // Dialog 使用独立窗口；布局完成不代表窗口焦点已同步到 Compose。跨数帧重试，避免
    // 首次请求落在窗口切换间隙后，整个选集弹层都收不到控制器输入。
    repeat(5) { attempt ->
      if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
      if (attempt < 4) withFrameNanos {}
    }
  }
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier =
        Modifier.widthIn(min = 420.dp, max = 680.dp)
          .heightIn(max = 620.dp)
          .onGloballyPositioned { if (!dialogReady) dialogReady = true },
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 8.dp,
      shadowElevation = 0.dp,
    ) {
      Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (movie) "正片" else "选集",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.weight(1f))
          TextButton(
            onClick = { showMenu = true },
            modifier =
              Modifier.bangumiPlaybackControlFocus(
                focusRequester = menuFocusRequester.takeIf { controlEnabled },
                leftFocus = FocusRequester.Default,
                rightFocus = FocusRequester.Default,
                upFocus = FocusRequester.Default,
                downFocus = FocusRequester.Default,
                shape = RoundedCornerShape(12.dp),
              ),
          ) {
            Text("更多")
          }
          TextButton(onClick = onDismiss) { Text("关闭") }
        }
        Spacer(Modifier.height(12.dp))
        if (episodes.isEmpty()) {
          Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("暂无可选剧集", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(if (movie) 1 else 4),
            state = gridState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(episodes, key = { it.id }) { episode ->
              BangumiEpisodeNumber(
                episode = episode,
                selected = episode.id == page.currentEpisodeId,
                controlEnabled = controlEnabled,
                focusRequester = episodeFocusRequesters[episode.id],
                onClick = { onEpisodeSelected(episode) },
              )
            }
          }
        }
      }
    }
  }
  if (showMenu) {
    BangumiEpisodeMenuDialog(
      page = page,
      controlEnabled = controlEnabled,
      onDismiss = {
        showMenu = false
        if (controlEnabled) {
          scope.launch {
            withFrameNanos {}
            runCatching { menuFocusRequester.requestFocus() }
          }
        }
      },
      onSeasonSelected = onSeasonSelected,
      onEpisodeSelected = onEpisodeSelected,
    )
  }
}

@Composable
private fun BangumiEpisodeMenuDialog(
  page: BangumiPageUi,
  controlEnabled: Boolean = false,
  onDismiss: () -> Unit,
  onSeasonSelected: (Long) -> Unit,
  onEpisodeSelected: (BangumiEpisode) -> Unit,
) {
  val season = page.season ?: return
  val seasons =
    season.seasons.ifEmpty {
      listOf(dev.openbili.webdemo.api.BangumiSeasonOption(season.seasonId, season.title))
    }
  val seasonIds = remember(seasons) { seasons.map { it.seasonId } }
  val seasonFocusRequesters =
    remember(seasonIds) { seasonIds.associateWith { FocusRequester() } }
  val extraEpisodes = remember(season.sections) { season.sections.flatMap { it.episodes } }
  val extraEpisodeIds = remember(extraEpisodes) { extraEpisodes.map(BangumiEpisode::id) }
  val extraEpisodeFocusRequesters =
    remember(extraEpisodeIds) { extraEpisodeIds.associateWith { FocusRequester() } }
  val controlFocusVisible = LocalControlFocusVisible.current
  var dialogReady by remember(season.seasonId) { mutableStateOf(false) }
  LaunchedEffect(controlEnabled, controlFocusVisible, dialogReady, season.seasonId, seasonIds) {
    if (!controlEnabled || !controlFocusVisible || !dialogReady) return@LaunchedEffect
    val fallbackId = seasonIds.firstOrNull() ?: return@LaunchedEffect
    val requester = seasonFocusRequesters[season.seasonId] ?: seasonFocusRequesters[fallbackId]
    if (requester == null) return@LaunchedEffect
    repeat(5) { attempt ->
      if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
      if (attempt < 4) withFrameNanos {}
    }
  }
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier =
        Modifier.widthIn(min = 420.dp, max = 620.dp)
          .heightIn(max = 570.dp)
          .onGloballyPositioned { if (!dialogReady) dialogReady = true },
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 8.dp,
      shadowElevation = 0.dp,
    ) {
      Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("季度与其他内容", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onDismiss) { Text("关闭") }
        }
        Text("季度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(seasons, key = { it.seasonId }) { option ->
            FilterChip(
              selected = option.seasonId == season.seasonId,
              onClick = {
                if (option.seasonId != season.seasonId) onSeasonSelected(option.seasonId)
              },
              label = {
                Text(option.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
              },
              modifier =
                Modifier.bangumiPlaybackControlFocus(
                  focusRequester = seasonFocusRequesters[option.seasonId].takeIf { controlEnabled },
                  leftFocus = FocusRequester.Default,
                  rightFocus = FocusRequester.Default,
                  upFocus = FocusRequester.Default,
                  downFocus = FocusRequester.Default,
                  shape = RoundedCornerShape(12.dp),
                ),
            )
          }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("花絮与其他", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (season.sections.isEmpty()) {
          Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
            Text("暂无其他内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(season.sections, key = { it.id }) { section ->
              Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(section.title, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                  items(section.episodes, key = { it.id }) { episode ->
                    Surface(
                      modifier =
                        Modifier.width(76.dp)
                          .height(48.dp)
                          .bangumiPlaybackControlFocus(
                            focusRequester =
                              extraEpisodeFocusRequesters[episode.id].takeIf { controlEnabled },
                            leftFocus = FocusRequester.Default,
                            rightFocus = FocusRequester.Default,
                            upFocus = FocusRequester.Default,
                            downFocus = FocusRequester.Default,
                            shape = RoundedCornerShape(10.dp),
                          )
                          .clickable { onEpisodeSelected(episode) },
                      shape = RoundedCornerShape(10.dp),
                      color =
                        if (episode.id == page.currentEpisodeId)
                          MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                          episode.title,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          fontWeight = FontWeight.Medium,
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun BangumiInfoDialog(
  page: BangumiPageUi,
  onDismiss: () -> Unit,
  onCacheClick: (() -> Unit)? = null,
) {
  val season = page.season
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(.86f).heightIn(min = 400.dp, max = 620.dp),
      shape = RoundedCornerShape(26.dp),
      tonalElevation = 10.dp,
      shadowElevation = 0.dp,
    ) {
      Row(
        Modifier.fillMaxSize().padding(22.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
      ) {
        CoverImage(
          coverUrl = season?.coverUrl?.takeIf(String::isNotBlank) ?: page.sourceCard.coverUrl,
          contentDescription = page.sourceCard.title,
          modifier = Modifier.width(280.dp).aspectRatio(3f / 4f),
          shape = RoundedCornerShape(16.dp),
          enforceAspectRatio = false,
          contentScale = ContentScale.Fit,
          requestWidth = 840,
          requestHeight = 1120,
        )
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            Row(verticalAlignment = Alignment.Top) {
              Text(
                season?.title ?: page.sourceCard.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
              )
              if (onCacheClick != null) {
                TextButton(onClick = onCacheClick) { Text("缓存视频") }
              }
              TextButton(onClick = onDismiss) { Text("关闭") }
            }
          }
          item { BangumiStatLine(season) }
          item {
            Text(
              listOfNotNull(
                  season?.areas?.joinToString(" / ")?.takeIf(String::isNotBlank),
                  season?.styles?.joinToString(" / ")?.takeIf(String::isNotBlank),
                  season?.publishText?.takeIf(String::isNotBlank)?.let(::bangumiPublishDate),
                  season?.typeName?.takeIf(String::isNotBlank),
                )
                .joinToString("  ·  "),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          season?.rating?.let { rating ->
            item {
              Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Text(
                  "$rating 分",
                  style = MaterialTheme.typography.headlineSmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  "${formatBangumiCount(season.ratingCount)} 人评分",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          item {
            Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
              season?.evaluate?.takeIf(String::isNotBlank)
                ?: page.sourceCard.subtitle.takeIf(String::isNotBlank)
                ?: "暂无简介",
              style = MaterialTheme.typography.bodyLarge,
            )
          }
          item {
            Text(
              "共 ${season?.episodes?.size ?: 0} 集" +
                if (season?.sections?.isNotEmpty() == true)
                  "，另有 ${season.sections.sumOf { it.episodes.size }} 个花絮或其他内容"
                else "",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BangumiShortReviewDialog(
  title: String,
  onDismiss: () -> Unit,
  onSubmit: (Int, String) -> Unit,
) {
  var stars by remember { mutableIntStateOf(5) }
  var content by remember { mutableStateOf("") }
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.widthIn(min = 440.dp, max = 580.dp),
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 8.dp,
      shadowElevation = 0.dp,
    ) {
      Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
          "给《$title》写短评",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          repeat(5) { index ->
            val value = index + 1
            IconButton(onClick = { stars = value }) {
              Icon(
                Icons.Default.Star,
                contentDescription = "$value 星",
                modifier = Modifier.size(32.dp),
                tint =
                  if (value <= stars) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant,
              )
            }
          }
          Spacer(Modifier.width(8.dp))
          Text(
            "$stars 星",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        OutlinedTextField(
          value = content,
          onValueChange = { content = it.take(100) },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("短评（可不填，最多 100 字）") },
          minLines = 3,
          maxLines = 4,
          supportingText = { Text("${content.length}/100") },
        )
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onDismiss) { Text("取消") }
          Button(onClick = { onSubmit(bangumiScoreForStars(stars), content) }) {
            Text("发布短评")
          }
        }
      }
    }
  }
}
