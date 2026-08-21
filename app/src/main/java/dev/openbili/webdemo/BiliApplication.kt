package dev.openbili.webdemo

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import dev.openbili.webdemo.feed.LoadedFeedImageRegistry
import dev.openbili.webdemo.feed.PlaybackCoverRegistry
import dev.openbili.webdemo.music.HomeMusicPlayerViewModel
import dev.openbili.webdemo.offline.OfflineMediaManager
import kotlinx.coroutines.Dispatchers

internal enum class PlaybackSessionTarget {
  DETAIL,
  MUSIC,
}

/** 为每个 Compose 图片请求（包括头像）一次性注册动画 GIF 解码。 */
class BiliApplication : Application(), SingletonImageLoader.Factory, ViewModelStoreOwner {
  override val viewModelStore = ViewModelStore()

  @Volatile internal var playbackSessionTarget: PlaybackSessionTarget = PlaybackSessionTarget.DETAIL

  override fun onCreate() {
    super.onCreate()
    DevicePerformancePolicy.configure(this)
    LoadedFeedImageRegistry.configure(DevicePerformancePolicy.isConstrainedImageMode)
    PlaybackCoverRegistry.configure(DevicePerformancePolicy.isConstrainedImageMode)
    // 进程一启动就重新打开持久下载索引，这样即使用户在本进程中还没访问过缓存页，
    // Media3 也能恢复排队的工作。
    OfflineMediaManager.get(this)
  }

  @Suppress("DEPRECATION")
  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
      // 低内存回调发生在真正 OOM 前，先丢弃 Coil 的软缓存，让播放器和 Compose 保留工作集。
      SingletonImageLoader.get(this).memoryCache?.clear()
    }
  }

  /**
   * 详情播放器是进程作用域的，因此 Activity 和 MediaSessionService 始终寻址同一个
   * ExoPlayer。PlayerView/SurfaceView 的所有权完全保留在 Activity UI 内。
   */
  val sharedPlayerViewModel: PlayerViewModel by
    lazy(LazyThreadSafetyMode.NONE) {
      ViewModelProvider(
        this,
        ViewModelProvider.AndroidViewModelFactory.getInstance(this),
      )[PlayerViewModel::class.java]
    }

  /** 音乐在 Activity 重建后仍存活，并且也可被 MediaSessionService 直接寻址。 */
  internal val homeMusicPlayerViewModel: HomeMusicPlayerViewModel by
    lazy(LazyThreadSafetyMode.NONE) {
      ViewModelProvider(
        this,
        ViewModelProvider.AndroidViewModelFactory.getInstance(this),
      )[HomeMusicPlayerViewModel::class.java]
    }

  override fun newImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
      .components { add(GifDecoder.Factory()) }
      .apply {
        if (DevicePerformancePolicy.isConstrainedImageMode) {
          // 256 MiB 应用堆的平板也需要限制图片缓存和解码突发，避免与播放器缓冲叠加耗尽堆。
          memoryCache(
            MemoryCache.Builder()
              .maxSizeBytes(DevicePerformancePolicy.imageMemoryCacheBytes)
              .build()
          )
          decoderCoroutineContext(Dispatchers.IO.limitedParallelism(1))
          fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(2))
        }
      }
      .build()
}
