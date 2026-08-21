package dev.openbili.webdemo.settings

/**
 * 应用缓存体积统计与清理工具。
 *
 * 集中管理三类可重建缓存：ExoPlayer 播放缓存（PlaybackCache）、Coil 图片磁盘/内存缓存，
 * 以及 dash 清单目录（bili_dash / bili_dash_preview）。对外提供字节数统计、整体清理，
 * 以及把字节数格式化为人类可读的 GB/MB 字符串。
 */

import android.content.Context
import coil3.imageLoader
import dev.openbili.webdemo.PlaybackCache
import java.io.File

/**
 * 应用缓存管理器（单例）。
 *
 * 所有统计/清理均通过 applicationContext 操作，避免持有 Activity 引用；对外部组件
 * 可能抛异常的情况统一用 runCatching 兜底，保证设置页的容量显示与清理动作永不崩溃。
 */
internal object AppCacheManager {
  private val rebuildableDirectories = listOf("bili_dash", "bili_dash_preview")

  /** 汇总播放缓存、图片缓存与 dash 清单目录的总字节数。 */
  fun sizeBytes(context: Context): Long {
    val appContext = context.applicationContext
    // 播放缓存（ExoPlayer 已下载的分片与预取）
    val playbackBytes = runCatching { PlaybackCache.sizeBytes(appContext) }.getOrDefault(0L)
    // Coil 图片磁盘缓存
    val imageBytes = runCatching { appContext.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
    // dash 清单目录：删除后可重新下载，计入"可清理"体积
    val manifestBytes = rebuildableDirectories.sumOf { name ->
      directorySize(File(appContext.cacheDir, name))
    }
    return playbackBytes + imageBytes + manifestBytes
  }

  /** 清空全部可重建缓存：播放缓存、图片内存/磁盘缓存与 dash 清单目录。 */
  fun clear(context: Context) {
    val appContext = context.applicationContext
    // 播放缓存可能正被 ExoPlayer 占用，清理失败不影响其余项
    runCatching { PlaybackCache.clear(appContext) }
    // 图片缓存分内存与磁盘两级，逐一清空
    runCatching { appContext.imageLoader.memoryCache?.clear() }
    runCatching { appContext.imageLoader.diskCache?.clear() }
    rebuildableDirectories.forEach { name ->
      File(appContext.cacheDir, name).listFiles()?.forEach(File::deleteRecursively)
    }
  }

  /**
   * 把字节数格式化为 GB/MB 字符串。
   *
   * 不足 10 MB 与超过 1 GB 保留一位小数，10 MB ~ 1 GB 区间取整，避免中等量级上的
   * 小数点徒增噪音。
   */
  internal fun formatSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    // 先换算成 MiB，再按数量级选择单位与精度
    val mib = safeBytes / (1024.0 * 1024.0)
    return when {
      mib >= 1024.0 -> String.format(java.util.Locale.US, "%.1f GB", mib / 1024.0)
      mib >= 10.0 -> String.format(java.util.Locale.US, "%.0f MB", mib)
      else -> String.format(java.util.Locale.US, "%.1f MB", mib)
    }
  }

  /** 递归统计目录（或单个文件）的字节数；目录不存在返回 0。 */
  private fun directorySize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf(::directorySize) ?: 0L
  }
}
