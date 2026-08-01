package dev.openbili.webdemo.settings

import android.content.Context
import coil3.imageLoader
import dev.openbili.webdemo.PlaybackCache
import java.io.File

internal object AppCacheManager {
  private val rebuildableDirectories = listOf("bili_dash", "bili_dash_preview")

  fun sizeBytes(context: Context): Long {
    val appContext = context.applicationContext
    val playbackBytes = runCatching { PlaybackCache.sizeBytes(appContext) }.getOrDefault(0L)
    val imageBytes = runCatching { appContext.imageLoader.diskCache?.size ?: 0L }.getOrDefault(0L)
    val manifestBytes = rebuildableDirectories.sumOf { name ->
      directorySize(File(appContext.cacheDir, name))
    }
    return playbackBytes + imageBytes + manifestBytes
  }

  fun clear(context: Context) {
    val appContext = context.applicationContext
    runCatching { PlaybackCache.clear(appContext) }
    runCatching { appContext.imageLoader.memoryCache?.clear() }
    runCatching { appContext.imageLoader.diskCache?.clear() }
    rebuildableDirectories.forEach { name ->
      File(appContext.cacheDir, name).listFiles()?.forEach(File::deleteRecursively)
    }
  }

  internal fun formatSize(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val mib = safeBytes / (1024.0 * 1024.0)
    return when {
      mib >= 1024.0 -> String.format(java.util.Locale.US, "%.1f GB", mib / 1024.0)
      mib >= 10.0 -> String.format(java.util.Locale.US, "%.0f MB", mib)
      else -> String.format(java.util.Locale.US, "%.1f MB", mib)
    }
  }

  private fun directorySize(file: File): Long {
    if (!file.exists()) return 0L
    if (file.isFile) return file.length()
    return file.listFiles()?.sumOf(::directorySize) ?: 0L
  }
}
