package dev.openbili.webdemo.video

import java.util.WeakHashMap

/**
 * 管理弹幕 Surface 的整层遮挡状态。
 *
 * 置顶 SurfaceView 不参与 Compose 的 zIndex 排序，所以页面遮罩不能只依靠 Compose 层级
 * 覆盖它。这里把启动遮罩和播放器内转场分开记录，并在 Surface 自己的渲染线程之外同步
 * 发布最终状态。启动遮罩是应用级状态，所有已挂载和之后新挂载的弹幕层都必须遵守它。
 */
internal class DanmakuOcclusionController {
  data class Snapshot(
    val generation: Long,
    val blocked: Boolean,
  )

  private var startupMaskVisible = false
  private var declarativelySuppressed = false
  private var immediatelySuppressed = false
  private var generation = 0L

  @Volatile private var snapshot = Snapshot(generation = 0L, blocked = false)

  @Synchronized
  fun setStartupMaskVisible(visible: Boolean): Snapshot {
    if (startupMaskVisible == visible) return snapshot
    startupMaskVisible = visible
    return publish()
  }

  @Synchronized
  fun setDeclarativelySuppressed(suppressed: Boolean): Snapshot {
    if (declarativelySuppressed == suppressed) return snapshot
    declarativelySuppressed = suppressed
    return publish()
  }

  /** 点击或同步转场时立即锁住弹幕，避免 Compose 状态尚未赶上时多显示一帧。 */
  @Synchronized
  fun suppressImmediately(): Snapshot {
    if (immediatelySuppressed) return snapshot
    immediatelySuppressed = true
    return publish()
  }

  /** 与 [suppressImmediately] 配对，启动遮罩或其他声明式遮挡仍会继续生效。 */
  @Synchronized
  fun releaseImmediateSuppression(): Snapshot {
    if (!immediatelySuppressed) return snapshot
    immediatelySuppressed = false
    return publish()
  }

  fun currentSnapshot(): Snapshot = snapshot

  private fun publish(): Snapshot {
    val next =
      Snapshot(
        generation = ++generation,
        blocked = startupMaskVisible || declarativelySuppressed || immediatelySuppressed,
      )
    snapshot = next
    return next
  }
}

/** 应用级启动遮罩广播，避免每个播放器宿主分别等待 Compose 重组才能隐藏弹幕。 */
internal object DanmakuOcclusionRegistry {
  private val lock = Any()
  private val overlays = WeakHashMap<DanmakuOverlayView, Unit>()
  private var startupMaskVisible = false

  fun register(overlay: DanmakuOverlayView) {
    val visible =
      synchronized(lock) {
        overlays[overlay] = Unit
        startupMaskVisible
      }
    overlay.setStartupMaskVisible(visible)
  }

  fun unregister(overlay: DanmakuOverlayView) {
    synchronized(lock) { overlays.remove(overlay) }
  }

  fun setStartupMaskVisible(visible: Boolean) {
    val targets =
      synchronized(lock) {
        if (startupMaskVisible == visible) return
        startupMaskVisible = visible
        overlays.keys.toList()
      }
    targets.forEach { it.setStartupMaskVisible(visible) }
  }
}
