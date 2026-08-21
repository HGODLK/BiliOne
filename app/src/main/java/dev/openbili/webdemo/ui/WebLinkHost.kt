package dev.openbili.webdemo.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import dev.openbili.webdemo.BuildConfig
import dev.openbili.webdemo.WebViewConfigurator
import dev.openbili.webdemo.api.BiliHttpClient

val LocalWebLinkHandler = compositionLocalOf<(String) -> Unit> { {} }

/** 拥有服务器提供的文本链接所使用的唯一应用内系统 WebView 目的地。 */
@Composable
fun WebLinkHost(content: @Composable () -> Unit) {
  var requestedUrl by remember { mutableStateOf<String?>(null) }
  val context = LocalContext.current
  CompositionLocalProvider(
    LocalWebLinkHandler provides
      { rawUrl ->
        normalizeWebUrl(rawUrl)?.let { url ->
          if (!openExternalWebUrl(context, url)) requestedUrl = url
        }
      }
  ) {
    Box(Modifier.fillMaxSize()) {
      content()
      requestedUrl?.let { url ->
        WebLinkOverlay(
          url = url,
          onDismissed = { if (requestedUrl == url) requestedUrl = null },
        )
      }
    }
  }
}

/** 尝试交给系统默认浏览器；没有可用处理程序时由调用方挂载应用内 WebView。 */
internal fun openExternalWebUrl(context: Context, url: String): Boolean {
  return try {
    context.startActivity(
      Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    )
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }
}

internal fun normalizeWebUrl(rawUrl: String): String? {
  val trimmed = rawUrl.trim()
  val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
  return trimmed.takeIf {
    parsed.host?.isNotBlank() == true &&
      (parsed.scheme.equals("http", true) || parsed.scheme.equals("https", true))
  }
}

@Composable
private fun WebLinkOverlay(url: String, onDismissed: () -> Unit) {
  val reveal = remember(url) { Animatable(0f) }
  var webView by remember(url) { mutableStateOf<WebView?>(null) }
  var webMounted by remember(url) { mutableStateOf(false) }
  var pageVisible by remember(url) { mutableStateOf(false) }
  var progress by remember(url) { mutableIntStateOf(0) }
  var title by remember(url) { mutableStateOf("网页链接") }
  var closing by remember(url) { mutableStateOf(false) }

  fun requestClose() {
    if (closing) return
    val current = webView
    if (current?.canGoBack() == true) {
      current.goBack()
    } else {
      closing = true
    }
  }

  BackHandler(onBack = ::requestClose)
  LaunchedEffect(url) {
    reveal.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
    // 加载有意只在不透明转场外壳提交之后才激活。
    webMounted = true
  }
  LaunchedEffect(closing) {
    if (!closing) return@LaunchedEffect
    webMounted = false
    reveal.animateTo(0f, tween(150, easing = FastOutSlowInEasing))
    onDismissed()
  }

  Surface(
    modifier =
      Modifier.fillMaxSize().graphicsLayer {
        alpha = reveal.value
        val scale = .985f + .015f * reveal.value
        scaleX = scale
        scaleY = scale
      },
    color = MaterialTheme.colorScheme.background,
  ) {
    Column(Modifier.fillMaxSize()) {
      Row(
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = ::requestClose) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(
          title.ifBlank { "网页链接" },
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
        )
        IconButton(onClick = { closing = true }) {
          Icon(Icons.Default.Close, contentDescription = "关闭网页")
        }
      }
      if (webMounted && progress in 0..99) {
        LinearProgressIndicator(
          progress = { progress / 100f },
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Box(Modifier.weight(1f).fillMaxWidth()) {
        if (webMounted) {
          AndroidView(
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (pageVisible) 1f else 0f },
            factory = { context ->
              WebView(context).apply {
                WebViewConfigurator.configure(this, BuildConfig.DEBUG)
                BiliHttpClient.syncCookiesToWebView()
                webChromeClient =
                  object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                      progress = newProgress.coerceIn(0, 100)
                    }

                    override fun onReceivedTitle(view: WebView, value: String?) {
                      value?.takeIf(String::isNotBlank)?.let { title = it }
                    }
                  }
                webViewClient =
                  object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                      pageVisible = false
                    }

                    override fun onPageCommitVisible(view: WebView, url: String) {
                      pageVisible = true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                      pageVisible = true
                    }

                    override fun shouldOverrideUrlLoading(
                      view: WebView,
                      request: WebResourceRequest,
                    ): Boolean {
                      val target = normalizeWebUrl(request.url.toString()) ?: return true
                      if (target != request.url.toString()) view.loadUrl(target)
                      return false
                    }

                    @Deprecated("Compatibility callback for older WebView providers")
                    override fun shouldOverrideUrlLoading(view: WebView, target: String): Boolean {
                      val normalized = normalizeWebUrl(target) ?: return true
                      if (normalized != target) view.loadUrl(normalized)
                      return false
                    }
                  }
                webView = this
                loadUrl(url)
              }
            },
            onRelease = { released ->
              if (webView === released) webView = null
              released.stopLoading()
              released.webChromeClient = null
              released.webViewClient = WebViewClient()
              released.removeAllViews()
              released.destroy()
            },
          )
        }
        if (!pageVisible) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
      }
    }
  }
}
