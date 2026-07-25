package dev.openbili.webdemo

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewFixtureTest {
  private lateinit var webView: WebView
  private lateinit var scenario: ActivityScenario<TestHostActivity>
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @Before
  fun setup() {
    scenario = ActivityScenario.launch(TestHostActivity::class.java)
    scenario.onActivity { activity ->
      webView = WebView(activity)
      WebViewConfigurator.configure(webView, false)
      activity.setContentView(webView)
    }
  }

  @After
  fun tearDown() {
    instrumentation.runOnMainSync { webView.destroy() }
    scenario.close()
  }

  @Test
  fun localFixtureNavigationAndInjection() {
    val loaded = CountDownLatch(1)
    val assetLoader =
      WebViewAssetLoader.Builder()
        .addPathHandler("/fixtures/", WebViewAssetLoader.AssetsPathHandler(instrumentation.context))
        .build()
    val fixtureUrl = "https://appassets.androidplatform.net/fixtures/feed.html"
    val fixture =
      instrumentation.context.assets.open("fixtures/feed.html").bufferedReader().use {
        it.readText()
      }
    instrumentation.runOnMainSync {
      webView.webViewClient =
        object : WebViewClient() {
          override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
          ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

          @Suppress("DEPRECATION")
          override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(Uri.parse(url))

          override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript(
              "document.documentElement.style.overflowX='hidden';document.documentElement.dataset.injected='yes'",
              null,
            )
            loaded.countDown()
          }
        }
      webView.loadDataWithBaseURL(fixtureUrl, fixture, "text/html", "UTF-8", null)
    }
    assertTrue(loaded.await(10, TimeUnit.SECONDS))
    val result = arrayOfNulls<String>(1)
    val evaluated = CountDownLatch(1)
    instrumentation.runOnMainSync {
      webView.evaluateJavascript("document.documentElement.dataset.injected") {
        result[0] = it
        evaluated.countDown()
      }
    }
    assertTrue(evaluated.await(5, TimeUnit.SECONDS))
    assertEquals("\"yes\"", result[0])
  }

  @Test
  fun allowedAndExternalUrlsUseSamePolicy() {
    assertFalse(UrlPolicy.decide("https://www.bilibili.com/video/BV1x") != UrlDecision.ALLOW)
    assertTrue(UrlPolicy.decide("https://example.com/") != UrlDecision.ALLOW)
    assertEquals(UrlDecision.BLOCK, UrlPolicy.decide("javascript:alert(1)"))
  }
}
