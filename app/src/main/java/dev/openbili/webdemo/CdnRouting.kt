package dev.openbili.webdemo

import android.content.Context
import dev.openbili.webdemo.settings.CdnRegionPreference

/** 从设置读取当前 CDN 地区偏好，供各个播放 ViewModel 共享。 */
internal fun readCdnRegionPreference(context: Context): CdnRegionPreference {
  val prefs = context.applicationContext.getSharedPreferences("app_settings", 0)
  return runCatching {
      CdnRegionPreference.valueOf(
        prefs.getString("cdn_region_preference", null).orEmpty()
      )
    }
    .getOrDefault(CdnRegionPreference.MAINLAND_CHINA)
}

internal fun configuredCdnHost(context: Context): String =
  readCdnRegionPreference(context).preferredHost
