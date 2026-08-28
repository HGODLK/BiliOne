package dev.openbili.webdemo.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.WebViewConfigurator
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** 网页评论接口要求的浏览器环境参数。 */
internal data class BiliCommentWebRiskParameters(
  val environmentToken: String,
  val imageList: String? = null,
  val imageFingerprint: String? = null,
  val coverImageFingerprint: String? = null,
  val interactionFingerprint: String? = null,
) {
  init {
    require(environmentToken.isNotBlank()) { "评论环境令牌为空" }
  }

  /** WBI 只签浏览器行为参数；环境令牌由网页中间件在签名完成后追加。 */
  internal fun wbiParameters(): Map<String, String> {
    val fingerprints =
      listOf(imageList, imageFingerprint, coverImageFingerprint, interactionFingerprint)
    if (fingerprints.all { !it.isNullOrBlank() }) {
      return linkedMapOf(
        "dm_img_list" to imageList.orEmpty(),
        "dm_img_str" to imageFingerprint.orEmpty(),
        "dm_cover_img_str" to coverImageFingerprint.orEmpty(),
        "dm_img_inter" to interactionFingerprint.orEmpty(),
      )
    }
    return mapOf("dm_img_switch" to "0")
  }
}

/** 通过 B 站官方网页 SDK 取得发表评论所需的环境令牌和浏览器行为参数。 */
internal object BiliCommentWebRiskProvider {
  private const val BRIDGE_NAME = "BiliCommentRiskBridge"
  private const val LOAD_TIMEOUT_MS = 12_000L

  suspend fun collect(context: Context): BiliCommentWebRiskParameters =
    withContext(Dispatchers.Main.immediate) { collectOnMain(context) }

  @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
  private suspend fun collectOnMain(context: Context): BiliCommentWebRiskParameters =
    suspendCancellableCoroutine { continuation ->
      val handler = Handler(Looper.getMainLooper())
      BiliHttpClient.syncCookiesToWebView()
      val webView = WebView(context)
      WebViewConfigurator.configure(webView, BuildConfig.DEBUG)
      webView.settings.userAgentString = BiliHttpClient.desktopUserAgent
      webView.webChromeClient = WebChromeClient()

      var finished = false
      lateinit var timeout: Runnable

      fun finish(result: Result<BiliCommentWebRiskParameters>) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        runCatching {
          webView.removeJavascriptInterface(BRIDGE_NAME)
          webView.stopLoading()
          webView.destroy()
        }
        if (!continuation.isActive) return
        result.fold(continuation::resume, continuation::resumeWithException)
      }

      val bridge =
        CommentRiskBridge(
          onSuccess = { parameters -> handler.post { finish(Result.success(parameters)) } },
          onFailure = { message ->
            handler.post {
              finish(Result.failure(IllegalStateException(message.ifBlank { "评论环境校验失败" })))
            }
          },
        )
      timeout = Runnable {
        finish(Result.failure(IllegalStateException("评论环境校验超时，请重试")))
      }

      webView.addJavascriptInterface(bridge, BRIDGE_NAME)
      continuation.invokeOnCancellation { cause ->
        handler.post {
          finish(Result.failure(cause ?: CancellationException("评论环境校验已取消")))
        }
      }
      handler.postDelayed(timeout, LOAD_TIMEOUT_MS)
      webView.loadDataWithBaseURL(
        "https://www.bilibili.com/",
        commentRiskHtml(),
        "text/html",
        "UTF-8",
        null,
      )
    }
}

private class CommentRiskBridge(
  private val onSuccess: (BiliCommentWebRiskParameters) -> Unit,
  private val onFailure: (String) -> Unit,
) {
  @JavascriptInterface
  fun successWithFingerprints(
    token: String,
    imageList: String,
    imageFingerprint: String,
    coverImageFingerprint: String,
    interactionFingerprint: String,
  ) {
    runCatching {
        BiliCommentWebRiskParameters(
          environmentToken = token,
          imageList = imageList,
          imageFingerprint = imageFingerprint,
          coverImageFingerprint = coverImageFingerprint,
          interactionFingerprint = interactionFingerprint,
        )
      }
      .fold(onSuccess, { onFailure(it.message.orEmpty()) })
  }

  @JavascriptInterface
  fun successWithoutFingerprints(token: String) {
    runCatching { BiliCommentWebRiskParameters(environmentToken = token) }
      .fold(onSuccess, { onFailure(it.message.orEmpty()) })
  }

  @JavascriptInterface
  fun failed(message: String) {
    onFailure(message)
  }
}

private fun commentRiskHtml(): String =
  """
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <meta name="spm_prefix" content="333.788">
        <script>
          function reportRiskFailure(message) {
            BiliCommentRiskBridge.failed(String(message || '评论环境组件加载失败'));
          }
        </script>
        <script src="https://s1.hdslb.com/bfs/seed/jinkela/short/user-fingerprint/bili-user-fingerprint.min.js"></script>
        <script
          src="https://s1.hdslb.com/bfs/seed/jinkela/short/minntaki-wasm-sdk/bili-sc-sdk.umd.js"
          onerror="reportRiskFailure('评论环境组件加载失败')"
        ></script>
      </head>
      <body>
        <script>
          (async function () {
            try {
              if (!window.SecureCollectSDK ||
                  typeof window.SecureCollectSDK.getEnvToken !== 'function') {
                throw new Error('评论环境组件不可用');
              }
              const token = await window.SecureCollectSDK.getEnvToken(false);
              if (typeof token !== 'string' || !token) {
                throw new Error('评论环境令牌为空');
              }
              const provider = window.__biliUserFp__;
              if (provider && typeof provider.queryUserLog === 'function') {
                const values = provider.queryUserLog({});
                if (Array.isArray(values) && values.length >= 4) {
                  BiliCommentRiskBridge.successWithFingerprints(
                    token,
                    String(values[0] || ''),
                    String(values[1] || ''),
                    String(values[2] || ''),
                    String(values[3] || '')
                  );
                  return;
                }
              }
              BiliCommentRiskBridge.successWithoutFingerprints(token);
            } catch (error) {
              reportRiskFailure(error && error.message ? error.message : error);
            }
          })();
        </script>
      </body>
    </html>
  """
    .trimIndent()
