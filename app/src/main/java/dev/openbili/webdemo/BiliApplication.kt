package dev.openbili.webdemo

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import dev.openbili.webdemo.music.HomeMusicPlayerViewModel
import dev.openbili.webdemo.offline.OfflineMediaManager

internal enum class PlaybackSessionTarget {
  DETAIL,
  MUSIC,
}

/** Registers animated GIF decoding once for every Compose image request, including avatars. */
class BiliApplication : Application(), SingletonImageLoader.Factory, ViewModelStoreOwner {
  override val viewModelStore = ViewModelStore()

  @Volatile internal var playbackSessionTarget: PlaybackSessionTarget = PlaybackSessionTarget.DETAIL

  override fun onCreate() {
    super.onCreate()
    // Re-open the persistent download index as soon as the process starts so Media3 can resume
    // queued work even when the user has not visited the cache page in this process yet.
    OfflineMediaManager.get(this)
  }

  /**
   * The detail player is process-scoped so the Activity and MediaSessionService always address the
   * same ExoPlayer. PlayerView/SurfaceView ownership remains entirely inside the Activity UI.
   */
  val sharedPlayerViewModel: PlayerViewModel by lazy(LazyThreadSafetyMode.NONE) {
    ViewModelProvider(
      this,
      ViewModelProvider.AndroidViewModelFactory.getInstance(this),
    )[PlayerViewModel::class.java]
  }

  /** Music survives Activity recreation and is also directly addressable by MediaSessionService. */
  internal val homeMusicPlayerViewModel: HomeMusicPlayerViewModel by lazy(LazyThreadSafetyMode.NONE) {
    ViewModelProvider(
      this,
      ViewModelProvider.AndroidViewModelFactory.getInstance(this),
    )[HomeMusicPlayerViewModel::class.java]
  }

  override fun newImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
      .components { add(GifDecoder.Factory()) }
      .build()
}
