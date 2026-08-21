package dev.openbili.webdemo.video

import dev.openbili.webdemo.PlayerState
import dev.openbili.webdemo.PlayerSubtitleState
import dev.openbili.webdemo.api.VideoInfo

/** 控制器专用页的菜单数据，避免把菜单拼装逻辑继续堆进根页面组合体。 */
internal data class ControllerPlaybackMenu(
  val selectionItems: List<ControllerPlaybackActionItem>,
  val selectionGroups: List<ControllerPlaybackSelectionGroup>,
  val qualityItems: List<ControllerPlaybackActionItem>,
  val subtitleItems: List<ControllerPlaybackActionItem>,
  val moreItems: List<ControllerPlaybackActionItem>,
)

/** 选集面板的一层分组；items 与 children 二选一，children 可继续承载 50 项子分组。 */
internal data class ControllerPlaybackSelectionGroup(
  val key: String,
  val label: String,
  val items: List<ControllerPlaybackActionItem> = emptyList(),
  val children: List<ControllerPlaybackSelectionGroup> = emptyList(),
)

private const val CONTROLLER_SELECTION_GROUP_SIZE = 50

internal fun buildControllerPlaybackMenu(
  bangumiPage: BangumiPageUi?,
  videoInfo: VideoInfo?,
  playerState: PlayerState,
  subtitleState: PlayerSubtitleState,
  playbackSpeed: Float,
): ControllerPlaybackMenu {
  val bangumiItems =
    bangumiPage?.playableEpisodes()?.takeIf { it.size > 1 }?.map { episode ->
      ControllerPlaybackActionItem(
        key = "bangumi:${episode.id}",
        label = episode.displayTitle(bangumiPage.sourceCard.kind),
      )
    }.orEmpty()
  val videoGroups =
    videoInfo?.let { info ->
      videoSelectionGroups(info.bvid, info.pages, info.collection).map { group ->
        val items =
          if (group.pages.isNotEmpty()) {
            group.pages.map { page ->
              ControllerPlaybackActionItem(
                key = "page:${page.page}",
                label = "${page.page}. ${page.part.ifBlank { "分P ${page.page}" }}",
              )
            }
          } else {
            group.episodes.map { episode ->
              ControllerPlaybackActionItem(
                key = "collection:${episode.bvid}:${episode.cid}",
                label = episode.title.ifBlank { episode.bvid },
              )
            }
          }
        ControllerPlaybackSelectionGroup(
          key = group.key,
          label = group.title,
          items = items,
        )
      }
    }.orEmpty()
  val selectionItems: List<ControllerPlaybackActionItem>
  val selectionGroups: List<ControllerPlaybackSelectionGroup>
  if (bangumiItems.isNotEmpty()) {
    val chunked = chunkControllerSelectionItems("bangumi", "选集", bangumiItems)
    if (chunked.size == 1 && chunked.first().children.isEmpty()) {
      selectionItems = bangumiItems
      selectionGroups = emptyList()
    } else {
      selectionItems = emptyList()
      selectionGroups = chunked
    }
  } else {
    val preparedGroups = videoGroups.map(::prepareControllerSelectionGroup)
    if (preparedGroups.size == 1 && preparedGroups.first().children.isEmpty()) {
      selectionItems = preparedGroups.first().items
      selectionGroups = emptyList()
    } else {
      selectionItems = emptyList()
      selectionGroups = preparedGroups
    }
  }
  val qualityItems =
    (playerState as? PlayerState.Ready)?.playData?.streams.orEmpty().mapIndexed { index, stream ->
      ControllerPlaybackActionItem("quality:$index", stream.quality)
    }
  val subtitleItems = buildList {
    add(ControllerPlaybackActionItem("subtitle:off", "关闭字幕"))
    subtitleState.tracks.forEach { track ->
      add(ControllerPlaybackActionItem("subtitle:${track.id}", track.languageLabel))
    }
  }
  val moreItems = buildList {
    listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
      val label = if (speed == playbackSpeed) "倍速 · ${speed}x（当前）" else "倍速 · ${speed}x"
      add(ControllerPlaybackActionItem("speed:$speed", label))
    }
  }
  return ControllerPlaybackMenu(
    selectionItems = selectionItems,
    selectionGroups = selectionGroups,
    qualityItems = qualityItems,
    subtitleItems = subtitleItems,
    moreItems = moreItems,
  )
}

private fun prepareControllerSelectionGroup(
  group: ControllerPlaybackSelectionGroup
): ControllerPlaybackSelectionGroup {
  if (group.items.size <= CONTROLLER_SELECTION_GROUP_SIZE) return group
  return group.copy(
    items = emptyList(),
    children =
      group.items.chunked(CONTROLLER_SELECTION_GROUP_SIZE).mapIndexed { index, items ->
        ControllerPlaybackSelectionGroup(
          key = "${group.key}:chunk:${index + 1}",
          label = "第 ${index * CONTROLLER_SELECTION_GROUP_SIZE + 1}-${index * CONTROLLER_SELECTION_GROUP_SIZE + items.size} 项",
          items = items,
        )
      },
  )
}

private fun chunkControllerSelectionItems(
  key: String,
  label: String,
  items: List<ControllerPlaybackActionItem>,
): List<ControllerPlaybackSelectionGroup> =
  if (items.size <= CONTROLLER_SELECTION_GROUP_SIZE) {
    listOf(ControllerPlaybackSelectionGroup(key = key, label = label, items = items))
  } else {
    items.chunked(CONTROLLER_SELECTION_GROUP_SIZE).mapIndexed { index, chunk ->
      ControllerPlaybackSelectionGroup(
        key = "$key:chunk:${index + 1}",
        label = "第 ${index * CONTROLLER_SELECTION_GROUP_SIZE + 1}-${index * CONTROLLER_SELECTION_GROUP_SIZE + chunk.size} 项",
        items = chunk,
      )
    }
  }
