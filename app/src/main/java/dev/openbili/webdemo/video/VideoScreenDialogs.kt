package dev.openbili.webdemo.video

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import dev.openbili.webdemo.api.BangumiEpisode
import dev.openbili.webdemo.api.VideoInfo
import dev.openbili.webdemo.api.VideoPage
import dev.openbili.webdemo.api.VideoStream
import dev.openbili.webdemo.feed.FeedItem
import dev.openbili.webdemo.offline.OfflineCacheChooserDialog
import dev.openbili.webdemo.offline.OfflineMediaManager
import dev.openbili.webdemo.offline.OfflineMediaRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

@Composable
internal fun VideoScreenDialogs(
  controlMode: Boolean,
  controlScope: CoroutineScope,
  controlHeaderDetailsFocusRequester: FocusRequester,
  controlHeaderSelectionFocusRequester: FocusRequester,
  controlBangumiDetailFocusRequester: FocusRequester,
  controlBangumiEpisodeFocusRequester: FocusRequester,
  item: FeedItem,
  videoInfo: VideoInfo?,
  onlineViewerText: String?,
  description: String,
  bangumiPage: BangumiPageUi?,
  currentCid: Long,
  onVideoPageSelected: (VideoPage) -> Unit,
  onCollectionEpisodeSelected: (FeedItem, Rect?) -> Unit,
  onBangumiEpisodeSelected: (BangumiEpisode) -> Unit,
  onBangumiSeasonSelected: (Long) -> Unit,
  onControlBangumiEpisodeSelected: () -> Unit,
  onTogglePlayPause: () -> Unit,
  showVideoInfoState: MutableState<Boolean>,
  showVideoSelectionState: MutableState<Boolean>,
  showBangumiEpisodeSelectionState: MutableState<Boolean>,
  showBangumiInfoState: MutableState<Boolean>,
  showOfflineCacheChooserState: MutableState<Boolean>,
  resumeAfterBangumiInfoState: MutableState<Boolean>,
  cacheTargets: List<OfflineMediaRequest>,
  cacheStreams: List<VideoStream>,
  existingOfflineTargetIds: Set<String>,
  currentAccountVipActive: Boolean,
  offlineMediaManager: OfflineMediaManager,
  context: Context,
  commentImagePreview: CommentImagePreviewSession?,
  videoScreenBounds: Rect,
  commentImagePreviewState: MutableState<CommentImagePreviewSession?>,
  commentImagePreviewJobState: MutableState<Job?>,
  previewScope: CoroutineScope,
  reduceMotion: Boolean,
) {
  if (showVideoInfoState.value && bangumiPage == null) {
    VideoInfoTile(
      item = item,
      info = videoInfo,
      onlineViewerText = onlineViewerText,
      description = description,
      onDismiss = {
        showVideoInfoState.value = false
        if (controlMode)
          requestVideoControlFocus(controlScope, controlHeaderDetailsFocusRequester)
      },
      onCacheClick =
        {
            showVideoInfoState.value = false
            showOfflineCacheChooserState.value = true
          }
          .takeUnless { OfflineMediaManager.isOfflineUri(item.videoUrl) },
      controlEnabled = controlMode,
    )
  }
  if (showVideoSelectionState.value && videoInfo != null && bangumiPage == null) {
    VideoSelectionTile(
      info = videoInfo,
      currentCid = currentCid,
      onPageSelected = {
        showVideoSelectionState.value = false
        onVideoPageSelected(it)
      },
      onEpisodeSelected = { episode, bounds ->
        showVideoSelectionState.value = false
        onCollectionEpisodeSelected(feedItemFromCollectionEpisode(episode), bounds)
      },
      onDismiss = {
        showVideoSelectionState.value = false
        if (controlMode)
          requestVideoControlFocus(controlScope, controlHeaderSelectionFocusRequester)
      },
    )
  }
  if (showBangumiEpisodeSelectionState.value && bangumiPage != null) {
    BangumiEpisodeSelectionDialog(
      page = bangumiPage,
      controlEnabled = controlMode,
      onDismiss = {
        showBangumiEpisodeSelectionState.value = false
        if (controlMode)
          requestVideoControlFocus(controlScope, controlBangumiEpisodeFocusRequester)
      },
      onEpisodeSelected = { episode ->
        showBangumiEpisodeSelectionState.value = false
        onBangumiEpisodeSelected(episode)
        if (controlMode) onControlBangumiEpisodeSelected()
      },
      onSeasonSelected = { seasonId ->
        showBangumiEpisodeSelectionState.value = false
        onBangumiSeasonSelected(seasonId)
        if (controlMode) onControlBangumiEpisodeSelected()
      },
    )
  }
  if (showBangumiInfoState.value && bangumiPage != null) {
    BangumiInfoDialog(
      page = bangumiPage,
      onDismiss = {
        showBangumiInfoState.value = false
        if (resumeAfterBangumiInfoState.value) onTogglePlayPause()
        resumeAfterBangumiInfoState.value = false
        if (controlMode)
          requestVideoControlFocus(controlScope, controlBangumiDetailFocusRequester)
      },
      onCacheClick = {
        showBangumiInfoState.value = false
        showOfflineCacheChooserState.value = true
      },
    )
  }
  if (showOfflineCacheChooserState.value) {
    OfflineCacheChooserDialog(
      title = bangumiPage?.season?.title ?: videoInfo?.title ?: item.title,
      targets = cacheTargets,
      streams = cacheStreams,
      existingTargetIds = existingOfflineTargetIds,
      premiumAvailable = currentAccountVipActive,
      onDismiss = {
        showOfflineCacheChooserState.value = false
        if (resumeAfterBangumiInfoState.value) onTogglePlayPause()
        resumeAfterBangumiInfoState.value = false
      },
      onConfirm = { requests ->
        val added = requests.count(offlineMediaManager::enqueue)
        val message =
          if (added > 0) "已加入 $added 个缓存任务" else "所选内容已经在缓存列表中"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        showOfflineCacheChooserState.value = false
        if (resumeAfterBangumiInfoState.value) onTogglePlayPause()
        resumeAfterBangumiInfoState.value = false
      },
    )
  }
  commentImagePreview?.let { session ->
    CommentImagePreviewOverlay(
      session = session,
      rootBounds = videoScreenBounds,
      onDismiss = {
        closeVideoCommentImagePreview(
          previewState = commentImagePreviewState,
          previewJobState = commentImagePreviewJobState,
          scope = previewScope,
          reduceMotion = reduceMotion,
        )
      },
      modifier = Modifier.fillMaxSize().zIndex(100f),
    )
  }
}
