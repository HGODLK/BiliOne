package dev.openbili.webdemo.feed

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import dev.openbili.webdemo.TestHostActivity
import dev.openbili.webdemo.WebViewConfigurator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedPageExtractorInstrumentedTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private lateinit var webView: WebView
  private lateinit var assetLoader: WebViewAssetLoader
  private lateinit var scenario: ActivityScenario<TestHostActivity>

  @Before
  fun setUp() {
    assetLoader =
      WebViewAssetLoader.Builder()
        .addPathHandler("/fixtures/", WebViewAssetLoader.AssetsPathHandler(instrumentation.context))
        .build()
    scenario = ActivityScenario.launch(TestHostActivity::class.java)
    scenario.onActivity { activity ->
      webView = WebView(activity)
      WebViewConfigurator.configure(webView, false)
      activity.setContentView(webView)
    }
  }

  @After
  fun tearDown() {
    instrumentation.runOnMainSync {
      webView.stopLoading()
      webView.destroy()
    }
    scenario.close()
  }

  @Test
  fun extractsSemanticCardsAndFiltersInvalidFixtures() {
    loadFixture("feed-extraction.html")

    val result = runBlocking {
      FeedPageExtractor(
          context = instrumentation.targetContext,
          maxAttempts = 2,
          retryDelayMillis = 10,
          evaluationTimeoutMillis = 2_000,
        )
        .extract(webView)
    }

    assertTrue(result is FeedExtractionResult.Success)
    result as FeedExtractionResult.Success
    assertEquals(4, result.items.size)
    assertEquals(
      setOf("BVNORMAL1", "BVLAZY1", "BVRELATIVE1", "BVIMAGE1"),
      result.items.map { it.id }.toSet(),
    )
    assertEquals("https://www.bilibili.com/video/BVNORMAL1", result.items.first().videoUrl)
    assertTrue(result.items.single { it.id == "BVLAZY1" }.coverUrl.startsWith("https://"))
    assertTrue(result.items.single { it.id == "BVIMAGE1" }.coverUrl.startsWith("https://"))
    assertTrue(result.stats.duplicateItems >= 1)
    assertTrue(result.stats.filteredMissingTitle >= 1)
    assertTrue(result.stats.filteredMissingCover >= 1)
  }

  @Test
  fun emptyFixtureReturnsExplicitEmptyResultAfterFiniteRetries() {
    loadFixture("feed-extraction-empty.html")

    val result = runBlocking {
      FeedPageExtractor(
          context = instrumentation.targetContext,
          maxAttempts = 2,
          retryDelayMillis = 10,
          evaluationTimeoutMillis = 2_000,
        )
        .extract(webView)
    }

    assertTrue(result is FeedExtractionResult.Empty)
    assertEquals(0, result.stats.videoLinksFound)
  }

  private fun loadFixture(name: String) {
    val loaded = CountDownLatch(1)
    val fixtureUrl = "https://appassets.androidplatform.net/fixtures/$name"
    val fixture =
      instrumentation.context.assets.open("fixtures/$name").bufferedReader().use { it.readText() }
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
            loaded.countDown()
          }
        }
      webView.loadDataWithBaseURL(fixtureUrl, fixture, "text/html", "UTF-8", null)
    }
    assertTrue("fixture did not load", loaded.await(10, TimeUnit.SECONDS))
  }
}
