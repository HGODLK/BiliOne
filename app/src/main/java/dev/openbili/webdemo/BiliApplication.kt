package dev.openbili.webdemo

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder

/** Registers animated GIF decoding once for every Compose image request, including avatars. */
class BiliApplication : Application(), SingletonImageLoader.Factory {
  override fun newImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
      .components { add(GifDecoder.Factory()) }
      .build()
}
