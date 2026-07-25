package dev.openbili.webdemo

const val FEED_URL = "https://www.bilibili.com/"

data class WebViewState(
  val currentUrl: String = FEED_URL,
  val pageTitle: String = "哔哩网页 Demo",
  val progress: Int = 0,
  val isLoading: Boolean = true,
  val canGoBack: Boolean = false,
  val isFullscreen: Boolean = false,
  val error: PageError? = null,
  val subresourceErrors: Int = 0,
  val webViewGeneration: Int = 0,
)

sealed interface PageError {
  val detail: String

  data class Network(override val detail: String) : PageError

  data class Timeout(override val detail: String) : PageError

  data class Http(val statusCode: Int, override val detail: String) : PageError

  data class Ssl(override val detail: String) : PageError

  data class Renderer(override val detail: String) : PageError

  data class ExternalOpen(override val detail: String) : PageError
}

enum class BackAction {
  EXIT_FULLSCREEN,
  WEB_HISTORY,
  RETURN_TO_FEED,
  FINISH_ACTIVITY,
}

fun resolveBackAction(isFullscreen: Boolean, canGoBack: Boolean): BackAction =
  when {
    isFullscreen -> BackAction.EXIT_FULLSCREEN
    canGoBack -> BackAction.WEB_HISTORY
    else -> BackAction.FINISH_ACTIVITY
  }

fun resolveAppBackAction(isFullscreen: Boolean, isVideoScreen: Boolean): BackAction =
  when {
    isFullscreen -> BackAction.EXIT_FULLSCREEN
    isVideoScreen -> BackAction.RETURN_TO_FEED
    else -> BackAction.FINISH_ACTIVITY
  }
