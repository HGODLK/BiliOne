package dev.openbili.webdemo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import dev.openbili.webdemo.api.BangumiExploreCategory
import dev.openbili.webdemo.api.BangumiIndexItem
import dev.openbili.webdemo.api.BangumiIndexOrder
import dev.openbili.webdemo.api.BangumiIndexQuery
import dev.openbili.webdemo.bangumi.BangumiIndexUiState
import dev.openbili.webdemo.feed.CoverImage
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos

private data class BangumiIndexOption(val label: String, val value: String)

private enum class BangumiIndexFilterKind(val title: String) {
  TYPE("类型"),
  LANGUAGE("配音"),
  AREA("地区"),
  FINISHED("状态"),
  COPYRIGHT("版权"),
  PAYMENT("付费"),
  MONTH("季度"),
  YEAR("年份"),
  STYLE("风格"),
}

private val animeIndexOtherAreas = (1..70).filterNot { it == 2 || it == 3 }.joinToString(",")

private val bangumiIndexFilterOptions =
  mapOf(
    BangumiIndexFilterKind.TYPE to
      listOf("全部" to "-1", "正片" to "1", "电影" to "2", "其他" to "3"),
    BangumiIndexFilterKind.LANGUAGE to listOf("全部" to "-1", "原声" to "1", "中文配音" to "2"),
    BangumiIndexFilterKind.AREA to
      listOf("全部" to "-1", "日本" to "2", "美国" to "3", "其他" to animeIndexOtherAreas),
    BangumiIndexFilterKind.FINISHED to listOf("全部" to "-1", "完结" to "1", "连载" to "0"),
    BangumiIndexFilterKind.COPYRIGHT to listOf("全部" to "-1", "独家" to "3", "其他" to "1,2,4"),
    BangumiIndexFilterKind.PAYMENT to
      listOf("全部" to "-1", "免费" to "1", "付费" to "2,6", "大会员" to "4,6"),
    BangumiIndexFilterKind.MONTH to listOf("全部" to "-1", "1月" to "1", "4月" to "4", "7月" to "7", "10月" to "10"),
    BangumiIndexFilterKind.YEAR to bangumiIndexYears(),
    BangumiIndexFilterKind.STYLE to bangumiIndexStyles(),
  )

/**
 * Filter chips each category's index exposes. Only filters with verified/shared values are shown —
 * taxonomies whose numeric ids are not yet verified (style/area/producer for non-anime) are omitted
 * rather than fabricated. Anime keeps its full verified set.
 */
private fun filterKindsFor(category: BangumiExploreCategory): List<BangumiIndexFilterKind> =
  when (category) {
    BangumiExploreCategory.ANIME -> BangumiIndexFilterKind.entries.toList()
    BangumiExploreCategory.GUOCHUANG ->
      listOf(
        BangumiIndexFilterKind.TYPE,
        BangumiIndexFilterKind.FINISHED,
        BangumiIndexFilterKind.COPYRIGHT,
        BangumiIndexFilterKind.PAYMENT,
        BangumiIndexFilterKind.YEAR,
      )
    BangumiExploreCategory.MOVIE,
    BangumiExploreCategory.TV,
    BangumiExploreCategory.DOCUMENTARY ->
      listOf(BangumiIndexFilterKind.PAYMENT, BangumiIndexFilterKind.YEAR)
    BangumiExploreCategory.VARIETY -> listOf(BangumiIndexFilterKind.PAYMENT)
  }

/** Movie/TV/documentary filter by release-date range instead of the anime `year` field. */
private fun usesReleaseDate(category: BangumiExploreCategory): Boolean =
  category == BangumiExploreCategory.MOVIE ||
    category == BangumiExploreCategory.TV ||
    category == BangumiExploreCategory.DOCUMENTARY

private fun filterOptionsFor(
  kind: BangumiIndexFilterKind,
  category: BangumiExploreCategory,
): List<Pair<String, String>> =
  if (kind == BangumiIndexFilterKind.YEAR && usesReleaseDate(category)) bangumiIndexReleaseYears()
  else bangumiIndexFilterOptions.getValue(kind)

@Composable
internal fun BangumiIndexScreen(
  state: BangumiIndexUiState,
  gridState: LazyGridState = rememberLazyGridState(),
  onBack: () -> Unit,
  onReset: () -> Unit,
  onOrderSelected: (BangumiIndexOrder) -> Unit,
  onSortDirectionToggle: () -> Unit,
  onQueryChanged: ((BangumiIndexQuery) -> BangumiIndexQuery) -> Unit,
  onKeywordChange: (String) -> Unit,
  onRefresh: () -> Unit,
  onLoadMore: () -> Unit,
  onRetry: () -> Unit,
  onOpen: (BangumiIndexItem, Rect) -> Unit,
  onItemBounds: (BangumiIndexItem, Rect) -> Unit,
  hiddenItemId: String?,
  foregroundActive: Boolean,
  modifier: Modifier = Modifier,
) {
  var expandedFilter by remember { mutableStateOf<BangumiIndexFilterKind?>(null) }
  val filterBounds = remember { mutableStateMapOf<BangumiIndexFilterKind, Rect>() }
  val density = LocalDensity.current
  BackHandler(enabled = expandedFilter != null) { expandedFilter = null }
  val selectedFilter = expandedFilter
  val query = state.query
  val visibleError = state.visibleError
  val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
  LaunchedEffect(lastVisibleIndex, state.visibleItems.size, state.visibleHasNext, state.visibleInitialLoading, state.visibleLoadingMore) {
    if (
      state.visibleItems.isNotEmpty() &&
        state.visibleHasNext &&
        !state.visibleInitialLoading &&
        !state.visibleLoadingMore &&
        lastVisibleIndex >= state.visibleItems.lastIndex - 5
    ) {
      onLoadMore()
    }
  }

  Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      BangumiIndexToolbar(
        title = "${state.category.label}索引",
        total = state.visibleTotal,
        onBack = onBack,
        onReset = onReset,
      )
      // Local keyword filter (the index result API has no keyword param), styled after the
      // favorites search box.
      OutlinedTextField(
        value = state.keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 8.dp),
        singleLine = true,
        label = { Text("搜索${state.category.label}") },
        placeholder = { Text("搜索全部${state.category.label}内容") },
      )
      if (!state.searching) {
        BangumiIndexOrders(
          query = query,
          onOrderSelected = onOrderSelected,
          onSortDirectionToggle = onSortDirectionToggle,
        )
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          filterKindsFor(state.category).forEach { kind ->
            val value = filterValue(query, kind, state.category)
            val label = filterOptionsFor(kind, state.category).firstOrNull { it.second == value }?.first ?: "全部"
            IndexTextAction(
              label = if (value == "-1") kind.title else "${kind.title}·$label",
              active = value != "-1",
              onClick = { expandedFilter = kind },
              modifier =
                Modifier.onGloballyPositioned { filterBounds[kind] = it.boundsInRoot() },
            )
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      when {
        state.visibleInitialLoading -> BangumiIndexLoading(Modifier.weight(1f))
        state.visibleItems.isEmpty() && visibleError != null -> BangumiIndexError(visibleError, onRetry, Modifier.weight(1f))
        state.visibleItems.isEmpty() -> BangumiIndexEmpty(state.category, keywordActive = state.searching, modifier = Modifier.weight(1f))
        else ->
          PullRefreshContainer(
            refreshing = state.visibleInitialLoading,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
          ) {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
          ) {
            items(state.visibleItems, key = BangumiIndexItem::stableId) { item ->
              BangumiIndexCard(
                item = item,
                hidden = item.stableId == hiddenItemId,
                foregroundActive = foregroundActive,
                onOpen = onOpen,
                onBounds = onItemBounds,
              )
            }
            if (state.visibleLoadingMore) {
              item(key = "index-loading-more") { CircularProgressIndicator(Modifier.padding(16.dp).size(28.dp), strokeWidth = 2.dp) }
            }
          }
          }
      }
    }
    selectedFilter?.let { kind ->
      val options = filterOptionsFor(kind, state.category).map { (label, value) -> BangumiIndexOption(label, value) }
      if (kind == BangumiIndexFilterKind.YEAR || kind == BangumiIndexFilterKind.STYLE) {
        BangumiIndexOptionPanel(
          title = kind.title,
          options = options,
          selected = filterValue(query, kind, state.category),
          onSelect = { value ->
            onQueryChanged { current -> current.withFilter(kind, value, state.category) }
            expandedFilter = null
          },
          onDismiss = { expandedFilter = null },
        )
      } else {
        DropdownMenu(
          expanded = true,
          onDismissRequest = { expandedFilter = null },
          offset =
            filterBounds[kind]?.let { bounds ->
              DpOffset(
                x = with(density) { bounds.left.toDp() },
                y = with(density) { bounds.bottom.toDp() },
              )
            } ?: DpOffset.Zero,
          modifier = Modifier.widthIn(min = 176.dp, max = 240.dp),
        ) {
          options.forEach { option ->
            DropdownMenuItem(
              text = { Text(option.label) },
              onClick = {
                onQueryChanged { current -> current.withFilter(kind, option.value, state.category) }
                expandedFilter = null
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BangumiIndexToolbar(title: String, total: Int, onBack: () -> Unit, onReset: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TextButton(onClick = onBack) { Text("返回") }
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (total > 0) {
      Text(
        "共 $total 部",
        modifier = Modifier.padding(start = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.weight(1f))
    TextButton(onClick = onReset) { Text("重置") }
  }
}

@Composable
private fun BangumiIndexOrders(
  query: BangumiIndexQuery,
  onOrderSelected: (BangumiIndexOrder) -> Unit,
  onSortDirectionToggle: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("排序", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(BangumiIndexOrder.entries.toList()) { order ->
        IndexTextAction(
          label = order.label,
          active = query.order == order,
          onClick = { onOrderSelected(order) },
        )
      }
    }
    IndexTextAction(
      label = if (query.sortDescending) "↓" else "↑",
      active = true,
      onClick = onSortDirectionToggle,
    )
  }
}

@Composable
private fun IndexTextAction(
  label: String,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.height(48.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
      color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      textDecoration = if (active) TextDecoration.Underline else TextDecoration.None,
    )
  }
}

@Composable
private fun BangumiIndexOptionPanel(
  title: String,
  options: List<BangumiIndexOption>,
  selected: String,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)).clickable(onClick = onDismiss)) {
    Surface(
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(392.dp).padding(vertical = 16.dp, horizontal = 12.dp),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
      Column(Modifier.padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
          Spacer(Modifier.weight(1f))
          TextButton(onClick = onDismiss) { Text("完成") }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(options, key = { it.value }) { option ->
            IndexTextAction(
              label = option.label,
              active = option.value == selected,
              onClick = { onSelect(option.value) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BangumiIndexCard(
  item: BangumiIndexItem,
  hidden: Boolean,
  foregroundActive: Boolean,
  onOpen: (BangumiIndexItem, Rect) -> Unit,
  onBounds: (BangumiIndexItem, Rect) -> Unit,
) {
  var bounds by remember(item.stableId) { mutableStateOf(Rect.Zero) }
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  val scope = rememberCoroutineScope()
  var opening by remember(item.stableId) { mutableStateOf(false) }
  val chromeAlpha = remember(item.stableId) { Animatable(1f) }
  LaunchedEffect(hidden, foregroundActive) {
    if (opening && !hidden && foregroundActive) {
      opening = false
      chromeAlpha.animateTo(1f, tween(MotionTokens.Standard))
    }
  }
  Box(
    Modifier.fillMaxWidth()
      .aspectRatio(3f / 4f)
      .bringIntoViewRequester(bringIntoViewRequester)
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .clickable(enabled = !opening && !hidden) {
        opening = true
        scope.launch {
          bringIntoViewRequester.bringIntoView()
          chromeAlpha.animateTo(0f, tween(MotionTokens.Standard))
          withFrameNanos {}
          onOpen(item, bounds)
        }
      },
  ) {
    CoverImage(
      coverUrl = item.coverUrl,
      contentDescription = item.title,
      modifier =
        Modifier.fillMaxSize()
          .onGloballyPositioned { coordinates ->
            bounds = coordinates.boundsInRoot()
            onBounds(item, bounds)
          }
          .graphicsLayer { alpha = if (hidden) 0f else 1f },
      shape = RoundedCornerShape(16.dp),
      enforceAspectRatio = false,
      requestWidth = 480,
      requestHeight = 640,
      loadKey = "bangumi-index-${item.stableId}",
      contentScale = ContentScale.Crop,
    )
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer { alpha = chromeAlpha.value }
        .background(
          Brush.verticalGradient(
            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .78f)),
          ),
        ),
    )
    if (item.score.isNotBlank()) {
      Surface(
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).graphicsLayer { alpha = chromeAlpha.value },
        color = MaterialTheme.colorScheme.primary.copy(alpha = .92f),
        shape = RoundedCornerShape(9.dp),
      ) {
        Text(
          item.score,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
          color = MaterialTheme.colorScheme.onPrimary,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    if (item.badge.isNotBlank()) {
      Surface(
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).graphicsLayer { alpha = chromeAlpha.value },
        color = Color(0xFFFB7299),
        shape = RoundedCornerShape(8.dp),
      ) {
        Text(
          item.badge,
          modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
          color = Color.White,
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
    Column(
      Modifier.align(Alignment.BottomStart).padding(12.dp).graphicsLayer { alpha = chromeAlpha.value },
      verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
        item.title,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
      )
      val meta = item.indexShow.ifBlank { item.subtitle }.ifBlank { item.orderText }
      if (meta.isNotBlank()) {
        Text(
          meta,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          color = Color.White.copy(alpha = .82f),
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

@Composable
private fun BangumiIndexLoading(modifier: Modifier) {
  Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(strokeWidth = 2.dp) }
}

@Composable
private fun BangumiIndexError(message: String, onRetry: () -> Unit, modifier: Modifier) {
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = onRetry) { Text("重试") }
  }
}

@Composable
private fun BangumiIndexEmpty(
  category: BangumiExploreCategory,
  keywordActive: Boolean,
  modifier: Modifier,
) {
  Box(modifier, contentAlignment = Alignment.Center) {
    Text(
      if (keywordActive) "没有找到相关${category.label}" else "没有符合条件的${category.label}",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun filterValue(
  query: BangumiIndexQuery,
  kind: BangumiIndexFilterKind,
  category: BangumiExploreCategory,
): String =
  when (kind) {
    BangumiIndexFilterKind.TYPE -> query.seasonVersion
    BangumiIndexFilterKind.LANGUAGE -> query.spokenLanguageType
    BangumiIndexFilterKind.AREA -> query.area
    BangumiIndexFilterKind.FINISHED -> query.isFinish
    BangumiIndexFilterKind.COPYRIGHT -> query.copyright
    BangumiIndexFilterKind.PAYMENT -> query.seasonStatus
    BangumiIndexFilterKind.MONTH -> query.seasonMonth
    BangumiIndexFilterKind.YEAR -> if (usesReleaseDate(category)) query.releaseDate else query.year
    BangumiIndexFilterKind.STYLE -> query.styleId
  }

private fun BangumiIndexQuery.withFilter(
  kind: BangumiIndexFilterKind,
  value: String,
  category: BangumiExploreCategory,
): BangumiIndexQuery =
  when (kind) {
    BangumiIndexFilterKind.TYPE -> copy(seasonVersion = value)
    BangumiIndexFilterKind.LANGUAGE -> copy(spokenLanguageType = value)
    BangumiIndexFilterKind.AREA -> copy(area = value)
    BangumiIndexFilterKind.FINISHED -> copy(isFinish = value)
    BangumiIndexFilterKind.COPYRIGHT -> copy(copyright = value)
    BangumiIndexFilterKind.PAYMENT -> copy(seasonStatus = value)
    BangumiIndexFilterKind.MONTH -> copy(seasonMonth = value)
    BangumiIndexFilterKind.YEAR ->
      if (usesReleaseDate(category)) copy(releaseDate = value) else copy(year = value)
    BangumiIndexFilterKind.STYLE -> copy(styleId = value)
  }

private fun bangumiIndexYears(): List<Pair<String, String>> {
  val currentYear = java.time.Year.now().value
  return listOf("全部" to "-1") +
    (currentYear downTo 2015).map { it.toString() to "[$it,${it + 1})" } +
    listOf("2014-2010" to "[2010,2015)", "2009-2005" to "[2005,2010)", "2004-2000" to "[2000,2005)", "90年代" to "[1990,2000)", "80年代" to "[1980,1990)", "更早" to "[,1980)")
}

/**
 * Release-date range values for movie/TV/documentary, using the documented left-closed/right-open
 * format from 番剧二级子页接口文档.md §1.4.3 (e.g. [2024-01-01 00:00:00,2025-01-01 00:00:00)).
 * Only single years are offered; open-ended "earlier" ranges are not documented for release_date.
 */
private fun bangumiIndexReleaseYears(): List<Pair<String, String>> {
  val currentYear = java.time.Year.now().value
  fun range(year: Int) = "[$year-01-01 00:00:00,${year + 1}-01-01 00:00:00)"
  return listOf("全部" to "-1") + (currentYear downTo 2010).map { it.toString() to range(it) }
}

private fun bangumiIndexStyles(): List<Pair<String, String>> =
  listOf(
    "全部" to "-1", "原创" to "10010", "漫画改" to "10011", "小说改" to "10012", "游戏改" to "10013", "特摄" to "10014", "布袋戏" to "10015", "热血" to "10016", "穿越" to "10017", "奇幻" to "10018", "战斗" to "10019", "搞笑" to "10020", "日常" to "10021", "科幻" to "10022", "萌系" to "10023", "治愈" to "10024", "校园" to "10025", "少儿" to "10026", "泡面" to "10027", "恋爱" to "10028", "少女" to "10029", "魔法" to "10030", "冒险" to "10031", "历史" to "10032", "架空" to "10033", "机战" to "10034", "神魔" to "10035", "声控" to "10036", "运动" to "10037", "励志" to "10038", "音乐" to "10039", "推理" to "10040", "社团" to "10041", "智斗" to "10042", "催泪" to "10043", "美食" to "10044", "偶像" to "10045", "乙女" to "10046", "职场" to "10047",
  )
