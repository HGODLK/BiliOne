package dev.openbili.webdemo.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.openbili.webdemo.api.BiliFeedApi
import dev.openbili.webdemo.api.FeedCard
import dev.openbili.webdemo.api.FeedResponse
import dev.openbili.webdemo.api.PopularPeriod
import java.util.EnumMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PopularSection(
  val title: String,
  val description: String,
) {
  ALL("综合热门", "各个领域中最新奇好玩的优质内容都在这里~"),
  WEEKLY("每周必看", "每周五晚 18:00 更新"),
  PRECIOUS("入站必刷", "我不允许还有人没看过这些宝藏视频！"),
  RANKING("排行榜", "根据稿件内容质量、近期的数据综合展示，动态更新"),
  MUSIC("全站音乐榜", "发现本周最受欢迎的音乐视频"),
}

enum class PopularRankCategory(
  val title: String,
  val rid: Int,
) {
  ALL("全站", 0),
  GUOCHUANG("国创", 168),
  ANIMATION("动画", 1),
  GAME("游戏", 4),
  MUSIC("音乐", 3),
  DANCE("舞蹈", 129),
  MOVIE("影视", 181),
  ENTERTAINMENT("娱乐", 5),
  KNOWLEDGE("知识", 36),
}

data class PopularFeedUiState(
  val section: PopularSection = PopularSection.ALL,
  val content: FeedUiState = FeedUiState.Loading,
  val weeklyPeriods: List<PopularPeriod> = emptyList(),
  val selectedWeeklyNumber: Int? = null,
  val rankCategory: PopularRankCategory = PopularRankCategory.ALL,
  val musicPeriods: List<PopularPeriod> = emptyList(),
  val selectedMusicListId: Int? = null,
) {
  val isRefreshing: Boolean
    get() = content is FeedUiState.Content && content.isRefreshing
}

private data class PopularCacheKey(
  val section: PopularSection,
  val variant: Int,
)

/** 公开热门页模型：承载 B 站网页暴露的五个分类与各自分页缓存。 */
class PopularFeedViewModel : ViewModel() {
  private val _state = MutableStateFlow(PopularFeedUiState())
  val state: StateFlow<PopularFeedUiState> = _state.asStateFlow()

  private val cache =
    EnumMap<PopularSection, MutableMap<Int, List<FeedItem>>>(PopularSection::class.java)
  private var allPage = 1
  private var allNoMore = false
  private var generation = 0L
  private var loadJob: Job? = null

  init {
    PopularSection.entries.forEach { cache[it] = linkedMapOf() }
    loadCurrent(refresh = false)
  }

  fun selectSection(section: PopularSection) {
    if (_state.value.section == section) return
    loadJob?.cancel()
    val next =
      when (section) {
        // 把已加载的综合列表留在屏幕上。旧实现会在离开的页面（例如入站必刷）上
        // 保留加载态，让快速切回看起来像标签卡死，直到下一次网络响应到达。
        PopularSection.ALL ->
          _state.value.copy(
            section = section,
            content = cachedContent(section, 0),
          )
        PopularSection.WEEKLY ->
          _state.value.copy(
            section = section,
            content = cachedContent(section, _state.value.selectedWeeklyNumber),
          )
        PopularSection.PRECIOUS ->
          _state.value.copy(
            section = section,
            content = cachedContent(section, 0),
          )
        PopularSection.RANKING ->
          _state.value.copy(
            section = section,
            content = cachedContent(section, _state.value.rankCategory.rid),
          )
        PopularSection.MUSIC ->
          _state.value.copy(
            section = section,
            content = cachedContent(section, _state.value.selectedMusicListId),
          )
      }
    _state.value = next
    if (next.content !is FeedUiState.Content) loadCurrent(refresh = false)
  }

  fun selectWeeklyPeriod(period: PopularPeriod) {
    val current = _state.value
    if (current.selectedWeeklyNumber == period.id) return
    loadJob?.cancel()
    val content = cachedContent(PopularSection.WEEKLY, period.id)
    _state.value = current.copy(selectedWeeklyNumber = period.id, content = content)
    if (content !is FeedUiState.Content) loadCurrent(refresh = false)
  }

  fun selectRankCategory(category: PopularRankCategory) {
    val current = _state.value
    if (current.rankCategory == category) return
    loadJob?.cancel()
    val content = cachedContent(PopularSection.RANKING, category.rid)
    _state.value = current.copy(rankCategory = category, content = content)
    if (content !is FeedUiState.Content) loadCurrent(refresh = false)
  }

  fun selectMusicPeriod(period: PopularPeriod) {
    val current = _state.value
    if (current.selectedMusicListId == period.id) return
    loadJob?.cancel()
    val content = cachedContent(PopularSection.MUSIC, period.id)
    _state.value = current.copy(selectedMusicListId = period.id, content = content)
    if (content !is FeedUiState.Content) loadCurrent(refresh = false)
  }

  fun refresh() {
    loadJob?.cancel()
    if (_state.value.section == PopularSection.ALL) {
      allPage = 1
      allNoMore = false
    }
    val current = _state.value
    _state.value =
      current.copy(
        content =
          when (val content = current.content) {
            is FeedUiState.Content -> content.copy(isRefreshing = true, isLoadingMore = false)
            else -> FeedUiState.Loading
          }
      )
    loadCurrent(refresh = true)
  }

  fun loadNextPage() {
    val current = _state.value
    if (current.section != PopularSection.ALL || allNoMore || loadJob?.isActive == true) return
    if (current.content !is FeedUiState.Content || current.content.isRefreshing) return
    _state.value = current.copy(content = current.content.copy(isLoadingMore = true))
    loadCurrent(refresh = false)
  }

  fun consumeRefreshMessage() {
    val current = _state.value
    val content = current.content
    if (content is FeedUiState.Content && content.refreshMessage != null) {
      _state.value = current.copy(content = content.copy(refreshMessage = null))
    }
  }

  private fun loadCurrent(refresh: Boolean) {
    val requestState = _state.value
    val requestGeneration = ++generation
    val section = requestState.section
    loadJob = viewModelScope.launch {
      try {
        var weeklyPeriods = requestState.weeklyPeriods
        var selectedWeeklyNumber = requestState.selectedWeeklyNumber
        var musicPeriods = requestState.musicPeriods
        var selectedMusicListId = requestState.selectedMusicListId
        val requestedPage = allPage
        val response =
          withContext(Dispatchers.IO) {
            when (section) {
              PopularSection.ALL -> BiliFeedApi.getPopularFeed(requestedPage)
              PopularSection.WEEKLY -> {
                if (weeklyPeriods.isEmpty()) weeklyPeriods = BiliFeedApi.getPopularWeeklyPeriods()
                selectedWeeklyNumber = selectedWeeklyNumber ?: weeklyPeriods.firstOrNull()?.id
                selectedWeeklyNumber?.let(BiliFeedApi::getPopularWeekly) ?: FeedResponse(emptyList())
              }
              PopularSection.PRECIOUS -> BiliFeedApi.getPopularPrecious()
              PopularSection.RANKING -> BiliFeedApi.getPopularRanking(requestState.rankCategory.rid)
              PopularSection.MUSIC -> {
                if (musicPeriods.isEmpty()) musicPeriods = BiliFeedApi.getPopularMusicPeriods()
                selectedMusicListId = selectedMusicListId ?: musicPeriods.firstOrNull()?.id
                selectedMusicListId?.let(BiliFeedApi::getPopularMusic) ?: FeedResponse(emptyList())
              }
            }
          }
        if (requestGeneration != generation || _state.value.section != section) return@launch
        val incoming = response.cards.map(::toFeedItem)
        if (section == PopularSection.ALL) {
          if (incoming.isEmpty()) {
            allNoMore = true
            if (refresh && cachedItems(section, 0).isEmpty()) {
              _state.value = _state.value.copy(content = FeedUiState.Empty("暂时没有热门内容"))
            } else {
              val existing = _state.value.content as? FeedUiState.Content
              _state.value =
                _state.value.copy(
                  content =
                    existing?.copy(
                      isRefreshing = false,
                      isLoadingMore = false,
                      refreshMessage = if (refresh) "刷新结果为空，已保留原内容" else null,
                    ) ?: FeedUiState.Empty("暂时没有热门内容")
                )
            }
            return@launch
          }
        } else if (incoming.isEmpty()) {
          _state.value = _state.value.copy(content = FeedUiState.Empty("暂时没有可展示的热门内容"))
          return@launch
        }

        val variant =
          when (section) {
            PopularSection.ALL,
            PopularSection.PRECIOUS -> 0
            PopularSection.WEEKLY -> selectedWeeklyNumber ?: 0
            PopularSection.RANKING -> requestState.rankCategory.rid
            PopularSection.MUSIC -> selectedMusicListId ?: 0
          }
        val currentItems = cache[section].orEmpty()[variant].orEmpty()
        val merged =
          if (section == PopularSection.ALL && !refresh) {
            val seen = currentItems.mapTo(HashSet()) { it.videoUrl }
            (currentItems + incoming.filter { seen.add(it.videoUrl) })
          } else {
            incoming
          }
        cache[section]!![variant] = merged
        if (section == PopularSection.ALL) {
          allPage = requestedPage + 1
        }
        _state.value =
          _state.value.copy(
            content = FeedUiState.Content(items = merged),
            weeklyPeriods = weeklyPeriods,
            selectedWeeklyNumber = selectedWeeklyNumber,
            musicPeriods = musicPeriods,
            selectedMusicListId = selectedMusicListId,
          )
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (requestGeneration != generation || _state.value.section != section) return@launch
        val existing = _state.value.content as? FeedUiState.Content
        _state.value =
          _state.value.copy(
            content =
              existing?.copy(
                isRefreshing = false,
                isLoadingMore = false,
                refreshMessage = "加载失败：${error.message ?: "请稍后重试"}",
              ) ?: FeedUiState.NetworkError(error.message ?: "加载热门失败")
          )
      }
    }
  }

  private fun cachedContent(section: PopularSection, variant: Int?): FeedUiState =
    cache[section].orEmpty()[variant ?: 0]?.takeIf { it.isNotEmpty() }?.let(FeedUiState::Content)
      ?: FeedUiState.Loading

  private fun cachedItems(section: PopularSection, variant: Int): List<FeedItem> =
    cache[section].orEmpty()[variant].orEmpty()

  override fun onCleared() {
    loadJob?.cancel()
  }
}

private fun toFeedItem(card: FeedCard): FeedItem =
  FeedItem(
    id = card.bvid.ifBlank { card.aid.toString() },
    title = card.title,
    videoUrl = "https://www.bilibili.com/video/${card.bvid.ifBlank { "av${card.aid}" }}",
    coverUrl = card.coverUrl,
    uploader = card.uploaderName,
    playCount = FeedViewModel.formatCount(card.playCount),
    duration = FeedViewModel.formatDuration(card.durationSeconds),
    uploaderFace = card.uploaderFace,
    uploaderMid = card.uploaderMid,
    danmakuCount = card.danmakuCount,
    publishedAt = card.pubdate,
    description = card.description,
  )
