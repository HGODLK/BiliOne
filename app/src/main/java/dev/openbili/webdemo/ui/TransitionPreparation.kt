package dev.openbili.webdemo.ui

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Maximum time a transition may hold the tapped source still while preparing its target. */
internal const val TRANSITION_PREPARE_TIMEOUT_MS = 450L

/** Comment-profile taps may pause briefly to precompose, but must still feel immediate. */
internal const val COMMENT_PROFILE_PREPARE_TIMEOUT_MS = 280L

/** Concrete prerequisites that may be shared by otherwise different transition types. */
internal enum class TransitionReadySignal {
  SOURCE_BOUNDS,
  SOURCE_SNAPSHOT,
  IMAGE_READY,
  TARGET_MOUNTED,
  TARGET_BOUNDS_STABLE,
  PLAYER_VIEW_READY,
  SURFACE_READY,
}

internal enum class TransitionPreparationResult {
  READY,
  TIMED_OUT,
  CANCELLED,
}

/**
 * A token-local readiness barrier. Late callbacks may still call [markReady], but they can never
 * release a newer transition because every transition owns a separate barrier instance.
 */
internal class TransitionPreparationBarrier(requiredSignals: Set<TransitionReadySignal>) {
  private val required = requiredSignals.toSet()
  private val ready = mutableSetOf<TransitionReadySignal>()
  private val completion = CompletableDeferred<Unit>()
  private var cancelled = false

  init {
    if (required.isEmpty()) completion.complete(Unit)
  }

  fun markReady(vararg signals: TransitionReadySignal) {
    synchronized(this) {
      if (cancelled || completion.isCompleted) return
      ready.addAll(signals)
      if (ready.containsAll(required)) completion.complete(Unit)
    }
  }

  fun isReady(): Boolean = synchronized(this) { !cancelled && ready.containsAll(required) }

  fun pendingSignals(): Set<TransitionReadySignal> =
    synchronized(this) { required.filterNotTo(mutableSetOf()) { it in ready } }

  fun cancel() {
    synchronized(this) {
      if (cancelled) return
      cancelled = true
      if (!completion.isCompleted) completion.complete(Unit)
    }
  }

  suspend fun await(
    timeoutMillis: Long = TRANSITION_PREPARE_TIMEOUT_MS
  ): TransitionPreparationResult {
    val completed =
      withTimeoutOrNull(timeoutMillis) {
        completion.await()
        true
      }
    return synchronized(this) {
      when {
        cancelled -> TransitionPreparationResult.CANCELLED
        completed == true && ready.containsAll(required) -> TransitionPreparationResult.READY
        else -> TransitionPreparationResult.TIMED_OUT
      }
    }
  }
}

/** Detects a usable layout rectangle that remains unchanged for consecutive rendered frames. */
internal class StableBoundsTracker(
  private val requiredMatches: Int = 2,
  private val tolerancePx: Float = 1f,
) {
  private var previous = Rect.Zero
  private var matches = 0

  fun observe(bounds: Rect): Boolean {
    if (!bounds.hasUsableSize()) {
      previous = bounds
      matches = 0
      return false
    }
    if (bounds.approximatelyEquals(previous, tolerancePx)) {
      matches += 1
    } else {
      matches = 0
    }
    previous = bounds
    return matches >= requiredMatches
  }
}
