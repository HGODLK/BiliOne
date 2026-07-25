package dev.openbili.webdemo

import dev.openbili.webdemo.feed.FeedItem

data class AppUiState(
  val selectedVideo: FeedItem? = null,
  val video: WebViewState = WebViewState(isLoading = false),
) {
  val isVideoScreen: Boolean
    get() = selectedVideo != null
}
