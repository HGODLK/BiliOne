package dev.openbili.webdemo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
internal object PlaybackCache {
  private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

  @Volatile private var instance: SimpleCache? = null

  fun get(context: Context): SimpleCache {
    instance?.let {
      return it
    }
    return synchronized(this) {
      instance
        ?: SimpleCache(
            File(context.applicationContext.cacheDir, "media3_playback"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
          )
          .also { instance = it }
    }
  }
}
