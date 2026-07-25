package dev.openbili.webdemo.feed

import android.content.Context
import android.util.Log
import android.webkit.WebView
import dev.openbili.webdemo.BuildConfig
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class FeedPageExtractor(
  context: Context,
  private val maxAttempts: Int = 4,
  private val retryDelayMillis: Long = 500,
  private val evaluationTimeoutMillis: Long = 3_000,
) {
  private val script =
    context.applicationContext.assets.open("extraction/feed-extractor.js").bufferedReader().use {
      it.readText()
    }

  init {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    require(retryDelayMillis >= 0) { "retryDelayMillis cannot be negative" }
    require(evaluationTimeoutMillis > 0) { "evaluationTimeoutMillis must be positive" }
  }

  suspend fun extract(webView: WebView): FeedExtractionResult =
    withContext(Dispatchers.Main.immediate) {
      var lastResult: FeedExtractionResult =
        FeedExtractionResult.Failure(
          FeedExtractionErrorCode.EVALUATION_FAILED,
          "推荐内容提取器尚未执行",
          retryable = true,
        )

      repeat(maxAttempts) { index ->
        ensureActive()
        val attempt = index + 1
        lastResult = evaluateAttempt(webView)
        logAttempt(attempt, lastResult)
        if (!lastResult.shouldRetry() || attempt == maxAttempts) return@withContext lastResult
        delay(retryDelayMillis)
      }
      lastResult
    }

  private suspend fun evaluateAttempt(webView: WebView): FeedExtractionResult {
    return try {
      val rawResult =
        withTimeoutOrNull(evaluationTimeoutMillis) { evaluateJavascript(webView, script) }
          ?: return FeedExtractionResult.Failure(
            code = FeedExtractionErrorCode.EVALUATION_TIMEOUT,
            message = "推荐内容提取超时",
            retryable = true,
          )
      FeedItemJsonParser.parse(rawResult)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Throwable) {
      FeedExtractionResult.Failure(
        code = FeedExtractionErrorCode.EVALUATION_FAILED,
        message = "无法执行推荐内容提取器",
        retryable = true,
      )
    }
  }

  private suspend fun evaluateJavascript(webView: WebView, source: String): String =
    suspendCancellableCoroutine { continuation ->
      try {
        webView.evaluateJavascript(source) { value ->
          if (continuation.isActive) continuation.resume(value ?: "null")
        }
      } catch (error: Throwable) {
        if (continuation.isActive) continuation.resumeWithException(error)
      }
    }

  private fun FeedExtractionResult.shouldRetry(): Boolean =
    this is FeedExtractionResult.Empty || (this is FeedExtractionResult.Failure && retryable)

  private fun logAttempt(attempt: Int, result: FeedExtractionResult) {
    if (!BuildConfig.DEBUG) return
    val outcome =
      when (result) {
        is FeedExtractionResult.Success -> "success"
        is FeedExtractionResult.Empty -> "empty"
        is FeedExtractionResult.Failure -> "failure:${result.code}"
      }
    Log.d(
      TAG,
      "attempt=$attempt outcome=$outcome links=${result.stats.videoLinksFound} " +
        "parsed=${result.stats.parsedItems} unique=${result.stats.uniqueItems} " +
        "duplicates=${result.stats.duplicateItems} invalid=${result.stats.filteredInvalidVideoUrl} " +
        "missingTitle=${result.stats.filteredMissingTitle} missingCover=${result.stats.filteredMissingCover}",
    )
  }

  private companion object {
    const val TAG = "FeedPageExtractor"
  }
}
