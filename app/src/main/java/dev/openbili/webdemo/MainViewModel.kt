package dev.openbili.webdemo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dev.openbili.webdemo.feed.FeedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
  private val restoredItem = restoreItem()
  private val _state =
    MutableStateFlow(
      AppUiState(
        selectedVideo = restoredItem,
        video =
          if (restoredItem == null) WebViewState(isLoading = false)
          else
            WebViewState(
              currentUrl = savedStateHandle[KEY_URL] ?: restoredItem.videoUrl,
              pageTitle = restoredItem.title,
            ),
      )
    )
  val state: StateFlow<AppUiState> = _state.asStateFlow()

  fun openVideo(item: FeedItem) {
    saveItem(item)
    // Safety net: ensure the video URL uses the desktop host even if the feed item was
    // parsed before a UA-related normalization fix.
    val url =
      UrlPolicy.normalizeVideoUrl(item.videoUrl)
        ?: item.videoUrl.replace("://m.bilibili.com/", "://www.bilibili.com/")
    savedStateHandle[KEY_URL] = url
    _state.value =
      AppUiState(
        selectedVideo = item,
        video =
          WebViewState(
            currentUrl = url,
            pageTitle = item.title,
            isLoading = true,
          ),
      )
  }

  /**
   * Replaces the video rendered by the current video page without creating a new navigation page.
   */
  fun replaceVideoInPlace(item: FeedItem) {
    saveItem(item)
    val url =
      UrlPolicy.normalizeVideoUrl(item.videoUrl)
        ?: item.videoUrl.replace("://m.bilibili.com/", "://www.bilibili.com/")
    savedStateHandle[KEY_URL] = url
    val retainedVideoState = _state.value.video
    _state.value =
      AppUiState(
        selectedVideo = item,
        video =
          retainedVideoState.copy(
            currentUrl = url,
            pageTitle = item.title,
            progress = 0,
            isLoading = true,
            canGoBack = false,
            error = null,
          ),
      )
  }

  fun returnToFeed() {
    clearSavedItem()
    _state.value = AppUiState()
  }

  fun onNavigation(url: String, title: String? = null, canGoBack: Boolean = false) {
    savedStateHandle[KEY_URL] = url
    _state.update { app ->
      app.copy(
        video =
          app.video.copy(
            currentUrl = url,
            // The native video title comes from the selected feed item, never the web page title.
            pageTitle = app.selectedVideo?.title ?: title?.takeIf(String::isNotBlank).orEmpty(),
            canGoBack = canGoBack,
            error = null,
          )
      )
    }
  }

  fun onProgress(progress: Int) = _state.update { app ->
    app.copy(video = app.video.copy(progress = progress, isLoading = progress in 0..99))
  }

  fun onError(error: PageError) = _state.update { app ->
    app.copy(video = app.video.copy(error = error, isLoading = false))
  }

  fun clearError() = _state.update { app ->
    app.copy(video = app.video.copy(error = null, isLoading = true, progress = 0))
  }

  fun onFullscreenChanged(value: Boolean) = _state.update { app ->
    app.copy(video = app.video.copy(isFullscreen = value))
  }

  fun onSubresourceError() = _state.update { app ->
    app.copy(video = app.video.copy(subresourceErrors = app.video.subresourceErrors + 1))
  }

  fun recreateWebView() = _state.update { app ->
    app.copy(
      video =
        app.video.copy(
          error = null,
          webViewGeneration = app.video.webViewGeneration + 1,
          isLoading = true,
        )
    )
  }

  private fun restoreItem(): FeedItem? {
    val rawVideoUrl = savedStateHandle.get<String>(KEY_ITEM_VIDEO_URL) ?: return null
    val normalized = UrlPolicy.normalizeVideoUrl(rawVideoUrl) ?: return null
    val title =
      savedStateHandle.get<String>(KEY_ITEM_TITLE)?.takeIf(String::isNotBlank) ?: return null
    val rawCoverUrl = savedStateHandle.get<String>(KEY_ITEM_COVER_URL) ?: return null
    val normalizedCover = UrlPolicy.normalizeImageUrl(rawCoverUrl) ?: return null
    return FeedItem(
      id = savedStateHandle[KEY_ITEM_ID] ?: normalized.substringAfterLast('/'),
      title = title,
      videoUrl = normalized,
      coverUrl = normalizedCover,
      uploader = savedStateHandle[KEY_ITEM_UPLOADER],
      playCount = savedStateHandle[KEY_ITEM_PLAY_COUNT],
      duration = savedStateHandle[KEY_ITEM_DURATION],
    )
  }

  private fun saveItem(item: FeedItem) {
    savedStateHandle[KEY_ITEM_ID] = item.id
    savedStateHandle[KEY_ITEM_TITLE] = item.title
    savedStateHandle[KEY_ITEM_VIDEO_URL] = item.videoUrl
    savedStateHandle[KEY_ITEM_COVER_URL] = item.coverUrl
    savedStateHandle[KEY_ITEM_UPLOADER] = item.uploader
    savedStateHandle[KEY_ITEM_PLAY_COUNT] = item.playCount
    savedStateHandle[KEY_ITEM_DURATION] = item.duration
  }

  private fun clearSavedItem() {
    listOf(
        KEY_URL,
        KEY_ITEM_ID,
        KEY_ITEM_TITLE,
        KEY_ITEM_VIDEO_URL,
        KEY_ITEM_COVER_URL,
        KEY_ITEM_UPLOADER,
        KEY_ITEM_PLAY_COUNT,
        KEY_ITEM_DURATION,
      )
      .forEach { key -> savedStateHandle.remove<String>(key) }
  }

  companion object {
    private const val KEY_URL = "current_url"
    private const val KEY_ITEM_ID = "video_item_id"
    private const val KEY_ITEM_TITLE = "video_item_title"
    private const val KEY_ITEM_VIDEO_URL = "video_item_url"
    private const val KEY_ITEM_COVER_URL = "video_item_cover"
    private const val KEY_ITEM_UPLOADER = "video_item_uploader"
    private const val KEY_ITEM_PLAY_COUNT = "video_item_play_count"
    private const val KEY_ITEM_DURATION = "video_item_duration"
  }
}
