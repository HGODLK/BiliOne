package dev.openbili.webdemo.video

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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
import dev.openbili.webdemo.ui.VideoShapeTokens

data class BangumiPageUi(
  val sourceCard: SpaceContentCard,
  val season: BangumiSeason?,
  val loading: Boolean,
  val error: String?,
  val currentEpisodeId: Long,
  val posterVisible: Boolean,
  val followBusy: Boolean = false,
)

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

/** The main episode list is preferred; a section is only treated as playable when it owns the
 * currently selected item or when the season has no main episodes at all. */
internal fun BangumiPageUi.playableEpisodes(): List<BangumiEpisode> {
  val season = season ?: return emptyList()
  val mainEpisodes = season.episodes
  val selectedSectionEpisodes =
    season.sections.firstOrNull { section ->
      section.episodes.any { it.id == currentEpisodeId }
    }?.episodes
  return selectedSectionEpisodes ?: mainEpisodes.ifEmpty {
    season.sections.firstOrNull { it.episodes.isNotEmpty() }?.episodes.orEmpty()
  }
}

internal fun BangumiPageUi.currentEpisodeTitle(): String {
  val episode = playableEpisodes().firstOrNull { it.id == currentEpisodeId }
  return episode?.displayTitle(sourceCard.kind)?.takeIf(String::isNotBlank)
    ?: sourceCard.subtitle.takeIf(String::isNotBlank)
    ?: sourceCard.title
}

internal fun BangumiPageUi.nextPlayableEpisode(): BangumiEpisode? {
  val episodes = playableEpisodes()
  val currentIndex = episodes.indexOfFirst { it.id == currentEpisodeId }
  return episodes.getOrNull(currentIndex + 1).takeIf { currentIndex >= 0 }
}

internal fun BangumiSeason.isMoviePage(): Boolean =
  typeName.contains("电影") ||
    styles.any { it.contains("电影") || it.contains("剧场版") }

@Composable
internal fun BangumiHeader(
  page: BangumiPageUi,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onFollow: () -> Unit,
  onRate: (Int, String) -> Unit,
  panelSlideProgress: () -> Float,
) {
  var showShortReview by remember(page.season?.mediaId) { mutableStateOf(false) }
  val title = page.currentEpisodeTitle()
  Surface(
    modifier =
      Modifier.fillMaxWidth().height(76.dp).graphicsLayer {
        alpha = panelSlideProgress().coerceIn(0f, 1f)
      },
    color = MaterialTheme.colorScheme.background,
    tonalElevation = 2.dp,
  ) {
    Row(
      Modifier.fillMaxSize().padding(end = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack, modifier = Modifier.testTag("bangumi_back_button")) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
      }
      IconButton(onClick = onHome) {
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
      OutlinedButton(
        onClick = { showShortReview = true },
        enabled = page.season?.mediaId?.let { it > 0L } == true,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          page.season?.userRatingScore?.let { "${it / 2} 星" }
            ?: page.season?.rating?.let { "$it 分" }
            ?: "短评"
        )
      }
      Spacer(Modifier.width(8.dp))
      Button(
        onClick = onFollow,
        enabled = !page.followBusy && page.season?.seasonId?.let { it > 0L } == true,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
      ) {
        Text(
          if (page.followBusy) "处理中…"
          else if (page.season?.followed == true) "已追"
          else if (page.sourceCard.kind == SpaceContentKind.DRAMA) "追剧"
          else "追番"
        )
      }
      Spacer(Modifier.width(14.dp))
      DeviceStatusCluster()
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
  onEpisodeSelected: (BangumiEpisode) -> Unit,
  onSeasonSelected: (Long) -> Unit,
  panelSlideProgress: () -> Float,
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
      modifier = Modifier.weight(if (movie) 7f else 5f).fillMaxHeight(),
    )
    BangumiEpisodeCard(
      page = page,
      movie = movie,
      onEpisodeSelected = onEpisodeSelected,
      onSeasonSelected = onSeasonSelected,
      panelSlideProgress = panelSlideProgress,
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
  modifier: Modifier,
) {
  val season = page.season
  Surface(
    modifier = modifier.clickable(onClick = onClick),
    shape = VideoShapeTokens.Card,
    color = Color.Transparent,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
  ) {
    Box(Modifier.fillMaxSize()) {
      Surface(
        modifier =
          Modifier.fillMaxSize().graphicsLayer {
            alpha = panelSlideProgress().coerceIn(0f, 1f)
          },
        shape = VideoShapeTokens.Card,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
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
          Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp, vertical = 9.dp)
            .graphicsLayer { alpha = panelSlideProgress().coerceIn(0f, 1f) },
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
        Text(
          season?.title ?: page.sourceCard.title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        BangumiStatLine(season)
        val metadata =
          buildList {
              season?.areas?.takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let(::add)
              season?.styles?.take(3)?.takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let(::add)
              season?.publishText
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          color = MaterialTheme.colorScheme.onSurfaceVariant,
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
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
          }
          season?.ratingCount?.takeIf { it > 0 }?.let {
            Text(
              "${formatBangumiCount(it)} 人评分",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.weight(1f))
          Text(
            "点击查看完整信息",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        }
      }
    }
  }
}

@Composable
private fun BangumiStatLine(season: BangumiSeason?) {
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
      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
  onEpisodeSelected: (BangumiEpisode) -> Unit,
  onSeasonSelected: (Long) -> Unit,
  panelSlideProgress: () -> Float,
  modifier: Modifier,
) {
  var showMenu by remember(page.season?.seasonId) { mutableStateOf(false) }
  val episodes =
    page.season?.episodes.orEmpty().ifEmpty {
      page.season?.sections?.firstOrNull { it.episodes.isNotEmpty() }?.episodes.orEmpty()
    }
  val showingRelatedInsteadOfMain = page.season != null && page.season.episodes.isEmpty()
  Surface(
    modifier = modifier.graphicsLayer { alpha = panelSlideProgress().coerceIn(0f, 1f) },
    shape = VideoShapeTokens.Card,
    tonalElevation = 2.dp,
    shadowElevation = 2.dp,
    border = BorderStroke(.75.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(Modifier.fillMaxSize()) {
      Column(Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp, top = 10.dp, bottom = 10.dp)) {
        Text(
          if (showingRelatedInsteadOfMain) "相关内容"
          else if (movie) "正片"
          else "选集",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        when {
          page.loading && episodes.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("正在读取选集…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          page.error != null && episodes.isEmpty() ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                page.error,
                color = MaterialTheme.colorScheme.error,
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
                  onClick = { onEpisodeSelected(episode) },
                )
              }
            }
        }
      }
      Surface(
        modifier = Modifier.width(48.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
      ) {
        Column(
          Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "季度和花絮")
          }
          Text(
            "更多",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
  if (showMenu) {
    BangumiEpisodeMenuDialog(
      page = page,
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
  onClick: () -> Unit,
) {
  Surface(
    modifier =
      Modifier.fillMaxWidth().aspectRatio(1.35f).clickable(enabled = !selected, onClick = onClick),
    shape = RoundedCornerShape(10.dp),
    color =
      if (selected) MaterialTheme.colorScheme.primaryContainer
      else MaterialTheme.colorScheme.surfaceVariant,
    contentColor =
      if (selected) MaterialTheme.colorScheme.onPrimaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Fullscreen uses the same episode source and selected-item treatment as the page card without
 * changing the existing page-card or its more-content menu. */
@Composable
internal fun BangumiEpisodeSelectionDialog(
  page: BangumiPageUi,
  onDismiss: () -> Unit,
  onEpisodeSelected: (BangumiEpisode) -> Unit,
) {
  val episodes = page.playableEpisodes()
  val movie = page.season?.isMoviePage() == true
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.widthIn(min = 420.dp, max = 680.dp).heightIn(max = 620.dp),
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 8.dp,
      shadowElevation = 12.dp,
    ) {
      Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (movie) "正片" else "选集",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          Spacer(Modifier.weight(1f))
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
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(episodes, key = { it.id }) { episode ->
              BangumiEpisodeNumber(
                episode = episode,
                selected = episode.id == page.currentEpisodeId,
                onClick = { onEpisodeSelected(episode) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BangumiEpisodeMenuDialog(
  page: BangumiPageUi,
  onDismiss: () -> Unit,
  onSeasonSelected: (Long) -> Unit,
  onEpisodeSelected: (BangumiEpisode) -> Unit,
) {
  val season = page.season ?: return
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      modifier = Modifier.widthIn(min = 420.dp, max = 620.dp).heightIn(max = 570.dp),
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 8.dp,
      shadowElevation = 12.dp,
    ) {
      Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("季度与其他内容", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onDismiss) { Text("关闭") }
        }
        Text("季度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val seasons =
          season.seasons.ifEmpty {
            listOf(dev.openbili.webdemo.api.BangumiSeasonOption(season.seasonId, season.title))
          }
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
                        Modifier.width(76.dp).height(48.dp)
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
      shadowElevation = 14.dp,
    ) {
      Row(Modifier.fillMaxSize().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
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
              TextButton(onClick = onDismiss) { Text("关闭") }
            }
          }
          item { BangumiStatLine(season) }
          item {
            Text(
              listOfNotNull(
                  season?.areas?.joinToString(" / ")?.takeIf(String::isNotBlank),
                  season?.styles?.joinToString(" / ")?.takeIf(String::isNotBlank),
                  season?.publishText
                    ?.takeIf(String::isNotBlank)
                    ?.let(::bangumiPublishDate),
                  season?.typeName?.takeIf(String::isNotBlank),
                )
                .joinToString("  ·  "),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          season?.rating?.let { rating ->
            item {
              Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
      shadowElevation = 12.dp,
    ) {
      Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("给《$title》写短评", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
