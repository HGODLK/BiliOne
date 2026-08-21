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

/**
 * 在 WebView 中执行提取脚本并解析推荐信息的调度器。
 *
 * 负责把 assets 中的 feed-extractor.js 注入 WebView 执行，带超时与重试：
 *  - 单次求值超时或抛错会先归一为可重试的 [FeedExtractionResult.Failure]；
 *  - Empty 与可重试失败会在 [maxAttempts] 内按 [retryDelayMillis] 间隔重试；
 *  - 所有求值都在主线程立即调度（WebView 求值要求主线程），结果交给 [FeedItemJsonParser] 解析。
 */
class FeedPageExtractor(
  context: Context,
  private val maxAttempts: Int = 4,
  private val retryDelayMillis: Long = 500,
  private val evaluationTimeoutMillis: Long = 3_000,
) {
  // 构造时一次性读取提取脚本文本，避免每次提取重复 IO。
  private val script =
    context.applicationContext.assets.open("extraction/feed-extractor.js").bufferedReader().use {
      it.readText()
    }

  init {
    require(maxAttempts > 0) { "maxAttempts must be positive" }
    require(retryDelayMillis >= 0) { "retryDelayMillis cannot be negative" }
    require(evaluationTimeoutMillis > 0) { "evaluationTimeoutMillis must be positive" }
  }

  /** 执行提取：在 [maxAttempts] 内循环求值，遇到可重试结果按间隔延迟重试。 */
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

  /** 单次求值：超时归一为 EVALUATION_TIMEOUT，抛错归一为 EVALUATION_FAILED。 */
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

  /** 把 WebView 回调式的 evaluateJavascript 包装为可挂起、可取消的协程调用。 */
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

  /** 空结果与可重试失败都需要继续重试。 */
  private fun FeedExtractionResult.shouldRetry(): Boolean =
    this is FeedExtractionResult.Empty || (this is FeedExtractionResult.Failure && retryable)

  /** 仅在 Debug 构建下输出每次尝试的结果与统计，用于诊断提取漏斗。 */
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
